package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentProfileEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
public class EditProfileFragment : Fragment() {

    // ─────────── Constants ───────────
    private companion object {
        private const val IMAGE_MIME_SELECTOR = "image/*"
        private const val CURSOR_NOT_SET = -1
    }

    // ─────────── View / VM ───────────
    private var _binding: FragmentProfileEditBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditProfileViewModel by viewModels()

    // ─────────── Activity Result Launchers ───────────
    // Lets user pick an image; we optimistically paint it, then store the Uri in VM.
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                binding.ivProfilePicture.setImageBitmap(bitmap)
            }
            viewModel.setProfilePictureUri(uri)
        } catch (_: Exception) {
            showError(getString(R.string.profile_edit_image_load_failed))
        }
    }

    // ─────────── Lifecycle ───────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Wires toolbar, click handlers, text listeners, and collects state.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupClickListeners()
        setupTextChangeListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────── UI Wiring ───────────
    // Primary click actions: pick photo, save profile, toggle password section.
    private fun setupClickListeners() = with(binding) {
        btnEditPhoto.setOnClickListener { pickImageLauncher.launch(IMAGE_MIME_SELECTOR) }

        btnSaveChanges.setOnClickListener {
            viewModel.updateProfile(requireContext())
            findNavController().navigateUp()
        }

        btnChangePassword.setOnClickListener {
            val nowVisible = !layoutPasswordFields.isVisible
            layoutPasswordFields.visibility = if (nowVisible) View.VISIBLE else View.GONE
            btnChangePassword.text = if (nowVisible) getString(android.R.string.cancel) else getString(R.string.change)
            if (!nowVisible) {
                etCurrentPassword.text?.clear()
                etNewPassword.text?.clear()
                etConfirmNewPassword.text?.clear()
            }
        }
    }

    // Simple two-way text sync; VM holds the authoritative state.
    private fun setupTextChangeListeners() = with(binding) {
        tvFullName.doAfterTextChanged { viewModel.setFullName(it?.toString().orEmpty()) }
        etCurrentPassword.doAfterTextChanged { viewModel.setCurrentPassword(it?.toString().orEmpty()) }
        etNewPassword.doAfterTextChanged { viewModel.setNewPassword(it?.toString().orEmpty()) }
        etConfirmNewPassword.doAfterTextChanged { viewModel.setConfirmPassword(it?.toString().orEmpty()) }
    }

    // ─────────── Collectors ───────────
    // Collects VM state for rendering; keeps UI reactive and simple.
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is EditProfileUiState.Loading -> showLoading(true)

                        is EditProfileUiState.Success -> {
                            showLoading(false)

                            val currentEmail = binding.tvEmail.text?.toString()
                            if (currentEmail != state.user.email) {
                                binding.tvEmail.setText(state.user.email.orEmpty())
                            }

                            updateFullName(state.user.fullName)
                            updateProfilePicture(state.profilePictureUri, state.user.photoBase64)

                            state.successMessage?.let(::showSuccess)
                            binding.btnSaveChanges.isEnabled = state.isFormValid
                        }

                        is EditProfileUiState.Error -> {
                            showLoading(false)
                            if (state.message.isNotBlank()) showError(state.message)
                        }
                    }
                }
            }
        }
    }

    // ─────────── Render Helpers ───────────
    // Preserves cursor when possible to avoid jarring jumps while typing.
    private fun updateFullName(fullName: String?) {
        if (fullName == null) return
        val current = binding.tvFullName.text?.toString()
        if (current == fullName) return

        val selection = binding.tvFullName.selectionEnd
        binding.tvFullName.setText(fullName)

        val safeEnd = if (selection == CURSOR_NOT_SET) fullName.length else selection
        val clamped = safeEnd.coerceIn(0, fullName.length)
        binding.tvFullName.setSelection(clamped)
    }

    // Prefers the freshly chosen Uri; falls back to base64 from server.
    private fun updateProfilePicture(uri: Uri?, photoBase64: String?) {
        if (uri != null) {
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    binding.ivProfilePicture.setImageBitmap(bitmap)
                }
                return
            } catch (_: Exception) {
                // fall through to base64
            }
        }
        loadServerProfileImage(photoBase64)
    }

    // Decodes base64 safely; shows a default avatar on failure or missing data.
    private fun loadServerProfileImage(photoBase64: String?) {
        if (photoBase64.isNullOrBlank()) {
            binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
            return
        }
        try {
            val imageBytes = Base64.decode(photoBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            binding.ivProfilePicture.setImageBitmap(bitmap)
        } catch (_: Exception) {
            binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
        }
    }

    // ─────────── UI State Toggles ───────────
    private fun showLoading(show: Boolean) {
        binding.progressBar.isVisible = show
        binding.editProfileContent.isVisible = !show
        binding.btnSaveChanges.isEnabled = !show
    }

    private fun showError(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun showSuccess(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.success_green))
                .show()
        }
    }
}

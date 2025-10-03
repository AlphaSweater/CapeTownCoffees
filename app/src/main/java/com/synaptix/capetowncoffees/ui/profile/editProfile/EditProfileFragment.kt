package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.synaptix.capetowncoffees.databinding.FragmentEditProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.ivProfilePicture.setImageBitmap(bitmap)
                inputStream?.close()
                viewModel.setProfilePictureUri(it)
            } catch (e: Exception) {
                showError("Failed to load image")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupTextChangeListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.apply {
            btnBack.setOnClickListener { findNavController().navigateUp() }
            btnEditPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
            btnSaveChanges.setOnClickListener { viewModel.updateProfile() }
        }
    }

    private fun setupTextChangeListeners() {
        binding.apply {
            // Update the ID to match the one in the layout (tvFullName)
            tvFullName.doAfterTextChanged { editable ->
                viewModel.setFullName(editable?.toString() ?: "")
            }
            etCurrentPassword.doAfterTextChanged { editable ->
                viewModel.setCurrentPassword(editable?.toString() ?: "")
            }
            etNewPassword.doAfterTextChanged { editable ->
                viewModel.setNewPassword(editable?.toString() ?: "")
            }
            etConfirmNewPassword.doAfterTextChanged { editable ->
                viewModel.setConfirmPassword(editable?.toString() ?: "")
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is EditProfileUiState.Loading -> showLoading(true)
                        is EditProfileUiState.Success -> {
                            showLoading(false)
                            state.user.let { user ->
                                // Only set the text if it's different to prevent cursor jumping
                                if (binding.tvFullName.text?.toString() != user.fullName) {
                                    binding.tvFullName.setText(user.fullName)
                                }
                                binding.etEmail.setText(user.email)
                                // Load profile picture if available
                                user.photoBase64?.let { base64 ->
                                    // Load image from base64
                                }
                            }
                            state.successMessage?.let { message ->
                                showSuccess(message)
                            }
                            binding.btnSaveChanges.isEnabled = state.isFormValid
                        }
                        is EditProfileUiState.Error -> {
                            showLoading(false)
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.btnSaveChanges.isEnabled = !isLoading
    }

    private fun showSuccess(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
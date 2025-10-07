//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT assisted in designing and structuring this Fragment, including lifecycle
//handling, navigation setup, and interaction with the ViewModel.
//* It also provided guidance on ConstraintLayout usage and UI event handling.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.profile.editProfile

// Remove this import as it's not needed
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
class EditProfileFragment : Fragment() {

    private var _binding: FragmentProfileEditBinding? = null

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    // First set the image in the UI
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    binding.ivProfilePicture.setImageBitmap(bitmap)
                    inputStream?.close()

                    // Then update the ViewModel with the URI
                    viewModel.setProfilePictureUri(uri)

                    // Note: We'll let the user save the changes explicitly with the save button
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
        _binding = FragmentProfileEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hook up toolbar back navigation (layout uses MaterialToolbar with navigationIcon)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupClickListeners()
        setupTextChangeListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.apply {
            btnEditPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
            btnSaveChanges.setOnClickListener {
                // Pass the context to the ViewModel
                viewModel.updateProfile(requireContext())

                findNavController().navigateUp()
            }

            // Add click listener for the change password button
            btnChangePassword.setOnClickListener {
                val isVisible = layoutPasswordFields.isVisible
                layoutPasswordFields.visibility = if (isVisible) View.GONE else View.VISIBLE
                btnChangePassword.text = if (isVisible) {
                    getString(R.string.change)
                } else {
                    getString(android.R.string.cancel)
                }
                // Clear password fields when hiding
                if (isVisible) {
                    etCurrentPassword.text?.clear()
                    etNewPassword.text?.clear()
                    etConfirmNewPassword.text?.clear()
                }
            }
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
                            
                            // Update email if needed
                            val currentEmail = binding.tvEmail.text?.toString()
                            if (currentEmail != state.user.email) {
                                binding.tvEmail.setText(state.user.email ?: "")
                            }
                            
                            // Update full name if needed
                            updateFullName(state.user.fullName)
                            
                            // Update profile picture
                            updateProfilePicture(state.profilePictureUri, state.user.photoBase64)
                            
                            // Show success message if available
                            state.successMessage?.let { showSuccess(it) }
                            
                            // Update save button state
                            binding.btnSaveChanges.isEnabled = state.isFormValid
                        }
                        is EditProfileUiState.Error -> {
                            showLoading(false)
                            if (state.message.isNotBlank()) {
                                showError(state.message)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun updateFullName(fullName: String?) {
        if (fullName != null && binding.tvFullName.text?.toString() != fullName) {
            val selection = binding.tvFullName.selectionEnd
            binding.tvFullName.setText(fullName)
            
            // Restore cursor position if possible
            if (selection in 0..(fullName.length)) {
                binding.tvFullName.setSelection(selection)
            } else {
                binding.tvFullName.setSelection(fullName.length)
            }
        }
    }
    
    private fun updateProfilePicture(uri: Uri?, photoBase64: String?) {
        // Try to load from URI first (newly selected image)
        uri?.let { 
            try {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.ivProfilePicture.setImageBitmap(bitmap)
                inputStream?.close()
                return
            } catch (e: Exception) {
                // Fall through to base64 if URI loading fails
            }
        }
        
        // Fall back to base64 from server
        loadServerProfileImage(photoBase64)
    }
    
    private fun loadServerProfileImage(photoBase64: String?) {
        photoBase64?.let { base64 ->
            try {
                val imageBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                binding.ivProfilePicture.setImageBitmap(bitmap)
            } catch (e: Exception) {
                binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
            }
        } ?: run {
            binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
        }
    }


    private fun showLoading(show: Boolean) {
        binding.progressBar.isVisible = show
        binding.editProfileContent.isVisible = !show
        binding.btnSaveChanges.isEnabled = !show
    }

    private fun showError(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showSuccess(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.success_green))
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

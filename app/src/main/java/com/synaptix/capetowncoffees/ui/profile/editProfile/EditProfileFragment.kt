package com.synaptix.capetowncoffees.ui.profile.editProfile

// Remove this import as it's not needed
import android.graphics.BitmapFactory
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
import com.google.android.material.snackbar.Snackbar
import com.synaptix.capetowncoffees.R
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
        if (uri != null) {
            try {
                // First set the image in the UI
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.ivProfilePicture.setImageBitmap(bitmap)
                inputStream?.close()
                
                // Then update the ViewModel with the URI
                viewModel.setProfilePictureUri(uri)
                
                // Trigger an immediate save of the profile with the new image
                viewModel.updateProfile(requireContext())
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
            btnSaveChanges.setOnClickListener { 
                // Pass the context to the ViewModel
                viewModel.updateProfile(requireContext())
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
                viewModel.uiState.collect { state: EditProfileUiState ->
                    when (state) {
                        is EditProfileUiState.Loading -> showLoading(true)
                        is EditProfileUiState.Success -> {
                            showLoading(false)
                            state.user.let { user ->
                                // Set the email
                                binding.tvEmail.setText(user.email ?: "")
                                // Only set the text if it's different to prevent cursor jumping
                                if (binding.tvFullName.text?.toString() != user.fullName) {
                                    binding.tvFullName.setText(user.fullName)
                                }
                                
                                // Load profile picture if available
                                user.photoBase64?.let { base64 ->
                                    try {
                                        val imageBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        binding.ivProfilePicture.setImageBitmap(bitmap)
                                    } catch (e: Exception) {
                                        // If there's an error loading the image, show the default avatar
                                        binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                                    }
                                } ?: run {
                                    // If no profile picture is available, show the default avatar
                                    binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                                }
                                
                                // Set the user's name in the profile header if available
                                user.fullName?.let { name ->
                                    binding.tvFullName.setText(name)
                                }
                            }
                            
                            // Show success message if available
                            state.successMessage?.let { successMessage: String ->
                                showSuccess(successMessage)
                            }
                            
                            // Update save button state based on form validity
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
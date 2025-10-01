package com.synaptix.capetowncoffees.ui.profile

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.synaptix.capetowncoffees.databinding.FragmentEditProfileBinding
import com.synaptix.capetowncoffees.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Base64
import com.synaptix.capetowncoffees.R

@AndroidEntryPoint
class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels()

    private var hasAttemptedSave: Boolean = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                // Show the selected image immediately
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                binding.ivProfilePicture.setImageBitmap(bitmap)
                inputStream?.close()

                // Pass the URI and context to the ViewModel
                viewModel.updateProfilePicture(it, requireContext())
            } catch (e: Exception) {
                Log.e("EditProfileFragment", "Error loading image", e)
                Snackbar.make(binding.root, "Failed to load image", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up click listeners
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSaveChanges.setOnClickListener {
            hasAttemptedSave = true
            saveProfileChanges()
        }

        // Load user profile when view is created
        viewModel.loadUserProfile()

        // Observe ViewModel state
        observeViewModel()
    }

    private fun observeViewModel() {
        // In onViewCreated, update the UI state observer
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            resource.data?.let { uiState ->
                                // Update name and email
                                binding.etFullName.setText("${uiState.firstName} ${uiState.lastName}".trim())
                                binding.etEmail.setText(uiState.email)

                                // Load profile image if available
                                uiState.photoBase64?.let { base64 ->
                                    try {
                                        val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        binding.ivProfilePicture.setImageBitmap(bitmap)
                                    } catch (e: Exception) {
                                        Log.e("EditProfileFragment", "Error loading profile image", e)
                                        binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                                    }
                                } ?: run {
                                    binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                                }
                            }
                        }
                        is Resource.Error -> {
                            // Handle error
                        }
                        is Resource.Loading -> {
                            // Show loading state if needed
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe update state
                viewModel.updateState.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            // Only disable button if user initiated a save
                            binding.btnSaveChanges.isEnabled = !hasAttemptedSave
                        }
                        is Resource.Success -> {
                            // Only react to success after a save was attempted
                            if (hasAttemptedSave) {
                                binding.btnSaveChanges.isEnabled = true
                                showSuccess("Profile updated successfully")
                                findNavController().navigateUp()
                                hasAttemptedSave = false
                            } else {
                                binding.btnSaveChanges.isEnabled = true
                            }
                        }
                        is Resource.Error -> {
                            binding.btnSaveChanges.isEnabled = true
                            if (hasAttemptedSave) {
                                showError(resource.message ?: "Failed to update profile")
                                hasAttemptedSave = false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveProfileChanges() {
        val fullName = binding.etFullName.text?.toString()?.trim() ?: ""
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        val currentPassword = binding.etCurrentPassword.text?.toString() ?: ""
        val newPassword = binding.etNewPassword.text?.toString() ?: ""
        val confirmPassword = binding.etConfirmNewPassword.text?.toString() ?: ""

        // Basic validation
        if (fullName.isEmpty()) {
            showError("Please enter your full name")
            return
        }

        if (email.isEmpty()) {
            showError("Please enter your email")
            return
        }

        // If changing password, validate the password fields
        if (newPassword.isNotEmpty() || currentPassword.isNotEmpty() || confirmPassword.isNotEmpty()) {
            if (newPassword.length < 6) {
                showError("New password must be at least 6 characters")
                return
            }

            if (newPassword != confirmPassword) {
                showError("New passwords do not match")
                return
            }
        }

        // Split full name into first and last name
        val nameParts = fullName.split(" ").filter { it.isNotBlank() }
        val firstName = nameParts.firstOrNull() ?: ""
        val lastName = nameParts.drop(1).joinToString(" ")

        // Call ViewModel to update profile
        viewModel.updateProfile(
            firstName = firstName,
            lastName = lastName,
            email = email,
            currentPassword = if (currentPassword.isNotEmpty()) currentPassword else null,
            newPassword = if (newPassword.isNotEmpty()) newPassword else null
        )
    }
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.editProfileContent.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }

    private fun showMessage(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showSuccess(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(requireContext().getColor(android.R.color.holo_green_dark))
                .show()
        }
    }

    private fun showError(message: String) {
        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(requireContext().getColor(android.R.color.holo_red_dark))
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object { private const val TAG = "EditProfileFragment" }
}

package com.synaptix.capetowncoffees.ui.profile.editProfile

import android.graphics.BitmapFactory
import android.os.Bundle
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Base64
import com.synaptix.capetowncoffees.R
import timber.log.Timber

@AndroidEntryPoint
class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels()

    private var hasAttemptedSave: Boolean = false

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
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnChangePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnSaveChanges.setOnClickListener {
            hasAttemptedSave = true
            viewModel.updateProfile(
                currentPassword = binding.etCurrentPassword.text?.toString(),
                newPassword = binding.etNewPassword.text?.toString(),
                context = requireContext()
            )
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.editProfileContent.visibility = View.VISIBLE
                        // Profile image
                        viewModel.photoBase64.value?.let { base64 ->
                            try {
                                val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                binding.ivProfilePicture.setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                            }
                        } ?: run {
                            binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                        }
                    }
                    is Resource.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.editProfileContent.visibility = View.VISIBLE
                        showError(resource.message)
                    }
                    is Resource.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.editProfileContent.visibility = View.INVISIBLE
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.updateState.collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.btnSaveChanges.isEnabled = !hasAttemptedSave
                    }
                    is Resource.Success -> {
                        if (hasAttemptedSave) {
                            binding.btnSaveChanges.isEnabled = true
                            showSuccess("Profile updated successfully")
                            findNavController().navigateUp()
                            hasAttemptedSave = false
                            viewModel.resetUpdateState()
                        } else {
                            binding.btnSaveChanges.isEnabled = true
                        }
                    }
                    is Resource.Error -> {
                        binding.btnSaveChanges.isEnabled = true
                        if (hasAttemptedSave) {
                            showError(resource.message)
                            hasAttemptedSave = false
                            viewModel.resetUpdateState()
                        }
                    }
                }
            }
        }
    }

    private fun showSuccess(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(requireContext().getColor(android.R.color.holo_green_dark)).show() }
    }
    private fun showError(message: String?) {
        view?.let { Snackbar.make(it, message ?: "Error", Snackbar.LENGTH_LONG)
            .setBackgroundTint(requireContext().getColor(android.R.color.holo_red_dark)).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object { private const val TAG = "EditProfileFragment" }
}

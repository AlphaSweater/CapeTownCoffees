package com.synaptix.capetowncoffees.ui.profile

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.util.Base64
import timber.log.Timber


@AndroidEntryPoint

class ProfileFragment : Fragment() {

    private var _binding: com.synaptix.capetowncoffees.databinding.FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = com.synaptix.capetowncoffees.databinding.FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        

        // Observe user data
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userState.collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            resource.data.let { user ->
                                binding.tvUserName.text = user.fullName.trim()
                                // Load Base64 image if available
                                // check if photoBase64 is not null or empty
                                if (user.photoBase64.isNullOrEmpty()) {
                                    Timber.d("PhotoBase64 is null or empty")
                                }
                                user.photoBase64?.let { base64 ->
                                    try {
                                        Timber.d("Loading profile image from Base64")
                                        val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        binding.ivProfilePicture.setImageBitmap(bitmap)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error loading profile image")
                                        binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                                    }
                                } ?: run {
                                    binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                                }
                            }
                        }
                        is Resource.Error -> {
                            Timber.e("Error loading user: ${resource.message}")
                        }
                        else -> { /* Loading state can be handled here if needed */ }
                    }
                }
            }
        }

        // Load user data
        viewModel.loadUserProfile()

        // Settings button navigation - simplified and fixed
        binding.btnSettings.apply {
            // Make sure the button is clickable
            isClickable = true
            isFocusable = true
            
            // Handle click
            setOnClickListener {
                try {
                    // Try direct navigation first (simpler approach)
                    findNavController().navigate(R.id.settingsFragment)
                    Timber.d("Navigating to settings fragment")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to navigate to settings")
                }
            }
            
            // Add touch feedback
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                    }
                }
                true
            }
        }

        // Edit profile button navigation
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }
}

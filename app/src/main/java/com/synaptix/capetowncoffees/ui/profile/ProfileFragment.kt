package com.synaptix.capetowncoffees.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentProfileNewBinding
import com.synaptix.capetowncoffees.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile_new) {

    private var _binding: FragmentProfileNewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileNewBinding.bind(view)

        setupToolbar()
        observeUser()
        viewModel.loadUserProfile()

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = binding.toolbar

        // Inflate menu if you didn’t set app:menu in XML
        if (toolbar.menu.size() == 0) {
            toolbar.inflateMenu(R.menu.menu_profile) // contains action_settings
        }

        // Handle right-side settings icon click
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    try {
                        findNavController().navigate(R.id.settingsFragment)
                        Timber.d("Navigating to settings fragment")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to navigate to settings")
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun observeUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userState.collect { res ->
                    when (res) {
                        is Resource.Success -> {
                            val user = res.data
                            binding.tvUserName.text = user.fullName?.trim().orEmpty()

                            val b64 = user.photoBase64
                            if (b64.isNullOrBlank()) {
                                Timber.d("No Base64 profile photo; using placeholder.")
                                binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                            } else {
                                // Decode off the main thread
                                lifecycleScope.launch {
                                    val bmp = decodeBase64Bitmap(b64)
                                    if (bmp != null) {
                                        binding.ivProfilePicture.setImageBitmap(bmp)
                                    } else {
                                        binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                                    }
                                }
                            }
                        }
                        is Resource.Error -> {
                            Timber.e("Error loading user: ${res.message}")
                            binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                            binding.tvUserName.text = getString(R.string.app_name) // or keep last value
                        }
                        is Resource.Loading -> {
                            // Optional: show a shimmer/skeleton here
                        }
                    }
                }
            }
        }
    }

    private suspend fun decodeBase64Bitmap(base64: String): Bitmap? = withContext(Dispatchers.Default) {
        return@withContext try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode Base64 image")
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
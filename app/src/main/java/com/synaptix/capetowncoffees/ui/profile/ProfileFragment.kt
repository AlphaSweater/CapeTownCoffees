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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile_new) {

    // ─────────── View & VM ───────────
    private var _binding: FragmentProfileNewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    // ─────────── Lifecycle ───────────
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileNewBinding.bind(view)

        setupToolbar()
        observeUser()
        observeGamification()
        viewModel.loadUserProfile()

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────── UI Wiring ───────────
    // Sets up toolbar menu and handles navigation to settings.
    private fun setupToolbar() {
        val toolbar: MaterialToolbar = binding.toolbar
        if (toolbar.menu.size() == 0) toolbar.inflateMenu(R.menu.menu_profile)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    runCatching { findNavController().navigate(R.id.settingsFragment) }
                        .onFailure { Timber.e(it, "Failed to navigate to settings") }
                    true
                }
                else -> false
            }
        }
    }

    // ─────────── Collectors ───────────
    // Observes user state and renders name/photo; decodes images off main thread.
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
                                binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                            } else {
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
                            Timber.e("Error loading user: ${'$'}{res.message}")
                            binding.ivProfilePicture.setImageResource(R.drawable.ic_ctc_person)
                            binding.tvUserName.text = getString(R.string.app_name)
                        }
                        is Resource.Loading -> {
                            // no-op; add shimmer here if needed
                        }
                    }
                }
            }
        }
    }

    private fun observeGamification() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gamificationState.collect { state ->
                    binding.progressBarExplorer.max = 100
                    binding.progressBarExplorer.progress = state.progressPercent

                    binding.tvLevel.text = getString(R.string.level_dynamic, state.level)

                    // Level-based label used for both the header tagline and the
                    // gamification card title so they always stay in sync.
                    val levelLabel = when (state.level) {
                        1 -> getString(R.string.latte_lover)
                        2 -> getString(R.string.coffee_explorer)
                        3 -> getString(R.string.cafe_connoisseur)
                        4 -> getString(R.string.roast_master)
                        else -> getString(R.string.coffee_legend)
                    }
                    binding.tvUserTagline.text = levelLabel
                    binding.tvGamificationTitle.text = levelLabel

                    // Set the current badge image based on level (mipmap icons)
                    // in the gamification card header.
                    val badgeRes = when (state.level) {
                        1 -> R.mipmap.im_ctc_level_one_badge_foreground
                        2 -> R.mipmap.im_ctc_level_two_badge_foreground
                        3 -> R.mipmap.im_ctc_level_three_badge_foreground
                        4 -> R.mipmap.im_ctc_level_one_badge_foreground
                        else -> R.mipmap.im_ctc_level_one_badge_foreground
                    }
                    binding.ivGamificationBadge.setImageResource(badgeRes)

                    val next = state.nextTarget
                    val summary = if (next != null) {
                        val remaining = (next - state.reviewCount).coerceAtLeast(0)
                        getString(R.string.gamification_summary, state.reviewCount, next, remaining)
                    } else {
                        getString(R.string.gamification_summary_max_level, state.reviewCount)
                    }
                    binding.tvLevelSummary.text = summary

                    binding.tvPoints.text = state.reviewCount.toString()
                    binding.tvProgressPercentage.text = "${state.progressPercent}%"
                    binding.tvBadges.text = state.badgeCount.toString()
                }
            }
        }
    }

    // ─────────── Helpers ───────────
    // Safe Base64 decode; returns null on failures to keep UI resilient.
    private suspend fun decodeBase64Bitmap(base64: String): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.onFailure { Timber.e(it, "Failed to decode Base64 image") }
            .getOrNull()
    }
}

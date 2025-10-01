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

        // Setup RecyclerView
        val rv = binding.rvReviews
        rv.layoutManager = LinearLayoutManager(requireContext())

        val demo = listOf(
            ReviewItem(
                "Truth Coffee",
                "2 days ago",
                4.5f,
                "This place is the best!! So many different options",
                1
            ),
            ReviewItem(
                "Origin Coffee",
                "1 week ago",
                4.0f,
                "Great ambiance and solid espresso.",
                3
            ),
            ReviewItem(
                "Deluxe Coffeeworks",
                "3 weeks ago",
                5.0f,
                "My favorite flat white in town!",
                5
            )
        )

        rv.adapter = ReviewAdapter(demo)

        // Observe user data
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userState.collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            resource.data?.let { user ->
                                binding.tvUserName.text = "${user.firstName} ${user.lastName}".trim()
                            }
                        }
                        is Resource.Error -> {
                            Log.e("ProfileFragment", "Error loading user: ${resource.message}")
                        }
                        else -> { /* Loading state can be handled here if needed */ }
                    }
                }
            }
        }

        // Load user data
        viewModel.loadUserProfile()

        // Settings button navigation
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        // Edit profile button navigation
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }
}

package com.synaptix.capetowncoffees.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentSignInBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignInFragment : Fragment() {
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up click listener for the Sign Up text
        binding.signUpText.setOnClickListener {
            findNavController().navigate(R.id.action_signInFragment_to_signUpFragment)
        }

        // Set up click listener for the Sign In button
        binding.signInButton.setOnClickListener {
            // For now, just navigate to home screen
            // In a real app, you would validate credentials first
            findNavController().navigate(R.id.action_signInFragment_to_homeFragment)
        }

        // Set up click listener for Google sign in button
        binding.googleSignInButton.setOnClickListener {
            // For now, just navigate to home screen
            // In a real app, you would implement Google Sign-In
            findNavController().navigate(R.id.action_signInFragment_to_homeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

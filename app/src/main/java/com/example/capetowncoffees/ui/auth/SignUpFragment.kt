package com.example.capetowncoffees.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.capetowncoffees.R
import com.example.capetowncoffees.databinding.FragmentSignUpBinding

class SignUpFragment : Fragment() {
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up click listener for the Sign In text
        binding.textSignIn.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // Set up click listener for the Sign Up button
        binding.buttonSignUp.setOnClickListener {
            // For now, just navigate to home screen
            // In a real app, you would validate input and create an account
            findNavController().navigate(R.id.action_signUpFragment_to_homeFragment)
        }
        
        // Set up click listener for Google sign up button
        binding.googleSignUpButton.setOnClickListener {
            // For now, just navigate to home screen
            // In a real app, you would implement Google Sign-In
            findNavController().navigate(R.id.action_signUpFragment_to_homeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

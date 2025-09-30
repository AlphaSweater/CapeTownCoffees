package com.synaptix.capetowncoffees.ui.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentAuthLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private var _binding: FragmentAuthLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up click listener for the Sign Up text
        binding.textRegisterSwap.setOnClickListener {
            findNavController().navigate(R.id.action_authLoginFragment_to_authRegisterFragment)
        }

        // Set up click listener for the Sign In button
        binding.buttonlogin.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            viewModel.loginUser(email, password)
        }

        // Set up click listener for Google sign in button
        binding.buttonGoogleLogin.setOnClickListener {
            // For now, just navigate to home screen
            // In a real app, you would implement Google Sign-In
            findNavController().navigate(R.id.action_authLoginFragment_to_homeFragment)
        }

        // TODO: Add error text view to show errors
        // Observe ViewModel state
        viewModel.loginState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is LoginUiState.Loading -> {
                    binding.buttonlogin.isEnabled = false
                }
                is LoginUiState.Success -> {
                    binding.buttonlogin.isEnabled = true
                    findNavController().navigate(R.id.action_authLoginFragment_to_homeFragment)
                    viewModel.resetState()
                }
                is LoginUiState.Error -> {
                    binding.buttonlogin.isEnabled = true
                    // binding.errorTextView.text = state.message
                    // binding.errorTextView.visibility = View.VISIBLE
                }
                is LoginUiState.ValidationError -> {
                    binding.buttonlogin.isEnabled = true
                    binding.emailInputLayout.error = state.emailError
                    binding.passwordInputLayout.error = state.passwordError
                }
                is LoginUiState.Idle -> {
                    binding.buttonlogin.isEnabled = true
                    // binding.errorTextView.visibility = View.GONE
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
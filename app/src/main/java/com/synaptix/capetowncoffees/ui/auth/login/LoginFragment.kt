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
import timber.log.Timber

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
        Timber.d("LoginFragment onCreateView called")
        _binding = FragmentAuthLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("LoginFragment onViewCreated called")

        // Set up click listener for the Sign Up text
        binding.textRegisterSwap.setOnClickListener {
            Timber.d("Register swap clicked, navigating to RegisterFragment")
            findNavController().navigate(R.id.action_authLoginFragment_to_authRegisterFragment)
        }

        // Set up click listener for the Sign In button
        binding.buttonlogin.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            Timber.d("Login button clicked with email=%s", email)
            viewModel.loginUser(email, password)
        }

        // Set up click listener for Google sign in button
        binding.buttonGoogleLogin.setOnClickListener {
            Timber.d("Google login button clicked, navigating to HomeFragment")
            findNavController().navigate(R.id.action_authLoginFragment_to_homeFragment)
        }

        // TODO: Add error text view to show errors
        // Observe ViewModel state
        viewModel.loginState.observe(viewLifecycleOwner, Observer { state ->
            Timber.d("Observed loginState: %s", state)
            when (state) {
                is LoginUiState.Loading -> {
                    Timber.d("UI state: Loading")
                    binding.buttonlogin.isEnabled = false
                }
                // Update the navigation in the success state
                is LoginUiState.Success -> {
                    Timber.d("UI state: Success, preparing to navigate to HomeFragment")
                    binding.buttonlogin.isEnabled = true

                    try {
                        Timber.d("Current back stack before navigation: ${findNavController().currentBackStackEntry?.destination?.label}")
                        Timber.d("Attempting navigation with action: action_authLoginFragment_to_homeFragment")

                        // Try with the action ID
                        findNavController().navigate(R.id.action_authLoginFragment_to_homeFragment)

                        // If we get here, navigation was attempted but might have failed silently
                        Timber.d("Navigation function was called, but we don't know if it succeeded")
                    } catch (e: Exception) {
                        Timber.e(e, "Navigation failed with exception")
                    }

                    viewModel.resetState()
                }
                is LoginUiState.Error -> {
                    Timber.d("UI state: Error, message=%s", state.message)
                    binding.buttonlogin.isEnabled = true
                    // binding.errorTextView.text = state.message
                    // binding.errorTextView.visibility = View.VISIBLE
                }
                is LoginUiState.ValidationError -> {
                    Timber.d("UI state: ValidationError, emailError=%s, passwordError=%s", state.emailError, state.passwordError)
                    binding.buttonlogin.isEnabled = true
                    binding.emailInputLayout.error = state.emailError
                    binding.passwordInputLayout.error = state.passwordError
                }
                is LoginUiState.Idle -> {
                    Timber.d("UI state: Idle")
                    binding.buttonlogin.isEnabled = true
                    // binding.errorTextView.visibility = View.GONE
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                }
            }
        })
    }

    override fun onDestroyView() {
        Timber.d("LoginFragment onDestroyView called")
        super.onDestroyView()
        _binding = null
    }
}
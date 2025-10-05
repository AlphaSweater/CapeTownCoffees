package com.synaptix.capetowncoffees.ui.auth.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentAuthRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : Fragment() {
    private var _binding: FragmentAuthRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RegisterViewModel by viewModels()

    private lateinit var signInClient: GoogleSignInClient

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: throw IllegalStateException("No ID token")
            viewModel.registerWithGoogleToken(idToken)
        } catch (e: Exception) {
            // Optional: surface a message
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        signInClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.buttonSignUp.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val pass = binding.passwordEditText.text.toString()
            val confirm = binding.confirmPasswordEditText.text.toString()
            viewModel.registerUser(name, email, pass, confirm)
        }

        binding.buttonGoogleRegister.setOnClickListener {
            signInClient.signOut().addOnCompleteListener {
                googleLauncher.launch(signInClient.signInIntent)
            }
        }

        // Set up click listener for the Sign In text
        binding.textLoginSwap.setOnClickListener {
            findNavController().navigateUp()
        }

        // Set up click listener for the Sign Up button
        binding.buttonSignUp.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()
            viewModel.registerUser(name, email, password, confirmPassword)
        }

        // TODO: Add error text view to show errors
        // Observe ViewModel state
        viewModel.registerState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is RegisterUiState.Loading -> {
                    binding.buttonSignUp.isEnabled = false
                }
                is RegisterUiState.Success -> {
                    binding.buttonSignUp.isEnabled = true
                    findNavController().navigate(R.id.action_authRegisterFragment_to_homeFragment)
                    viewModel.resetState()
                }
                is RegisterUiState.Error -> {
                    binding.buttonSignUp.isEnabled = true
                    // Show error message (e.g., Toast or errorTextView)
                    // binding.errorTextView.text = state.message
                    // binding.errorTextView.visibility = View.VISIBLE
                }
                is RegisterUiState.ValidationError -> {
                    binding.buttonSignUp.isEnabled = true
                    binding.nameInputLayout.error = state.nameError
                    binding.emailInputLayout.error = state.emailError
                    binding.passwordInputLayout.error = state.passwordError
                    binding.confirmPasswordInputLayout.error = state.confirmPasswordError
                }
                is RegisterUiState.Idle -> {
                    binding.buttonSignUp.isEnabled = true
                    binding.nameInputLayout.error = null
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                    binding.confirmPasswordInputLayout.error = null
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
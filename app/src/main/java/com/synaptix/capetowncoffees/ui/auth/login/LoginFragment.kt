package com.synaptix.capetowncoffees.ui.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.synaptix.capetowncoffees.databinding.FragmentAuthLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private var _binding: FragmentAuthLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    private lateinit var signInClient: GoogleSignInClient

    // ───── tiny toast helper so we don't spam multiple toasts
    private var activeToast: Toast? = null
    private fun toast(message: CharSequence) {
        activeToast?.cancel()
        activeToast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).also { it.show() }
    }

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: throw IllegalStateException("No ID token from Google")
            Timber.d("Got Google ID token, forwarding to ViewModel")
            toast(getString(R.string.ctc_login_loading)) // "Signing in…"
            viewModel.loginWithGoogleToken(idToken)
        } catch (e: Exception) {
            Timber.e(e, "Google sign-in failed")
            toast(getString(R.string.ctc_login_google_failed)) // "Google sign-in failed. Please try again."
        }
    }

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

        binding.textRegisterSwap.setOnClickListener {
            Timber.d("Register swap clicked, navigating to RegisterFragment")
            findNavController().navigate(R.id.action_authLoginFragment_to_authRegisterFragment)
        }

        // Email/password sign-in
        binding.buttonlogin.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            Timber.d("Login button clicked with email=%s", email)
            viewModel.loginUser(email, password)
        }

        // Google sign-in
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        signInClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.buttonGoogleLogin.setOnClickListener {
            Timber.d("Google login button clicked")
            signInClient.signOut().addOnCompleteListener {
                googleLauncher.launch(signInClient.signInIntent)
            }
        }

        // Observe ViewModel state
        viewModel.loginState.observe(viewLifecycleOwner, Observer { state ->
            Timber.d("Observed loginState: %s", state)
            when (state) {
                is LoginUiState.Loading -> {
                    binding.buttonlogin.isEnabled = false
                    toast(getString(R.string.ctc_login_loading))
                }

                is LoginUiState.Success -> {
                    binding.buttonlogin.isEnabled = true
                    toast(getString(R.string.ctc_login_success))
                    try {
                        findNavController().navigate(R.id.action_authLoginFragment_to_homeFragment)
                    } catch (e: Exception) {
                        Timber.e(e, "Navigation failed with exception")
                    }
                    viewModel.resetState()
                }

                is LoginUiState.Error -> {
                    binding.buttonlogin.isEnabled = true
                    val msg = state.message.takeIf { it.isNotBlank() }
                        ?: getString(R.string.ctc_login_invalid_creds)
                    toast(msg)
                }

                is LoginUiState.ValidationError -> {
                    binding.buttonlogin.isEnabled = true
                    binding.emailInputLayout.error = state.emailError
                    binding.passwordInputLayout.error = state.passwordError
                }

                is LoginUiState.Idle -> {
                    binding.buttonlogin.isEnabled = true
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                }
            }
        })
    }

    override fun onDestroyView() {
        Timber.d("LoginFragment onDestroyView called")
        super.onDestroyView()
        activeToast?.cancel()
        activeToast = null
        _binding = null
    }
}
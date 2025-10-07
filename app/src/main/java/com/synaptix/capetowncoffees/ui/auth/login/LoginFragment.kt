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
// handling, navigation setup, and interaction with the ViewModel.
//* It also provided guidance on ConstraintLayout usage and UI event handling.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

    // ─────────── Dependencies & State ───────────
    // View binding is nullable to match Fragment view lifecycle; we gate access via 'binding'
    private var _binding: FragmentAuthLoginBinding? = null
    private val binding get() = _binding!!

    // Scoped VM instance for auth flows
    private val viewModel: LoginViewModel by viewModels()

    // Google sign-in client is created after the view is ready
    private lateinit var signInClient: GoogleSignInClient

    // Single active toast so messages don't stack
    private var activeToast: Toast? = null

    // ─────────── Activity Result Launchers ───────────
    // Handles the result from Google's sign-in intent and forwards the ID token to the VM
    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: throw IllegalStateException("No ID token from Google")
            Timber.d("Got Google ID token, forwarding to ViewModel")
            toast(getString(R.string.ctc_login_loading))
            viewModel.loginWithGoogleToken(idToken)
        } catch (e: Exception) {
            Timber.e(e, "Google sign-in failed")
            toast(getString(R.string.ctc_login_google_failed))
        }
    }

    // ─────────── Lifecycle ───────────
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

        // ─────────── UI Wiring ───────────
        // Swap to register screen
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

        // Configure Google sign-in and set click to launch
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        signInClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.buttonGoogleLogin.setOnClickListener {
            Timber.d("Google login button clicked")
            // We sign out to force the account chooser each time for clarity
            signInClient.signOut().addOnCompleteListener {
                googleLauncher.launch(signInClient.signInIntent)
            }
        }

        // ─────────── Observers ───────────
        // React to auth state changes and keep UI enabled/disabled appropriately
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
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
                    // Reset so a config change doesn't re-emit success
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
        }
    }

    override fun onDestroyView() {
        Timber.d("LoginFragment onDestroyView called")
        super.onDestroyView()
        activeToast?.cancel()
        activeToast = null
        _binding = null
    }

    // ─────────── Helpers ───────────
    // Small helper to show a single toast at a time
    private fun toast(message: CharSequence) {
        activeToast?.cancel()
        activeToast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).also { it.show() }
    }
}
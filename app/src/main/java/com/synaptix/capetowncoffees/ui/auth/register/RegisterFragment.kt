package com.synaptix.capetowncoffees.ui.auth.register

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
import com.synaptix.capetowncoffees.databinding.FragmentAuthRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    // ─────────── Dependencies & State ───────────
    // View binding follows Fragment view lifecycle; gate access via 'binding'
    private var _binding: FragmentAuthRegisterBinding? = null
    private val binding get() = _binding!!

    // VM that drives registration flow (DI provided)
    private val viewModel: RegisterViewModel by viewModels()

    // Google sign-in client is configured after view is created
    private lateinit var signInClient: GoogleSignInClient

    // Keep a single toast instance so messages don't stack
    private var activeToast: Toast? = null

    // ─────────── Activity Result Launchers ───────────
    // Handles Google sign-in result and forwards token to VM
    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: throw IllegalStateException("No ID token")
            toast(getString(R.string.ctc_register_loading))
            viewModel.registerWithGoogleToken(idToken)
        } catch (_: Exception) {
            toast(getString(R.string.ctc_register_google_failed))
        }
    }

    // ─────────── Lifecycle ───────────
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

        // ─────────── Init Google Sign-In ───────────
        // Request ID token + email; token is exchanged in VM
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        signInClient = GoogleSignIn.getClient(requireActivity(), gso)

        // ─────────── UI Wiring ───────────
        // Email/password sign-up
        binding.buttonSignUp.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val pass = binding.passwordEditText.text.toString()
            val confirm = binding.confirmPasswordEditText.text.toString()
            viewModel.registerUser(name, email, pass, confirm)
        }

        // Google sign-up (sign out first to force account chooser)
        binding.buttonGoogleRegister.setOnClickListener {
            signInClient.signOut().addOnCompleteListener {
                googleLauncher.launch(signInClient.signInIntent)
            }
        }

        // Swap to Login
        binding.textLoginSwap.setOnClickListener {
            findNavController().navigateUp()
        }

        // ─────────── Observers ───────────
        // React to registration state and update UI/feedback
        viewModel.registerState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RegisterUiState.Loading -> {
                    binding.buttonSignUp.isEnabled = false
                    toast(getString(R.string.ctc_register_loading))
                }
                is RegisterUiState.Success -> {
                    binding.buttonSignUp.isEnabled = true
                    toast(getString(R.string.ctc_register_success))
                    findNavController().navigate(R.id.action_authRegisterFragment_to_homeFragment)
                    viewModel.resetState()
                }
                is RegisterUiState.Error -> {
                    binding.buttonSignUp.isEnabled = true
                    val msg = state.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.ctc_register_error_generic)
                    toast(msg)
                }
                is RegisterUiState.ValidationError -> {
                    binding.buttonSignUp.isEnabled = true
                    binding.nameInputLayout.error = state.nameError
                    binding.emailInputLayout.error = state.emailError
                    binding.passwordInputLayout.error = state.passwordError
                    binding.confirmPasswordInputLayout.error = state.confirmPasswordError
                    toast(getString(R.string.ctc_register_fix_errors))
                }
                is RegisterUiState.Idle -> {
                    binding.buttonSignUp.isEnabled = true
                    binding.nameInputLayout.error = null
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                    binding.confirmPasswordInputLayout.error = null
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeToast?.cancel()
        activeToast = null
        _binding = null
    }

    // ─────────── Helpers ───────────
    // Small toast helper that cancels any prior toast before showing a new one
    private fun toast(msg: CharSequence) {
        activeToast?.cancel()
        activeToast = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).also { it.show() }
    }
}

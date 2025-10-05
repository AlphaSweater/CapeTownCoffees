package com.synaptix.capetowncoffees.domain.usecase.auth

import com.google.firebase.auth.FirebaseAuthException
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import timber.log.Timber
import javax.inject.Inject

class LoginWithGoogleUseCase @Inject constructor(
    private val repo: ICoffeeUserRepository
) {
    suspend operator fun invoke(idToken: String): LoginResult {
        Timber.d("LoginWithGoogleUseCase: start")
        return repo.signInWithGoogle(idToken).fold(
            onSuccess = {
                Timber.d("LoginWithGoogleUseCase: success uid=%s", it.uid)
                LoginResult.Success
            },
            onFailure = { e ->
                Timber.e(e, "LoginWithGoogleUseCase: failure")
                val msg = e.message ?: "Unknown error"

                //informs the user.
                val mapped = when {
                    e is FirebaseAuthException &&
                            e.errorCode == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                        LoginResult.Error("Account exists with a different sign-in method. Please use your original method.")
                    msg.contains("network", true) -> LoginResult.Error("Network error. Please try again.")
                    msg.contains("timeout", true) -> LoginResult.Error("Request timed out. Please retry.")
                    else -> LoginResult.Error(msg)
                }
                mapped
            }
        )
    }
}
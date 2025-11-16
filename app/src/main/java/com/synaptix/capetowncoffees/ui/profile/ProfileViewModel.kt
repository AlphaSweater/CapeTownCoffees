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
//* ChatGPT provided assistance in designing ViewModel logic, LiveData handling, and
//implementing clean MVVM architecture principles.
//* It also helped refine data flow between repositories and UI layers.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
public class ProfileViewModel @Inject constructor(
    private val getUserProfileUseCase: GetUserProfileUseCase
) : ViewModel() {

    // ─────────── State ───────────
    // Exposes user resource to the UI; starts as loading.
    private val _userState = MutableStateFlow<Resource<CoffeeUser>>(Resource.Loading)
    public val userState: StateFlow<Resource<CoffeeUser>> = _userState.asStateFlow()

    // Tracks a simple derived field for quick access (display name).
    private val _userName = MutableLiveData("")

    // Gamification state derived from total reviews written by the user.
    data class GamificationUi(
        val reviewCount: Int = 0,
        val level: Int = 1,
        val progressPercent: Int = 0,
        val nextTarget: Int? = 5,
        val badgeCount: Int = 0
    )

    private val _gamificationState = MutableStateFlow(GamificationUi())
    public val gamificationState: StateFlow<GamificationUi> = _gamificationState.asStateFlow()

    // ─────────── Public API ───────────
    // Lightweight getter for current cached name.
    public fun getUserName(): String = _userName.value ?: ""

    // Allows manual name override if needed by the UI.
    public fun setUserName(value: String) {
        if (_userName.value != value) _userName.value = value
    }

    // ─────────── Actions ───────────
    // Loads the profile and maps it to UI state; keeps a friendly display name.
    public fun loadUserProfile() {
        viewModelScope.launch {
            _userState.value = Resource.Loading
            try {
                val result = getUserProfileUseCase()
                result
                    .onSuccess { user ->
                        val fullName = user.fullName.trim()
                        Timber.d("Full name: $fullName")
                        _userName.value = fullName.ifEmpty { "User" }
                        Timber.d("User name set to: ${_userName.value}")
                        _userState.value = Resource.Success(user)
                        Timber.d("Loading gamification from reviewCount for userId=${user.id}, reviewCount=${user.reviewCount}")
                        loadGamification(user.reviewCount)
                    }
                    .onFailure { e ->
                        _userState.value = Resource.Error(e.message ?: "Failed to load profile")
                    }
            } catch (e: Exception) {
                _userState.value = Resource.Error(
                    "Failed to load profile: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    private fun loadGamification(reviewCount: Int) {
        try {
            val newState = computeGamification(reviewCount)
            Timber.d("Gamification: reviewCount=$reviewCount, level=${newState.level}, progress=${newState.progressPercent}, badges=${newState.badgeCount}")
            _gamificationState.value = newState
        } catch (e: Exception) {
            Timber.e(e, "Failed to load gamification from reviewCount=$reviewCount")
        }
    }

    private fun computeGamification(reviewCount: Int): GamificationUi {
        val thresholds = listOf(5, 7, 9, 10)
        val badges = thresholds.count { reviewCount >= it }
        val level = badges + 1
        val maxTarget = thresholds.last()
        val cappedReviews = reviewCount.coerceAtMost(maxTarget)
        val progressPercent = if (maxTarget > 0) {
            (cappedReviews * 100) / maxTarget
        } else {
            0
        }
        val nextTarget = thresholds.firstOrNull { reviewCount < it }
        return GamificationUi(
            reviewCount = reviewCount,
            level = level,
            progressPercent = progressPercent,
            nextTarget = nextTarget,
            badgeCount = badges
        )
    }
}

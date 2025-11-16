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
//* ChatGPT provided assistance in designing ViewModel logic, state management patterns,
//* and implementing clean MVVM architecture principles for multi-step forms.
//* It also helped generate useful comments and structure the review submission flow.
//======================================================================================

package com.synaptix.capetowncoffees.ui.review

import android.net.Uri
import android.os.Bundle
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.SimpleViewModel
import com.synaptix.capetowncoffees.ui.common.viewmodel.state
import com.synaptix.capetowncoffees.domain.usecase.coffeeReview.CreateCoffeeReviewUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.IncrementUserReviewCountUseCase
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.InAppReview
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

/**
 * Shared ViewModel for the multi-step review process.
 *
 * This ViewModel manages the review state across all steps (rating, text, images)
 * and handles the submission logic. All review fragments share this single instance
 * to maintain state consistency.
 *
 * Usage in Fragment:
 * ```
 * private val vm: ReviewViewModel by activityViewModels()
 * ```
 */
@HiltViewModel
@Suppress("unused")
class ReviewViewModel @Inject constructor(
    // Use case to create (persist) an in-app coffee review
    private val createCoffeeReviewUseCase: CreateCoffeeReviewUseCase,
    // Use case to obtain current user profile (if any)
    private val getUserProfileUseCase: GetUserProfileUseCase,
    // Use case to bump the current user's reviewCount for gamification
    private val incrementUserReviewCountUseCase: IncrementUserReviewCountUseCase,
) : SimpleViewModel() {

    // ─────────── Screen Args ───────────
    object ScreenArgs {
        const val PLACE_ID = "placeId"
        const val PLACE_NAME = "placeName"
    }

    // ─────────── State ───────────
    /**
     * Place ID for the review being created.
     */
    val placeId = state("")

    /**
     * Current rating (0-5 stars).
     */
    val rating = state(0f)

    /**
     * Review text written by the user.
     */
    val reviewText = state("")

    /**
     * Optional image URI for the review.
     */
    val imageUri = state<Uri?>(null)

    /**
     * Optional place name for display purposes (e.g., "Reviewing: Bean There Coffee")
     */
    val placeName = state<String?>(null)

    /**
     * Tracks if a submission is in progress to prevent duplicate submissions.
     */
    val isSubmitting = state(false)

    // ─────────── Steps ───────────
    enum class ReviewStep { RATING, TEXT, IMAGE, COMPLETE }
    val currentStep = state(ReviewStep.RATING)

    // ─────────── Initialization ───────────
    override fun start(args: Bundle?) {
        super.start(args)

        val id = args?.getString(ScreenArgs.PLACE_ID) ?: ""
        val name = args?.getString(ScreenArgs.PLACE_NAME)

        Timber.d("ReviewViewModel started for placeId=$id, placeName=$name")

        placeId.set(id)
        placeName.set(name)
    }

    // ─────────── Step 1: Rating ───────────
    /**
     * Update the rating value from Step 1.
     * @param newRating The star rating (0-5)
     */
    fun updateRating(newRating: Float) {
        Timber.d("Rating updated: $newRating")
        rating.set(newRating)
    }

    // ─────────── Step 2: Review Text ───────────
    /**
     * Update the review text from Step 2.
     * @param text The user's written review
     */
    fun updateReviewText(text: String) {
        Timber.d("Review text updated: ${text.length} chars")
        reviewText.set(text)
    }

    // ─────────── Step 3: Image ───────────
    /**
     * Update the review image from Step 3.
     * @param uri The URI of the selected image
     */
    @Suppress("unused")
    fun updateImageUri(uri: Uri?) {
        Timber.d("Image URI updated: $uri")
        imageUri.set(uri)
    }

    // ─────────── Step Navigation Helpers ───────────
    fun nextStep() {
        when (currentStep.value) {
            ReviewStep.RATING -> if (canProceedFromStep1()) currentStep.set(ReviewStep.TEXT)
            ReviewStep.TEXT -> if (canProceedFromStep2()) currentStep.set(ReviewStep.IMAGE)
            ReviewStep.IMAGE -> if (canProceedFromStep3()) submitReview()
            ReviewStep.COMPLETE -> Unit
        }
    }

    fun prevStep() {
        when (currentStep.value) {
            ReviewStep.RATING -> Unit
            ReviewStep.TEXT -> currentStep.set(ReviewStep.RATING)
            ReviewStep.IMAGE -> currentStep.set(ReviewStep.TEXT)
            ReviewStep.COMPLETE -> Unit // Do not go back from completion screen
        }
    }

    fun goToStep(step: ReviewStep) {
        // Basic guard: only allow forward navigation if prerequisites satisfied
        val allowed = when (step) {
            ReviewStep.RATING -> true
            ReviewStep.TEXT -> canProceedFromStep1()
            ReviewStep.IMAGE -> canProceedFromStep1() // text optional
            ReviewStep.COMPLETE -> isValid()
        }
        if (allowed) currentStep.set(step)
    }

    /**
     * Validation helper for UI - can be used to enable/disable Next buttons.
     */
    fun canProceedFromStep1(): Boolean = rating.value > 0f

    fun canProceedFromStep2(): Boolean = true // Text is optional

    fun canProceedFromStep3(): Boolean = true // Image is optional

    /**
     * Validates if the review has minimum required data for submission.
     * Currently requires a rating > 0.
     */
    private fun isValid(): Boolean = rating.value > 0f

    // ─────────── Submission ───────────
    /**
     * Submit the complete review.
     * Call this from the final step when the user confirms submission.
     */
    fun submitReview() {
        if (!isValid()) {
            Timber.w("Attempted to submit invalid review")
            main { send(Effect.Message("Please provide at least a rating")) }
            return
        }

        if (isSubmitting.value) {
            Timber.w("Submission already in progress")
            return
        }

        main {
            isSubmitting.set(true)
            Timber.i("Submitting review: placeId=${placeId.value}, rating=${rating.value}, text=${reviewText.value}")

            try {
                // Resolve the reviewer id from the current authenticated user if present
                val reviewerId = try {
                    getUserProfileUseCase().getOrNull()?.id ?: "anonymous"
                } catch (_: Exception) {
                    "anonymous"
                }

                // Build an InAppReview from current state
                val review: InAppReview = CoffeeReview.newInAppSubmission(
                    reviewerId = reviewerId,
                    placeId = placeId.value,
                    rating = rating.value.toDouble(),
                    text = reviewText.value
                )

                // Call the use case to persist the review
                val result = createCoffeeReviewUseCase(review, placeId.value)

                if (result.isSuccess) {
                    Timber.i("Review submitted successfully: ${result.getOrNull()}")

                    // Increment the user's review count for gamification; failure here
                    // should not block the main review submission.
                    try {
                        incrementUserReviewCountUseCase().onFailure { e ->
                            Timber.e(e, "Failed to increment user review count")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Exception while incrementing user review count")
                    }

                    send(Effect.Message("Review submitted successfully!"))
                    currentStep.set(ReviewStep.COMPLETE)
                } else {
                    val err = result.exceptionOrNull() ?: Exception("Unknown error")
                    Timber.e(err, "Failed to submit review")
                    send(Effect.Message("Failed to submit review: ${err.message}"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to submit review")
                send(Effect.Message("Failed to submit review: ${e.message}"))
            } finally {
                isSubmitting.set(false)
            }
        }
    }

    /**
     * Reset the review data (useful after submission or cancellation).
     */
    fun resetReview() {
        Timber.d("Resetting review data")
        val currentPlaceId = placeId.value
        rating.set(0f)
        reviewText.set("")
        imageUri.set(null)
        placeId.set(currentPlaceId)
        currentStep.set(ReviewStep.RATING)
    }

    fun finalizeReviewFlow() {
        // Called when user closes the completion screen
        resetReview()
    }
}

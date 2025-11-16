package com.synaptix.capetowncoffees.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.ui.common.viewmodel.collect
import com.synaptix.capetowncoffees.ui.common.viewmodel.start
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single container fragment that manages all review steps by switching child layouts.
 * Inflates the legacy step layouts as children and wires their controls to the shared ViewModel.
 */
@AndroidEntryPoint
class ReviewContainerFragment : Fragment() {

    private val vm: ReviewViewModel by activityViewModels()

    private lateinit var stepRating: View
    private lateinit var stepText: View
    private lateinit var stepImage: View
    private lateinit var stepComplete: View

    private lateinit var nextRating: View
    private lateinit var nextText: View
    private lateinit var nextImage: View
    private var doneComplete: View? = null

    private lateinit var closeRating: View
    private lateinit var closeText: View
    private lateinit var closeImage: View
    private var closeComplete: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_review_container, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        start(vm)
        inflateStepLayouts(view)
        bindStepControls()
        setupCollectors()
        handleSystemBack()
    }

    private fun inflateStepLayouts(root: View) {
        val inflater = LayoutInflater.from(root.context)
        val parent = root as ViewGroup
        stepRating = inflater.inflate(R.layout.fragment_review_step1, parent, false)
        stepText = inflater.inflate(R.layout.fragment_review_step2, parent, false)
        stepImage = inflater.inflate(R.layout.fragment_review_step3, parent, false)
        stepComplete = inflater.inflate(R.layout.fragment_review_complete, parent, false)
        parent.apply {
            addView(stepRating)
            addView(stepText)
            addView(stepImage)
            addView(stepComplete)
        }
    }

    private fun bindStepControls() {
        // Rating step
        stepRating.findViewById<android.widget.RatingBar>(R.id.ratingBar).apply {
            setIsIndicator(false)
            rating = vm.rating.value
            setOnRatingBarChangeListener { _, r, fromUser -> if (fromUser) vm.updateRating(r) }
        }
        nextRating = stepRating.findViewById(R.id.btnNext)
        closeRating = stepRating.findViewById(R.id.btnClose)

        // Text step
        stepText.findViewById<android.widget.EditText>(R.id.etReview).apply {
            setText(vm.reviewText.value)
            setSelection(text.length)
            addTextChangedListener { vm.updateReviewText(it?.toString() ?: "") }
        }
        nextText = stepText.findViewById(R.id.btnNext)
        closeText = stepText.findViewById(R.id.btnClose)

        // Image step
        stepImage.findViewById<View>(R.id.coffeeImage)?.setOnClickListener {
            // Placeholder for image picker
        }
        nextImage = stepImage.findViewById(R.id.btnNext)
        closeImage = stepImage.findViewById(R.id.btnClose)

        // Complete step
        doneComplete = stepComplete.findViewById(R.id.btnDone)
        closeComplete = stepComplete.findViewById(R.id.btnClose)

        // Click listeners for navigation
        nextRating.setOnClickListener { vm.nextStep() }
        nextText.setOnClickListener { vm.nextStep() }
        nextImage.setOnClickListener { vm.nextStep() } // triggers submit
        doneComplete?.setOnClickListener { vm.finalizeReviewFlow(); requireActivity().onBackPressedDispatcher.onBackPressed() }
        closeComplete?.setOnClickListener { vm.finalizeReviewFlow(); requireActivity().onBackPressedDispatcher.onBackPressed() }

        val cancel = { vm.resetReview(); requireActivity().onBackPressedDispatcher.onBackPressed() }
        closeRating.setOnClickListener { cancel() }
        closeText.setOnClickListener { cancel() }
        closeImage.setOnClickListener { cancel() }
    }

    private fun setupCollectors() {
        collect(vm.currentStep.flow) { step ->
            stepRating.isVisible = step == ReviewViewModel.ReviewStep.RATING
            stepText.isVisible = step == ReviewViewModel.ReviewStep.TEXT
            stepImage.isVisible = step == ReviewViewModel.ReviewStep.IMAGE
            stepComplete.isVisible = step == ReviewViewModel.ReviewStep.COMPLETE
            updateEnablement(step)
        }
        collect(vm.rating.flow) {
            // keep rating bar synced
            stepRating.findViewById<android.widget.RatingBar>(R.id.ratingBar)?.rating = vm.rating.value
            updateEnablement(vm.currentStep.value)
        }
        collect(vm.isSubmitting.flow) { submitting ->
            if (vm.currentStep.value == ReviewViewModel.ReviewStep.IMAGE) {
                nextImage.isEnabled = !submitting
                nextImage.alpha = if (submitting) 0.5f else 1f
            }
        }
    }

    private fun updateEnablement(step: ReviewViewModel.ReviewStep) {
        val canProceed = when (step) {
            ReviewViewModel.ReviewStep.RATING -> vm.canProceedFromStep1()
            ReviewViewModel.ReviewStep.TEXT -> vm.canProceedFromStep2()
            ReviewViewModel.ReviewStep.IMAGE -> vm.canProceedFromStep3()
            ReviewViewModel.ReviewStep.COMPLETE -> true
        }
        when (step) {
            ReviewViewModel.ReviewStep.RATING -> nextRating.apply { isEnabled = canProceed; alpha = if (canProceed) 1f else 0.5f }
            ReviewViewModel.ReviewStep.TEXT -> nextText.apply { isEnabled = canProceed; alpha = if (canProceed) 1f else 0.5f }
            ReviewViewModel.ReviewStep.IMAGE -> nextImage.apply { isEnabled = canProceed; alpha = if (canProceed) 1f else 0.5f }
            ReviewViewModel.ReviewStep.COMPLETE -> doneComplete?.apply { isEnabled = true; alpha = 1f }
        }
    }

    private fun handleSystemBack() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            when (vm.currentStep.value) {
                ReviewViewModel.ReviewStep.RATING -> { vm.resetReview(); remove(); requireActivity().onBackPressedDispatcher.onBackPressed() }
                ReviewViewModel.ReviewStep.TEXT -> vm.prevStep()
                ReviewViewModel.ReviewStep.IMAGE -> vm.prevStep()
                ReviewViewModel.ReviewStep.COMPLETE -> { vm.finalizeReviewFlow(); remove(); requireActivity().onBackPressedDispatcher.onBackPressed() }
            }
        }
    }
}

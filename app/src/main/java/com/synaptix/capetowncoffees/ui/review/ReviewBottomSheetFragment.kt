package com.synaptix.capetowncoffees.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.ui.common.viewmodel.collect
import com.synaptix.capetowncoffees.ui.common.viewmodel.start
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReviewBottomSheetFragment : BottomSheetDialogFragment() {

    private val vm: ReviewViewModel by activityViewModels()

    private lateinit var ratingBar: RatingBar
    private lateinit var etReview: EditText
    private lateinit var tvCharCount: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnSubmit: View
    private lateinit var btnClose: View
    private lateinit var btnNotNow: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_review_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        start(vm) // important: passes nav args to ViewModel start()

        bindViews(view)
        bindUiToViewModel()
        setupCollectors()
    }

    private fun bindViews(root: View) {
        ratingBar = root.findViewById(R.id.ratingBar)
        etReview = root.findViewById(R.id.etReview)
        tvCharCount = root.findViewById(R.id.tvCharCount)
        tvTitle = root.findViewById(R.id.tvTitle)
        tvSubtitle = root.findViewById(R.id.tvSubtitle)
        btnSubmit = root.findViewById(R.id.btnSubmit)
        btnClose = root.findViewById(R.id.btnClose)
        btnNotNow = root.findViewById(R.id.btnNotNow)
    }

    private fun bindUiToViewModel() {
        // Title with place name if available
        val placeName = vm.placeName.value
        tvTitle.text = if (!placeName.isNullOrBlank()) {
            getString(R.string.review_title_with_place, placeName)
        } else {
            getString(R.string.review_step1_title)
        }

        tvSubtitle.text = getString(R.string.review_step1_message)

        // Rating initial + listener
        ratingBar.apply {
            rating = vm.rating.value
            setIsIndicator(false)
            setOnRatingBarChangeListener { _, r, fromUser ->
                if (fromUser) vm.updateRating(r)
            }
        }

        // Review text initial + listener
        etReview.apply {
            setText(vm.reviewText.value)
            setSelection(text?.length ?: 0)
            addTextChangedListener { text ->
                vm.updateReviewText(text?.toString() ?: "")
                updateCharCount(text?.length ?: 0)
            }
            updateCharCount(text?.length ?: 0)
        }

        // Submit
        btnSubmit.setOnClickListener {
            vm.submitReview()
        }

        // Close / Not now
        val cancelAndClose = {
            vm.resetReview()
            dismissAllowingStateLoss()
        }
        btnClose.setOnClickListener { cancelAndClose() }
        btnNotNow.setOnClickListener { cancelAndClose() }
    }

    private fun setupCollectors() {
        // Keep rating in sync if VM changes externally
        collect(vm.rating.flow) { value ->
            if (ratingBar.rating != value) {
                ratingBar.rating = value
            }
            updateSubmitEnabled()
        }

        // Keep text in sync if VM changes externally
        collect(vm.reviewText.flow) { text ->
            if (etReview.text?.toString() != text) {
                etReview.setText(text)
                etReview.setSelection(text.length)
            }
            updateCharCount(text.length)
        }

        // Disable while submitting
        collect(vm.isSubmitting.flow) { submitting ->
            btnSubmit.isEnabled = !submitting && vm.canProceedFromStep1()
            btnSubmit.alpha = if (btnSubmit.isEnabled) 1f else 0.5f

            if (btnSubmit !is TextView) return@collect
            if (submitting) {
                (btnSubmit as TextView).text = getString(R.string.review_submitting)
            } else {
                (btnSubmit as TextView).text = getString(R.string.review_submit_button)
            }
        }

        // Auto-close when submission flow reaches COMPLETE
        collect(vm.currentStep.flow) { step ->
            if (step == ReviewViewModel.ReviewStep.COMPLETE) {
                // Notify host fragments/activities that a review was submitted so they can refresh
                val result = Bundle().apply { putString("place_id", vm.placeId.value) }
                parentFragmentManager.setFragmentResult("review_submitted", result)

                // Let the ViewModel clean up, then dismiss
                vm.finalizeReviewFlow()
                dismissAllowingStateLoss()
            }
        }
    }

    private fun updateCharCount(length: Int) {
        val max = 300 // or whatever you like
        tvCharCount.text = "$length/$max"
    }

    private fun updateSubmitEnabled() {
        val canProceed = vm.canProceedFromStep1()
        btnSubmit.isEnabled = canProceed && !vm.isSubmitting.value
        btnSubmit.alpha = if (btnSubmit.isEnabled) 1f else 0.5f
    }
}
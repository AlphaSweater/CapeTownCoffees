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
//* handling, navigation setup, and interaction with the ViewModel.
//* It also provided guidance on ConstraintLayout usage and UI event handling.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.coffeeDetail

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentCoffeeDetailBinding
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.GooglePlaceReview
import com.synaptix.capetowncoffees.domain.model.InAppReview
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeReview.AddCoffeeReviewReactionUseCase
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.collect
import com.synaptix.capetowncoffees.ui.common.viewmodel.collectLoadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.start
import com.synaptix.capetowncoffees.ui.coffeeDetail.adapters.ReviewsAdapter
import com.synaptix.capetowncoffees.ui.review.ReviewBottomSheetFragment
import com.synaptix.capetowncoffees.ui.review.ReviewViewModel
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CoffeeDetailFragment : Fragment() {

    // ─────────── Constants ───────────
    // Keep magic numbers/ids here for clarity and reuse.
    private companion object {
        private const val PHOTO_MAX_HEIGHT_DP = 300
        private const val EFFECT_OPEN_MAP = "action_open_external_map"
        private const val EFFECT_DIAL = "action_dial_phone"
        private const val EFFECT_OPEN_REVIEW_GALLERY = "action_open_review_gallery"
    }

    // ─────────── Injected Dependencies ───────────
    // Provided by Hilt; we delegate work to domain/util layers.
    @Inject lateinit var locationUtil: LocationUtil
    @Inject lateinit var coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase
    @Inject lateinit var addCoffeeReviewReactionUseCase: AddCoffeeReviewReactionUseCase
    @Inject lateinit var reviewsAdapterFactory: ReviewsAdapter.Factory

    // ─────────── UI & State ───────────
    // VM owns business/UI state; Fragment binds and handles Android-only work.
    private val vm: CafeDetailViewModel by viewModels()

    private var _binding: FragmentCoffeeDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var inAppReviewsAdapter: ReviewsAdapter
    private lateinit var googleReviewsAdapter: ReviewsAdapter

    private var allInAppReviews: List<CoffeeReview> = emptyList()
    private var currentInAppDisplayCount = 3
    private val inAppReviewsPageSize = 3

    // ─────────── Lifecycle: View Creation ───────────
    // Inflate view binding and return root for rendering.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoffeeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    // ─────────── Lifecycle: View Bound ───────────
    // Wire adapters, listeners, and reactive collectors.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        start(vm) // enter SimpleVM lifecycle
        setupRecyclers()
        setupUiListeners()
        setupCollectors()
        getCurrentLocation() // kick off distance once location is available
    }

    // ─────────── Recycler & Adapter ───────────
    // Sets up the reviews list and routes item interactions to the VM.
    private fun setupRecyclers() = with(binding) {
        val clickHandler: (ReviewsAdapter.Click) -> Unit = { click ->
            when (click) {
                is ReviewsAdapter.Click.Like      -> onReviewReactionClick(click.reviewId, isLike = true)
                is ReviewsAdapter.Click.Dislike   -> onReviewReactionClick(click.reviewId, isLike = false)
                is ReviewsAdapter.Click.OpenPhoto -> vm.onOpenPhoto(
                    reviewId = click.reviewId,
                    startIndex = click.startIndex,
                    urls = click.urls
                )
            }
        }

        // In-App Reviews Adapter
        inAppReviewsAdapter = reviewsAdapterFactory.create(viewLifecycleOwner.lifecycleScope, clickHandler)
        rvInAppReviews.layoutManager = LinearLayoutManager(requireContext())
        rvInAppReviews.adapter = inAppReviewsAdapter
        rvInAppReviews.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))

        // Google Reviews Adapter
        googleReviewsAdapter = reviewsAdapterFactory.create(viewLifecycleOwner.lifecycleScope, clickHandler)
        rvGoogleReviews.layoutManager = LinearLayoutManager(requireContext())
        rvGoogleReviews.adapter = googleReviewsAdapter
        rvGoogleReviews.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
    }

    // ─────────── UI Listeners ───────────
    // Only platform-level clicks here; view-model events are collected reactively.
    private fun setupUiListeners() = with(binding) {
        btnBack.setOnClickListener { findNavController().navigateUp() }
        tvDistance.setOnClickListener { getCurrentLocation() } // quick refresh for distance

        // Open review flow (NEW bottom sheet system)
        btnReview.setOnClickListener {
            val placeId = vm.placeId ?: ""
            val placeName = vm.ui.value.name

            if (placeId.isBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Cannot open review: missing place id",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val args = Bundle().apply {
                putString(ReviewViewModel.ScreenArgs.PLACE_ID, placeId)
                putString(ReviewViewModel.ScreenArgs.PLACE_NAME, placeName)
            }

            ReviewBottomSheetFragment().apply {
                arguments = args
            }.show(parentFragmentManager, "reviewBottomSheet")
        }

        // Show More button for in-app reviews
        tvShowMoreInApp.setOnClickListener {
            currentInAppDisplayCount += inAppReviewsPageSize
            updateInAppReviewsDisplay()
        }
    }

    /**
     * Handles a like/dislike click for a given review by calling the domain use case.
     *
     * @param reviewId The ID of the review being reacted to.
     * @param isLike True if the user clicked Like, false if they clicked Dislike.
     */
    private fun onReviewReactionClick(reviewId: String, isLike: Boolean) {
        val placeId = vm.placeId
        if (placeId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Missing place id for review reaction", Toast.LENGTH_SHORT).show()
            return
        }

        // Find the in-memory review and apply optimistic update so UI reflects the action immediately
        val idx = allInAppReviews.indexOfFirst { it is InAppReview && it.id == reviewId }
        val oldItem = if (idx >= 0) allInAppReviews[idx] as InAppReview else null

        val desiredType = if (isLike) "like" else "dislike"
        val newType = if (oldItem?.userReactionType == desiredType) null else desiredType

        if (oldItem != null) {
            // Apply optimistic change locally and refresh the visible slice
            val newItem = oldItem.copy(userReactionType = newType)
            allInAppReviews = allInAppReviews.toMutableList().also { it[idx] = newItem }
            updateInAppReviewsDisplay()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = addCoffeeReviewReactionUseCase(
                placeId = placeId,
                reviewId = reviewId,
                isLike = isLike,
                userId = vm.currentUserId
            )

            result
                .onFailure {
                    Timber.e(it, "Failed to react to review (reviewId=$reviewId)")
                    Toast.makeText(requireContext(), it.message ?: "Failed to update reaction", Toast.LENGTH_SHORT).show()

                    // Revert optimistic change on failure
                    if (oldItem != null) {
                        allInAppReviews = allInAppReviews.toMutableList().also { it[idx] = oldItem }
                        updateInAppReviewsDisplay()
                    }
                }
                .onSuccess {
                    // Refresh reviews to sync aggregate counts and authoritative reaction state
                    vm.retryReviews()
                }
        }
    }


    // ─────────── Collectors (Effects & State) ───────────
    // Consume one-shot effects and state streams from the VM.
    private fun setupCollectors() {
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message  -> Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
                is Effect.Navigate -> when (eff.route) {
                    EFFECT_OPEN_MAP -> {
                        val mapUrl = eff.args?.getString("map_url") ?: return@collect
                        startActivity(Intent(Intent.ACTION_VIEW, mapUrl.toUri()))
                    }
                    EFFECT_DIAL -> {
                        val digits = eff.args?.getString("phone")?.filter { it.isDigit() } ?: return@collect
                        startActivity(Intent(Intent.ACTION_DIAL, "tel:$digits".toUri()))
                    }
                    EFFECT_OPEN_REVIEW_GALLERY -> {
                        val urls = eff.args?.getStringArrayList("urls").orEmpty()
                        val start = eff.args?.getInt("start") ?: 0
                        val uri = urls.getOrNull(start)?.toUri() ?: return@collect
                        startActivity(Intent(Intent.ACTION_VIEW, uri)) // basic viewer; replace with in-app gallery when ready
                    }
                    else -> findNavController().navigate(eff.route.toUri(), null)
                }
            }
        }

        // Place loadable: drive image and loading indicator.
        collectLoadable(vm.place) { loadable ->
            when (loadable) {
                Loadable.Uninitialized -> showLoading(false)
                Loadable.Loading       -> showLoading(true)
                is Loadable.Data       -> {
                    showLoading(false)
                    updateImage(loadable.value)
                }
                is Loadable.Error      -> {
                    showLoading(false)
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                    Timber.e("Place load error: ${loadable.error.message}")
                }
            }
        }

        // UI-ready fields: bind straightforwardly with light view logic.
        collect(vm.ui.flow) { ui ->
            binding.tvCafeName.text = ui.name

            // Address text + underline when clickable
            binding.tvCafeAddress.text = ui.address
            binding.tvCafeAddress.paintFlags = if (ui.addressClickable)
                binding.tvCafeAddress.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            else
                binding.tvCafeAddress.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            binding.tvCafeAddress.isClickable = ui.addressClickable
            binding.tvCafeAddress.setOnClickListener(
                if (ui.addressClickable) View.OnClickListener { vm.onAddressClicked() } else null
            )

            // Opening hours text is pre-formatted by the VM
            binding.tvOpeningHours.text = if (ui.hasOpeningHours) ui.openingHoursText
            else getString(R.string.no_opening_hours_available)

            // Phone (visible only when provided)
            binding.tvPhone.isVisible = ui.phoneNumber != null
            binding.tvPhone.text = ui.phoneNumber.orEmpty()
            binding.tvPhone.setOnClickListener(
                if (ui.phoneNumber != null) View.OnClickListener { vm.onPhoneClicked() } else null
            )

            // Rating block toggles as a unit
            binding.ratingBar.isVisible = ui.showRating
            binding.tvRating.isVisible = ui.showRating
            if (ui.showRating) {
                binding.ratingBar.rating = ui.rating?.toFloat() ?: 0f
                binding.tvRating.text = ui.ratingCountText ?: ui.rating?.toString() ?: ""
            }

            // Distance (computed when user + place locations are known)
            binding.tvDistance.isVisible = ui.showDistance
            binding.ivPin.isVisible = ui.showDistance
            binding.tvDistance.text = ui.distanceText.orEmpty()
        }

        // Reviews: show skeleton while loading, list or error otherwise.
        collectLoadable(vm.reviews) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> showReviewsSkeleton()
                is Loadable.Data  -> renderReviews(loadable.value)
                is Loadable.Error -> showReviewsError(loadable.error.message) { vm.retryReviews() }
            }
        }
    }

    // ─────────── Render Helpers ───────────
    // Small view helpers to keep collectors tidy.
    private fun renderReviews(list: List<CoffeeReview>) = with(binding) {
        // Separate reviews by type
        val inAppReviews = list.filterIsInstance<InAppReview>()
        val googleReviews = list.filterIsInstance<GooglePlaceReview>()

        // Store all in-app reviews and reset pagination
        allInAppReviews = inAppReviews
        currentInAppDisplayCount = inAppReviewsPageSize

        // Show empty state if no reviews
        reviewsEmpty.isVisible = list.isEmpty()

        // Update in-app reviews section
        if (inAppReviews.isNotEmpty()) {
            inAppReviewsSection.isVisible = true
            updateInAppReviewsDisplay()
        } else {
            inAppReviewsSection.isVisible = false
        }

        // Update Google reviews section
        if (googleReviews.isNotEmpty()) {
            googleReviewsSection.isVisible = true
            googleReviewsAdapter.updateItems(googleReviews)
        } else {
            googleReviewsSection.isVisible = false
        }
    }

    private fun updateInAppReviewsDisplay() = with(binding) {
        val displayReviews = allInAppReviews.take(currentInAppDisplayCount)
        inAppReviewsAdapter.updateItems(displayReviews)

        // Show/hide "Show More" button
        val hasMore = allInAppReviews.size > currentInAppDisplayCount
        tvShowMoreInApp.isVisible = hasMore

        // Update button text with count
        if (hasMore) {
            val remaining = allInAppReviews.size - currentInAppDisplayCount
            tvShowMoreInApp.text = getString(R.string.show_more_with_count, remaining)
        }
    }

    private fun showReviewsSkeleton() = with(binding) {
        reviewsEmpty.isVisible = false
        // Hook shimmer/skeleton view here when available
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showReviewsError(msg: String, retry: () -> Unit) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        // Wire a retry button if a dedicated error view exists
    }

    private fun updateImage(cafe: CoffeePlaceFull) {
        val imageView = binding.ivImage
        val placeholderRes = R.drawable.featured_placeholder

        // Start from a known state
        imageView.setImageResource(placeholderRes)
        imageView.contentDescription = cafe.name?.let { "$it photo" }
            ?: getString(R.string.coffee_image)

        viewLifecycleOwner.lifecycleScope.launch {
            val uri = runCatching {
                coffeePlaceUtilsUseCase.getPhotoUriFromMetadata(
                    photoMetadata = cafe.images?.firstOrNull(),
                    isCached = cafe.isCached,
                    cachedImageUrl = cafe.cachedImageUrl,
                    maxHeightDp = PHOTO_MAX_HEIGHT_DP
                )
            }.getOrNull()

            if (!isAdded) return@launch

            if (uri != null) {
                imageView.loadWithGlide(uri, placeholderRes)
            }
        }
    }

    private fun ImageView.loadWithGlide(source: Any, placeholderRes: Int) {
        Glide.with(this)
            .load(source)
            .placeholder(placeholderRes)
            .error(placeholderRes)
            .into(this)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
    }

    // ─────────── Location ───────────
    // Requests a single current location and forwards it to the VM for distance calc.
    private fun getCurrentLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            @Suppress("MissingPermission")
            runCatching { locationUtil.getCurrentLatLng().getOrNull() }
                .onSuccess { it?.let(vm::onUserLocation) }
                .onFailure {
                    Timber.e(it, "Failed to get current location")
                    // VM will hide distance if it cannot compute it
                }
        }
    }

    // ─────────── Intents (Legacy) ───────────
    // Left for reference; new flows should emit Effect.Navigate instead.
    @Deprecated("Use Effect.Navigate instead")
    private fun openMaps(location: LatLng, address: String) {
        val gmmIntentUri = "geo:${location.latitude},${location.longitude}?q=${Uri.encode(address)}".toUri()
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}".toUri()
            )
            startActivity(browserIntent)
        }
    }

    // ─────────── Lifecycle: Teardown ───────────
    // Clear binding references to avoid leaks.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
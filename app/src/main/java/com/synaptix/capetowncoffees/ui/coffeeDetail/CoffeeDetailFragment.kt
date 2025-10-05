package com.synaptix.capetowncoffees.ui.coffeeDetail

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Effect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collectLoadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.start
import com.synaptix.capetowncoffees.ui.coffeeDetail.adapters.ReviewsAdapter
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CoffeeDetailFragment : Fragment() {
    @Inject lateinit var locationUtil: LocationUtil
    @Inject lateinit var coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase

    @Inject lateinit var reviewsAdapterFactory: ReviewsAdapter.Factory
    private lateinit var reviewsAdapter: ReviewsAdapter

    private val vm: CafeDetailViewModel by viewModels()
    private var _binding: FragmentCoffeeDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoffeeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // SimpleVM lifecycle entry point
        start(vm)

        setupRecyclers()

        setupUiListeners()
        setupCollectors()

        // Ask for user location to enable distance once available
        getCurrentLocation()
    }

    // ───────────────────────── Recycler & Adapter ─────────────────────────

    private fun setupRecyclers() = with(binding) {
        reviewsAdapter = reviewsAdapterFactory.create(
            viewLifecycleOwner.lifecycleScope
        ) { click ->
            when (click) {
                is ReviewsAdapter.Click.Like      -> vm.onReviewLike(click.reviewId)
                is ReviewsAdapter.Click.Dislike   -> vm.onReviewDislike(click.reviewId)
                is ReviewsAdapter.Click.OpenPhoto -> vm.onOpenPhoto(
                    reviewId = click.reviewId,
                    startIndex = click.startIndex,
                    urls = click.urls
                )
            }
        }

        rvReviews.layoutManager = LinearLayoutManager(requireContext())
        rvReviews.adapter = reviewsAdapter
        rvReviews.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL
            )
        )
    }

    // ───────────────────────── UI listeners (platform only) ─────────────────────────

    private fun setupUiListeners() = with(binding) {
        btnBack.setOnClickListener { findNavController().navigateUp() }
        tvDistance.setOnClickListener { getCurrentLocation() } // user can tap to refresh distance
        // Phone/address clicks are delegated to VM via collectors (only when available)
    }

    // ─────────────────────────────── Collectors ───────────────────────────────

    private fun setupCollectors() {
        // One-shot effects: do Android things here (intents/nav)
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message  -> Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
                is Effect.Navigate -> when (eff.route) {
                    // External map intent
                    "action_open_external_map" -> {
                        val args = eff.args ?: return@collect
                        val mapUrl = args.getString("map_url") ?: return@collect
                        val intent = Intent(Intent.ACTION_VIEW, mapUrl.toUri())
                        startActivity(intent)
                    }
                    // Dial intent
                    "action_dial_phone" -> {
                        val phone = eff.args?.getString("phone") ?: return@collect
                        val dial = Intent(Intent.ACTION_DIAL, "tel:${phone.filter { it.isDigit() }}".toUri())
                        startActivity(dial)
                    }
                    // Open a simple photo viewer route (adapt to your NavGraph or show a bottom sheet)
                    "action_open_review_gallery" -> {
                        val urls = eff.args?.getStringArrayList("urls").orEmpty()
                        val start = eff.args?.getInt("start") ?: 0
                        // Example: open external viewer for the tapped photo; replace with your gallery
                        val uri = urls.getOrNull(start)?.toUri() ?: return@collect
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    // Fallback: use NavController if route is a nav graph destination
                    else -> findNavController().navigate(eff.route.toUri(), null)
                }
            }
        }

        // Loadable place: VM owns derivation, Fragment binds image + loading
        collectLoadable(vm.place) { loadable ->
            when (loadable) {
                Loadable.Uninitialized -> showLoading(false)
                Loadable.Loading -> showLoading(true)
                is Loadable.Data -> {
                    showLoading(false)
                    updateImage(loadable.value)
                }
                is Loadable.Error -> {
                    showLoading(false)
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                    Timber.e("Place load error: ${loadable.error.message}")
                }
            }
        }

        // UI-ready state: just bind values (no logic here)
        collect(vm.ui.flow) { ui ->
            binding.tvCafeName.text = ui.name

            // Address text + click decoration
            binding.tvCafeAddress.text = ui.address
            binding.tvCafeAddress.paintFlags = if (ui.addressClickable)
                binding.tvCafeAddress.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            else
                binding.tvCafeAddress.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            binding.tvCafeAddress.isClickable = ui.addressClickable
            binding.tvCafeAddress.setOnClickListener(
                if (ui.addressClickable) View.OnClickListener { vm.onAddressClicked() } else null
            )

            // Opening hours (already formatted)
            binding.tvOpeningHours.text = if (ui.hasOpeningHours)
                ui.openingHoursText
            else
                getString(R.string.no_opening_hours_available)

            // Phone
            binding.tvPhone.isVisible = ui.phoneNumber != null
            binding.tvPhone.text = ui.phoneNumber.orEmpty()
            binding.tvPhone.setOnClickListener(
                if (ui.phoneNumber != null) View.OnClickListener { vm.onPhoneClicked() } else null
            )

            // Rating
            binding.ratingBar.isVisible = ui.showRating
            binding.tvRating.isVisible = ui.showRating
            if (ui.showRating) {
                binding.ratingBar.rating = ui.rating?.toFloat() ?: 0f
                // Prefer count badge if present, else numeric rating
                binding.tvRating.text = ui.ratingCountText ?: ui.rating?.toString() ?: ""
            }

            // Distance (computed in VM when both locations known)
            binding.tvDistance.isVisible = ui.showDistance
            binding.ivPin.isVisible = ui.showDistance
            binding.tvDistance.text = ui.distanceText.orEmpty()
        }

        // Reviews remain a Loadable; render as you like
        collectLoadable(vm.reviews) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> showReviewsSkeleton()
                is Loadable.Data  -> renderReviews(loadable.value)
                is Loadable.Error -> showReviewsError(loadable.error.message) { vm.retryReviews() }
            }
        }
    }

    // ───────────────────────────── Render helpers ─────────────────────────────

    private fun renderReviews(list: List<CoffeeReview>) = with(binding) {
        // Optional empty-state view if you have one
        reviewsEmpty.isVisible = list.isEmpty()
        rvReviews.isVisible = list.isNotEmpty()
        reviewsAdapter.updateItems(list)
    }

    private fun showReviewsSkeleton() = with(binding) {
        // Toggle your shimmer/skeleton views if present
        reviewsEmpty.isVisible = false
        // Optionally show a shimmer container here
    }

    private fun showReviewsError(msg: String, retry: () -> Unit) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        // If you have a dedicated error view with a retry button, wire it here
        // binding.reviewsError.retryButton.setOnClickListener { retry() }
    }

    private fun updateImage(cafe: CoffeePlaceFull) {
        val imageView = binding.ivImage
        val placeholderRes = R.drawable.featured_placeholder
        cafe.images?.firstOrNull()?.let { meta ->
            viewLifecycleOwner.lifecycleScope.launch {
                val uri = coffeePlaceUtilsUseCase.getPhotoUriFromMetadata(meta, maxWidthDp = 1000)
                if (uri != null) {
                    Glide.with(imageView.context)
                        .load(uri)
                        .placeholder(placeholderRes)
                        .into(imageView)
                } else {
                    imageView.setImageResource(placeholderRes)
                }
            }
        } ?: imageView.setImageResource(placeholderRes)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
    }

    // ───────────────────────────── Location plumbing ─────────────────────────────

    private fun getCurrentLocation() {
        // Fetch and let the VM compute distance text
        viewLifecycleOwner.lifecycleScope.launch {
            @Suppress("MissingPermission")
            runCatching { locationUtil.getCurrentLatLng().getOrNull() }
                .onSuccess { location -> location?.let { vm.onUserLocation(it) } }
                .onFailure {
                    Timber.e(it, "Failed to get current location")
                    // VM will hide distance when it can't compute it next tick
                }
        }
    }

    // ───────────────────────────── Intents ─────────────────────────────

    @Deprecated ("Use Effect.Navigate instead")
    private fun openMaps(location: LatLng, address: String) {
        val gmmIntentUri =
            "geo:${location.latitude},${location.longitude}?q=${Uri.encode(address)}".toUri()
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

    // ───────────────────────────── Lifecycle ─────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

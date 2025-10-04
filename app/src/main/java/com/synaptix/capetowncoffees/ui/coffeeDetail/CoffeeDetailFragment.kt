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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentCoffeeDetailBinding
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.ui._simple.Effect
import com.synaptix.capetowncoffees.ui._simple.Loadable
import com.synaptix.capetowncoffees.ui._simple.collect
import com.synaptix.capetowncoffees.ui._simple.collectLoadable
import com.synaptix.capetowncoffees.ui._simple.start
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CoffeeDetailFragment : Fragment() {
    @Inject lateinit var locationUtil: LocationUtil
    @Inject lateinit var coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase
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

        setupUiListeners()
        setupCollectors()

        // Ask for user location to enable distance once available
        getCurrentLocation()
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
                    //TODO: Replace with provided Uri from google in the places model
                    "action_open_external_map" -> {
                        val args = eff.args ?: return@collect
                        val mapUrl = args.getString("map_url") ?: return@collect
                        val intent = Intent(Intent.ACTION_VIEW, mapUrl.toUri())
                        startActivity(intent)
                    }
                    // Dial intent
                    "action_dial_phone" -> {
                        val phone = eff.args?.getString("phone") ?: return@collect
                        val dial = Intent(Intent.ACTION_DIAL,
                            "tel:${phone.filter { it.isDigit() }}".toUri())
                        startActivity(dial)
                    }
                    // Fallback: use NavController if route is a nav graph destination
                    else -> {
                        when (eff.route) {
                            else -> findNavController().navigate(eff.route.toUri(), null)
                        }
                    }
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
            binding.tvPhone.visibility = if (ui.phoneNumber != null) View.VISIBLE else View.GONE
            binding.tvPhone.text = ui.phoneNumber.orEmpty()
            binding.tvPhone.setOnClickListener(
                if (ui.phoneNumber != null) View.OnClickListener { vm.onPhoneClicked() } else null
            )

            // Rating
            binding.ratingBar.visibility = if (ui.showRating) View.VISIBLE else View.GONE
            binding.tvRating.visibility = if (ui.showRating) View.VISIBLE else View.GONE
            if (ui.showRating) {
                binding.ratingBar.rating = ui.rating?.toFloat() ?: 0f
                // Prefer count badge if present, else numeric rating
                binding.tvRating.text = ui.ratingCountText ?: ui.rating?.toString() ?: ""
            }

            // Distance (computed in VM when both locations known)
            binding.tvDistance.visibility = if (ui.showDistance) View.VISIBLE else View.GONE
            binding.ivPin.visibility = if (ui.showDistance) View.VISIBLE else View.GONE
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

    private fun renderReviews(list: List<CoffeeReview>) {
        // TODO: bind to RecyclerView adapter
        // binding.reviewsEmpty.isVisible = list.isEmpty()
        // adapter.submitList(list)
    }

    private fun showReviewsSkeleton() {
        // TODO: shimmer/skeleton
        // binding.reviewsEmpty.isVisible = false
    }

    private fun showReviewsError(msg: String, retry: () -> Unit) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        // binding.reviewsError.retryButton.setOnClickListener { retry() }
    }

    private fun updateImage(cafe: CoffeePlaceFull) {
        val imageView = binding.ivImage
        val placeholderRes = R.drawable.featured_placeholder
        cafe.images?.firstOrNull()?.let { meta ->
            lifecycleScope.launch {
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
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        // If you add SwipeRefreshLayout, also stop it here
        // binding.swipeRefresh.isRefreshing = false
    }

    // ───────────────────────────── Location plumbing ─────────────────────────────

    private fun getCurrentLocation() {
        // Fetch and let the VM compute distance text
        lifecycleScope.launch {
            @Suppress("MissingPermission")
            runCatching { locationUtil.getCurrentLatLng().getOrNull() }
                .onSuccess { location ->
                    location?.let { vm.onUserLocation(it) }
                }
                .onFailure {
                    Timber.e(it, "Failed to get current location")
                    // VM will hide distance when it can't compute it next tick
                }
        }
    }

    // ───────────────────────────── Intents ─────────────────────────────

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
        // Don’t clear VM state; SimpleVM persists across config changes.
    }
}

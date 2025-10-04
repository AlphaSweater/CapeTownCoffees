// File: com/synaptix/capetowncoffees/ui/coffeeDetail/CoffeeDetailFragment.kt
package com.synaptix.capetowncoffees.ui.coffeeDetail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentCoffeeDetailBinding
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.ui._simple.Effect
import com.synaptix.capetowncoffees.ui._simple.Loadable
import com.synaptix.capetowncoffees.ui._simple.collect
import com.synaptix.capetowncoffees.ui._simple.collectLoadable
import com.synaptix.capetowncoffees.ui._simple.start
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class CoffeeDetailFragment : Fragment() {

    @Inject lateinit var placesClient: PlacesClient

    private val vm: CafeDetailViewModel by viewModels()

    private var _binding: FragmentCoffeeDetailBinding? = null
    private val binding get() = _binding!!

    private var currentLocation: LatLng? = null
    private var currentCafe: CoffeePlaceFull? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

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

        // Kick distance once we have user location (then update when place loads)
        getCurrentLocation()
    }

    private fun setupUiListeners() = with(binding) {
        btnBack.setOnClickListener { findNavController().navigateUp() }
        btnHeart.setOnClickListener {
            // Example: you could emit an Effect or call a use case-backed VM method
            // vm.onToggleFavourite()
            Toast.makeText(requireContext(), "Favorites not implemented yet", Toast.LENGTH_SHORT).show()
        }
        tvDistance.setOnClickListener { getCurrentLocation() }
        //TODO: Add swipe to refresh
        // swipeRefresh.setOnRefreshListener { vm.refresh() } // if you have SwipeRefreshLayout
    }

    private fun setupCollectors() {
        // Effects (snackbar/nav)
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message  -> Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
                is Effect.Navigate -> findNavController().navigate(eff.route.toUri())
            }
        }

        // Place (Loadable)
        collectLoadable(vm.place) { loadable ->
            when (loadable) {
                Loadable.Uninitialized -> showLoading(false)
                Loadable.Loading -> showLoading(true)
                is Loadable.Data -> {
                    showLoading(false)
                    updateUI(loadable.value)
                    currentLocation?.let { updateDistance(loadable.value) } ?: getCurrentLocation()
                }
                is Loadable.Error -> {
                    showLoading(false)
                    Timber.e("Error loading cafe details: ${loadable.error.message}")
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Reviews (Loadable) — render as you like (skeleton/error per Loadable)
        collectLoadable(vm.reviews) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> showReviewsSkeleton()
                is Loadable.Data  -> renderReviews(loadable.value)
                is Loadable.Error -> showReviewsError(loadable.error.message) { vm.retryReviews() }
            }
        }

        // Selected location (if you later expose map interactions)
        collect(vm.selectedLocation.flow) { /* react if needed */ }
    }

    private fun renderReviews(list: List<CoffeeReview>) {
        // TODO: bind to RecyclerView
        //binding.reviewsEmpty.isVisible = list.isEmpty()
        // binding.recycler.adapter.submitList(list)
    }

    private fun showReviewsSkeleton() {
        // TODO: show shimmer/skeleton container
        //binding.reviewsEmpty.isVisible = false
    }

    private fun showReviewsError(msg: String, retry: () -> Unit) {
        // TODO: your error UI with retry callback
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        // binding.reviewsError.retryButton.setOnClickListener { retry() }
    }

    private fun updateUI(cafe: CoffeePlaceFull) {
        Timber.d("Updating UI for cafe: ${cafe.name}")
        Timber.d("Phone number fields - national: '${cafe.nationalPhoneNumber}', international: '${cafe.internationalPhoneNumber}'")

        currentCafe = cafe

        binding.apply {
            // Photo via Places SDK if present
            cafe.images?.firstOrNull()?.let { photoMetadata ->
                val req = FetchPhotoRequest.builder(photoMetadata).setMaxWidth(1000).build()
                placesClient.fetchPhoto(req)
                    .addOnSuccessListener { resp -> ivImage.setImageBitmap(resp.bitmap) }
                    .addOnFailureListener { ex ->
                        Timber.e("Error loading image: ${ex.message}")
                        ivImage.setImageResource(R.drawable.featured_placeholder)
                    }
            } ?: ivImage.setImageResource(R.drawable.featured_placeholder)

            // Name
            tvCafeName.text = cafe.name.orEmpty()

            // Address + open map
            val addressText = cafe.address.orEmpty()
            tvCafeAddress.text = addressText
            if (cafe.location != null) {
                tvCafeAddress.paintFlags = tvCafeAddress.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                tvCafeAddress.setOnClickListener { openMaps(cafe.location, addressText) }
            } else {
                tvCafeAddress.paintFlags = tvCafeAddress.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                tvCafeAddress.setOnClickListener(null)
            }

            // Opening hours (grouped)
            cafe.currentOpeningHours?.let { hours ->
                tvOpeningHours.text = if (hours.isNotEmpty()) formatOpeningHours(hours)
                else getString(R.string.no_opening_hours_available)
            } ?: run {
                tvOpeningHours.text = getString(R.string.no_opening_hours_available)
            }

            // Phone
            val phoneNumber = cafe.nationalPhoneNumber ?: cafe.internationalPhoneNumber
            if (!phoneNumber.isNullOrBlank()) {
                tvPhone.visibility = View.VISIBLE
                tvPhone.text = phoneNumber
                tvPhone.setOnClickListener {
                    val dial = Intent(Intent.ACTION_DIAL).apply {
                        data = "tel:${phoneNumber.filter { it.isDigit() }}".toUri()
                    }
                    startActivity(dial)
                }
            } else {
                tvPhone.visibility = View.GONE
            }

            // Rating + Distance
            val rating = cafe.rating
            val ratingCount = cafe.ratingCount
            if (rating != null) {
                tvRating.text = String.format("%.1f", rating)
                ratingBar.rating = rating.toFloat()
                ratingBar.visibility = View.VISIBLE
                tvRating.visibility = View.VISIBLE

                currentLocation?.let { user ->
                    cafe.location?.let { cafeLoc ->
                        tvDistance.text = LocationUtil.getFormattedDistance(user, cafeLoc)
                        tvDistance.visibility = View.VISIBLE
                        ivPin.visibility = View.VISIBLE
                    } ?: hideDistance()
                } ?: hideDistance()
            } else {
                ratingBar.visibility = View.GONE
                tvRating.visibility = View.GONE
                hideDistance()
            }

            // If you prefer ratingCount as badge:
            ratingCount?.let {
                tvRating.text = "($it)"
                tvRating.visibility = View.VISIBLE
            }
        }
    }

    private fun hideDistance() {
        binding.tvDistance.visibility = View.GONE
        binding.ivPin.visibility = View.GONE
    }

    private fun getCurrentLocation() {
        Timber.d("getCurrentLocation called")

        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted || !coarseGranted) {
            Timber.d("Location permissions not granted, requesting…")
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        Timber.d("Location permissions granted, fetching location…")
        lifecycleScope.launch {
            runCatching {
                currentLocation = LocationUtil.getCurrentLocation(requireContext())
            }.onSuccess {
                Timber.d("Current location set to: $currentLocation")
                (vm.place.value as? Loadable.Data)?.value?.let { cafe ->
                    updateDistance(cafe)
                }
            }.onFailure { e ->
                Timber.e(e, "Error getting location")
                hideDistance()
            }
        }
    }

    private fun updateDistance(cafe: CoffeePlaceFull) {
        val cafeLocation = cafe.location
        val userLocation = currentLocation
        if (cafeLocation == null || userLocation == null) {
            hideDistance(); return
        }
        val distanceText = LocationUtil.getFormattedDistance(userLocation, cafeLocation)
        view?.post {
            binding.tvDistance.text = distanceText
            binding.tvDistance.visibility = View.VISIBLE
            binding.ivPin.visibility = View.VISIBLE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.size >= 2 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED
            if (granted) getCurrentLocation() else hideDistance()
        }
    }

    private fun formatOpeningHours(hours: List<String>): String {
        if (hours.isEmpty()) return getString(R.string.no_opening_hours_available)
        val dayAbbrev = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val hoursByDay = hours.mapIndexed { index, full ->
            val timePart = full.substringAfter(": ", full)
            dayAbbrev[index] to timePart
        }
        val result = mutableListOf<String>()
        var currentRange = mutableListOf<String>()
        var currentHours = ""
        for ((day, hour) in hoursByDay) {
            if (currentHours != hour) {
                if (currentRange.isNotEmpty()) {
                    result.add(formatDayRange(currentRange, currentHours))
                    currentRange.clear()
                }
                currentHours = hour
            }
            currentRange.add(day)
        }
        if (currentRange.isNotEmpty()) result.add(formatDayRange(currentRange, currentHours))
        return result.joinToString("\n")
    }

    private fun formatDayRange(days: List<String>, hours: String): String = when (days.size) {
        1 -> "${days[0]}: $hours"
        2 -> "${days[0]} & ${days[1]}: $hours"
        else -> "${days.first()} - ${days.last()}: $hours"
    }

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

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        //binding.swipeRefresh.isRefreshing = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // ✅ Do NOT wipe VM state here; let SimpleVM persist across config changes
    }
}

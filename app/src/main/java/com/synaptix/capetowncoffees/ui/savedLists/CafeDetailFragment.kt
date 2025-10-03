package com.synaptix.capetowncoffees.ui.savedLists

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.databinding.FragmentCafeDetailBinding
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class CafeDetailFragment : Fragment() {

    @Inject
    lateinit var placesClient: PlacesClient

    private val viewModel: CafeDetailViewModel by activityViewModels()
    private var _binding: FragmentCafeDetailBinding? = null
    private val binding get() = _binding!!
    
    // Navigation arguments
    private val args: CafeDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCafeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up UI listeners
        setupUiListeners()
        
        // Observe ViewModel state
        observeViewModel()
        
        // Load coffee place details
        loadCoffeePlace()
    }
    
    private fun setupUiListeners() {
        binding.apply {
            // Set up back button
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }
            
            // Set up favorite button
            btnHeart.setOnClickListener {
                // TODO: Implement favorite functionality
            }
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is CafeDetailUiState.Loading -> {
                        showLoading(true)

                    }
                    is CafeDetailUiState.Success -> {
                        showLoading(false)
                        updateUI(state.coffeePlace)
                    }
                    is CafeDetailUiState.Error -> {
                        showLoading(false)

                    }
                }
            }
        }
        
    }
    
    private fun loadCoffeePlace() {
        // Get the coffee place from navigation arguments
        viewModel.loadCoffeePlace(placeId = args.placeId.ifEmpty { null })
    }

    private fun updateUI(cafe: CoffeePlaceFull) {
        Log.d("CafeDetailFragment", "Updating UI for cafe: ${cafe.name}")
        Log.d("CafeDetailFragment", "Phone number fields - national: '${cafe.nationalPhoneNumber}', international: '${cafe.internationalPhoneNumber}'")
        
        binding.apply {
            // Load cafe image if available
            cafe.images?.firstOrNull()?.let { photoMetadata ->
                val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                    .setMaxWidth(1000) // Adjust based on your needs
                    .build()
                    
                placesClient.fetchPhoto(photoRequest)
                    .addOnSuccessListener { fetchPhotoResponse ->
                        val bitmap = fetchPhotoResponse.bitmap
                        ivImage.setImageBitmap(bitmap)
                    }
                    .addOnFailureListener { exception ->
                        Log.e("CafeDetailFragment", "Error loading image: ${exception.message}")
                        // Set a default image if loading fails
                        ivImage.setImageResource(R.drawable.featured_placeholder)
                    }
            } ?: run {
                // No images available, set default image
                ivImage.setImageResource(R.drawable.featured_placeholder)
            }
            
            // Cafe Name
            tvCafeName.text = cafe.name ?: ""

            // Address
            val addressText = cafe.address ?: ""
            tvCafeAddress.text = addressText
            
            // Set up opening hours with grouped days
            cafe.currentOpeningHours?.let { hours ->
                if (hours.isNotEmpty()) {
                    val formattedHours = formatOpeningHours(hours)
                    tvOpeningHours.text = formattedHours
                } else {
                    tvOpeningHours.text = getString(R.string.no_opening_hours_available)
                }
            } ?: run {
                tvOpeningHours.text = getString(R.string.no_opening_hours_available)
            }
            
            // Set up click listener for address to open maps if location is available
            if (cafe.location != null) {
                tvCafeAddress.paintFlags = tvCafeAddress.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                tvCafeAddress.setOnClickListener {
                    openMaps(cafe.location, addressText)
                }
            } else {
                tvCafeAddress.paintFlags = tvCafeAddress.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
                tvCafeAddress.setOnClickListener(null)
            }
            
            // Phone Number
            val phoneNumber = cafe.nationalPhoneNumber ?: cafe.internationalPhoneNumber
            Log.d("CafeDetailFragment", "Using phone number: '$phoneNumber'")
            
            if (!phoneNumber.isNullOrEmpty()) {
                // Show phone number in the dedicated phone TextView
                tvPhone.text = phoneNumber
                tvPhone.visibility = View.VISIBLE
                
                // Make phone number clickable
                tvPhone.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${phoneNumber.filter { it.isDigit() }}")
                    }
                    startActivity(intent)
                }
            } else {
                // Hide phone number view if no number is available
                tvPhone.visibility = View.GONE
            }

            // Rating
            cafe.rating?.let { rating ->
                tvRating.text = String.format("%.1f", rating)
                ratingBar.rating = rating.toFloat()
                ratingBar.visibility = View.VISIBLE
                tvRating.visibility = View.VISIBLE
            } ?: run {
                ratingBar.visibility = View.GONE
                tvRating.visibility = View.GONE
            }
            
            // Show rating count if available
            cafe.ratingCount?.let { count ->
                tvRating.text = "($count)"
                tvRating.visibility = View.VISIBLE
            } ?: run {
                tvRating.visibility = View.GONE
            }
        }
    }
    
    private fun formatOpeningHours(hours: List<String>): String {
        if (hours.isEmpty()) return getString(R.string.no_opening_hours_available)
        
        val dayAbbreviations = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        
        // First, extract just the time part from each day's hours
        val hoursByDay = hours.mapIndexed { index, fullDayHours ->
            // Extract just the time part (e.g., "9:00 AM - 6:00 PM" from "Monday: 9:00 AM - 6:00 PM")
            val timePart = fullDayHours.substringAfter(": ", fullDayHours)
            dayAbbreviations[index] to timePart
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
        
        // Add the last range
        if (currentRange.isNotEmpty()) {
            result.add(formatDayRange(currentRange, currentHours))
        }
        
        return result.joinToString("\n")
    }
    
    private fun formatDayRange(days: List<String>, hours: String): String {
        return when (days.size) {
            1 -> "${days[0]}: $hours"
            2 -> "${days[0]} & ${days[1]}: $hours"
            else -> "${days.first()} - ${days.last()}: $hours"
        }
    }
    
    private fun openMaps(location: LatLng, address: String) {
        val gmmIntentUri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${Uri.encode(address)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        
        if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Fallback to browser if Google Maps is not installed
            val browserIntent = Intent(
                Intent.ACTION_VIEW, 
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}")
            )
            startActivity(browserIntent)
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.clearSelectedCafe()
    }
}
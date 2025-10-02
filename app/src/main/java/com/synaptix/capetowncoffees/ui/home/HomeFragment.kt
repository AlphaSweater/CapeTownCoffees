package com.synaptix.capetowncoffees.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository.CoffeeSearchParams
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val categories = listOf(
        Category("Top Rated", R.drawable.ic_medal),
        Category("Near Me", R.drawable.ic_pin),
        Category("Dog Friendly", R.drawable.baseline_pets_24),
        Category("Favorite", R.drawable.ic_heart)
    )

    private val nearMeCafes = mutableListOf<Cafe>()
    private val featuredItems = mutableListOf<FeaturedItem>()

    @Inject
    lateinit var placesApiRepository: IPlacesApiRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupClickListeners()

        // Fetch nearby coffee places
        fetchNearbyCoffeePlaces()
    }

    private fun setupRecyclerViews() {
        // Categories RecyclerView
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = CategoryAdapter(categories)
            setHasFixedSize(true)
        }

        // Near Me RecyclerView
        val nearMeAdapter = NearMeAdapter(nearMeCafes)
        nearMeAdapter.setOnItemClickListener { cafe ->
            navigateToCafeDetails(cafe.name, cafe.rating, cafe.distance, cafe.priceRange)
        }
        binding.rvNearMe.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = nearMeAdapter
            setHasFixedSize(true)
        }

        // Featured RecyclerView
        val featuredAdapter = FeaturedAdapter(featuredItems)
        featuredAdapter.setOnItemClickListener { featuredItem ->
            // Convert FeaturedItem to Cafe with default values for missing fields
            val cafe = Cafe(
                name = featuredItem.title,
                rating = featuredItem.rating,
                distance = featuredItem.distance.toDoubleOrNull() ?: 0.0,
                priceRange = "$$" // Default price range for featured items
            )
            navigateToCafeDetails(cafe.name, cafe.rating, cafe.distance, cafe.priceRange)
        }
        binding.rvFeatured.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = featuredAdapter
            setHasFixedSize(true)
        }
    }

    private fun navigateToCafeDetails(
        name: String,
        rating: Double,
        distance: Double,
        priceRange: String
    ) {
        // Create a bundle with cafe details
        val bundle = Bundle().apply {
            putString("cafeName", name)
            putFloat("cafeRating", rating.toFloat())
            putFloat("cafeDistance", distance.toFloat())
            putString("cafePriceRange", priceRange)
        }

        // Navigate to CafeDetailFragment using the action ID from the navigation graph
        findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment, bundle)
    }

    private fun setupClickListeners() {
        // Search bar click listener
        binding.searchBar.setOnClickListener {
            // TODO: Implement search functionality
            showMessage("Search clicked")
        }

        binding.tvSeeAllNearMe.setOnClickListener {
            // TODO: Navigate to all nearby cafes
            showMessage("See all nearby cafes")
        }

        binding.tvSeeAllFeatured.setOnClickListener {
            // TODO: Navigate to all featured items
            showMessage("See all featured items")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun fetchNearbyCoffeePlaces() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userLatLng = LocationUtil.getCurrentLocation(requireContext())
                if (userLatLng == null) {
                    Timber.e("User location unavailable")
                    showMessage("Could not get your location. Please enable location services and try again.")
                    return@launch
                }
                
                showLoading(true)
                
                val params = CoffeeSearchParams(
                    radiusMeters = 2000,
                    onlyOpenNow = true
                )
                
                when (val result = placesApiRepository.searchNearbyCoffeePlaces(params, userLatLng)) {
                    is Result.Success -> {
                        val places = result.getOrNull()
                        if (places.isNullOrEmpty()) {
                            showMessage("No nearby coffee places found.")
                        } else {
                            // Clear existing data
                            nearMeCafes.clear()
                            featuredItems.clear()
                            
                            // Process each place
                            places.forEach { place ->
                                val cafe = place.toCafe(userLatLng)
                                nearMeCafes.add(cafe)
                                
                                // Add to featured if rating is high enough
                                if (cafe.rating >= 4.5) {
                                    featuredItems.add(
                                        FeaturedItem(
                                            id = cafe.id,
                                            title = cafe.name,
                                            distance = String.format("%.1f km", cafe.distance),
                                            rating = cafe.rating,
                                            photoUrl = cafe.photoUrl,
                                            address = cafe.address
                                        )
                                    )
                                }
                            }
                            
                            // Sort by distance
                            nearMeCafes.sortBy { it.distance }
                            
                            // Update UI
                            binding.apply {
                                rvNearMe.adapter?.notifyDataSetChanged()
                                rvFeatured.adapter?.notifyDataSetChanged()
                                
                                // Show/hide sections based on data
                                if (nearMeCafes.isNotEmpty()) {
                                    tvNearbyCafesTitle.visibility = View.VISIBLE
                                    rvNearMe.visibility = View.VISIBLE
                                }
                                
                                if (featuredItems.isNotEmpty()) {
                                    tvFeaturedTitle.visibility = View.VISIBLE
                                    rvFeatured.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                    is Result.Error -> {
                        Timber.e(result.exceptionOrNull(), "Failed to fetch nearby coffee places")
                        showMessage("Could not get nearby coffee places. Please try again.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching coffee places")
                showMessage("Error fetching coffee places: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Data classes
data class Category(val name: String, val iconRes: Int)
data class Cafe(
    val id: String? = null,
    val name: String,
    val rating: Double = 0.0,
    val distance: Double = 0.0,
    val priceRange: String = "$$",
    val address: String? = null,
    val photoUrl: String? = null,
    val ratingCount: Int = 0,
    val businessStatus: String? = null,
    val isOpen: Boolean = false
)

// Extension function to convert CoffeePlaceLite to Cafe
private fun CoffeePlaceLite.toCafe(userLocation: LatLng? = null): Cafe {
    // Calculate distance if user location is available
    val distanceMeters = if (userLocation != null && this.location != null) {
        val results = FloatArray(1)
        Location.distanceBetween(
            userLocation.latitude,
            userLocation.longitude,
            this.location.latitude,
            this.location.longitude,
            results
        )
        results[0].toDouble()
    } else {
        0.0
    }
    
    // Get the first photo URL if available
    val photoUrl = this.images?.firstOrNull()?.let { photo ->
        // You'll need to implement a method to get the photo URL from PhotoMetadata
        // This is a placeholder - you'll need to use the Places API client to fetch the actual photo
        null
    }
    
    return Cafe(
        id = this.id,
        name = this.name ?: "Unknown Cafe",
        rating = this.rating ?: 0.0,
        distance = distanceMeters / 1000.0, // Convert to kilometers
        priceRange = when (this.priceLevel) {
            1 -> "$"
            2 -> "$$"
            3 -> "$$$"
            4 -> "$$$$"
            else -> "$"
        },
        address = this.address,
        photoUrl = photoUrl,
        ratingCount = this.ratingCount ?: 0,
        businessStatus = this.businessStatus,
        isOpen = this.currentOpeningHours?.isNotEmpty() == true
    )
}
data class FeaturedItem(
    val id: String? = null,
    val title: String,
    val distance: String,
    val rating: Double,
    val photoUrl: String? = null,
    val address: String? = null
)

// Adapters
class CategoryAdapter(private val categories: List<Category>) :
    RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chip: MaterialButton = view.findViewById(R.id.btnCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        // Ensure each chip wraps content and has proper height/margin in horizontal list
        val lp = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val marginEndPx = (parent.context.resources.displayMetrics.density * 10).toInt()
        lp.marginEnd = marginEndPx
        v.layoutParams = lp
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = categories[position]
        holder.chip.text = item.name
        holder.chip.setIconResource(item.iconRes)
    }

    override fun getItemCount() = categories.size
}

class NearMeAdapter(private val cafes: List<Cafe>) :
    RecyclerView.Adapter<NearMeAdapter.ViewHolder>() {

    private var onItemClickListener: ((Cafe) -> Unit)? = null

    fun setOnItemClickListener(listener: (Cafe) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_near_me, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cafe = cafes[position]
        
        // Set basic info
        holder.tvCafeName.text = cafe.name
        holder.tvCafeRating.text = String.format("%.1f", cafe.rating)
        holder.tvCafeDistance.text = "${String.format("%.1f", cafe.distance)} km"
        holder.tvCafePrice.text = cafe.priceRange
        
        // Set address if available
        cafe.address?.let { address ->
            holder.tvCafeAddress.text = address
            holder.tvCafeAddress.visibility = View.VISIBLE
        } ?: run {
            holder.tvCafeAddress.visibility = View.GONE
        }
        
        // Load image if URL is available
        cafe.photoUrl?.let { imageUrl ->
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .centerCrop()
                .placeholder(R.drawable.cafe_placeholder)
                .into(holder.ivCafeImage)
        } ?: holder.ivCafeImage.setImageResource(R.drawable.cafe_placeholder)

        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(cafe)
        }
    }

    override fun getItemCount() = cafes.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCafeName: TextView = view.findViewById(R.id.tvCafeName)
        val tvCafeRating: TextView = view.findViewById(R.id.tvCafeRating)
        val tvCafeDistance: TextView = view.findViewById(R.id.tvCafeDistance)
        val tvCafePrice: TextView = view.findViewById(R.id.tvCafePrice)
        val tvCafeAddress: TextView = view.findViewById(R.id.tvCafeAddress)
        val ivCafeImage: ImageView = view.findViewById(R.id.ivCafeImage)
    }
}

class FeaturedAdapter(private val items: List<FeaturedItem>) :
    RecyclerView.Adapter<FeaturedAdapter.ViewHolder>() {

    private var onItemClickListener: ((FeaturedItem) -> Unit)? = null

    fun setOnItemClickListener(listener: (FeaturedItem) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // Set basic info
        holder.tvTitle.text = item.title
        holder.tvDistance.text = "${item.distance} km away"
        holder.tvRating.text = String.format("%.1f", item.rating)
        
        // Load image if URL is available
        item.photoUrl?.let { imageUrl ->
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .centerCrop()
                .placeholder(R.drawable.cafe_placeholder)
                .into(holder.ivImage)
        } ?: holder.ivImage.setImageResource(R.drawable.cafe_placeholder)

        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(item)
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvFeaturedTitle)
        val tvDistance: TextView = view.findViewById(R.id.tvFeaturedDistance)
        val tvRating: TextView = view.findViewById(R.id.tvFeaturedRating)
        val ivImage: ImageView = view.findViewById(R.id.ivFeaturedImage)
    }
}

package com.synaptix.capetowncoffees.ui.home

import android.Manifest
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
import com.google.android.material.button.MaterialButton
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
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

    private val nearMeCafes = listOf(
        Cafe("Cape Town Roasters", 4.5, 0.5, "$$$"),
        Cafe("Beans & Leaves", 4.7, 1.2, "$$"),
        Cafe("The Daily Grind", 4.3, 0.8, "$$")
    )

    private val featuredItems = listOf(
        FeaturedItem("Cape Town Roasters", "0.5", 4.6)
    )

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
                val params = CoffeeSearchParams(
                    radiusMeters = 2000,
                    onlyOpenNow = false
                )
                val result = placesApiRepository.searchNearbyCoffeePlaces(params, userLatLng)
                if (result.isSuccess) {
                    val places = result.getOrNull()
                    Timber.d("Nearby coffee places:")
                    if (places.isNullOrEmpty()) {
                        showMessage("No nearby coffee places found.")
                    } else {
                        places.forEach { Timber.d("Place: ${it.name}, ${it.address}") }
                    }
                } else {
                    Timber.e(result.exceptionOrNull(), "Failed to fetch nearby coffee places")
                    showMessage("Could not get nearby coffee places. Please try again.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching location")
                showMessage("Could not get your location. Please enable location services and try again.")
            }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Data classes
data class Category(val name: String, val iconRes: Int)
data class Cafe(val name: String, val rating: Double, val distance: Double, val priceRange: String)
data class FeaturedItem(val title: String, val distance: String, val rating: Double)

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

    private var onItemClick: ((Cafe) -> Unit)? = null

    fun setOnItemClickListener(listener: (Cafe) -> Unit) {
        onItemClick = listener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvCafeName)
        val distance: TextView = view.findViewById(R.id.tvCafeDistance)
        val rating: TextView = view.findViewById(R.id.tvCafeRating)
        val image: ImageView = view.findViewById(R.id.ivCafeImage)
        val rootView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_near_me, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = cafes[position]
        holder.name.text = item.name
        holder.distance.text = "${item.distance} km away"
        holder.rating.text = item.rating.toString()
        // ImageView already has a placeholder in layout
        holder.image.setImageResource(R.drawable.cafe_placeholder)

        holder.rootView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount() = cafes.size
}

class FeaturedAdapter(private val items: List<FeaturedItem>) :
    RecyclerView.Adapter<FeaturedAdapter.ViewHolder>() {

    private var onItemClick: ((FeaturedItem) -> Unit)? = null

    fun setOnItemClickListener(listener: (FeaturedItem) -> Unit) {
        onItemClick = listener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvCafeName)
        val distance: TextView = view.findViewById(R.id.tvCafeDistance)
        val rating: TextView = view.findViewById(R.id.tvCafeRating)
        val image: ImageView = view.findViewById(R.id.ivFeaturedImage)
        val rootView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.distance.text = "${item.distance} km away"
        holder.rating.text = item.rating.toString()
        // ImageView already has a placeholder in layout
        holder.image.setImageResource(R.drawable.cafe_placeholder)

        holder.rootView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount() = items.size
}

package com.example.capetowncoffees.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capetowncoffees.R
import com.example.capetowncoffees.databinding.FragmentHomeNewBinding
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

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
        FeaturedItem("Cape Town Roasters", "0.5", 4.6),
        FeaturedItem("Beans & Leaves", "1.2", 4.8),
        FeaturedItem("The Daily Grind", "0.8", 4.4)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerViews()
        setupClickListeners()
    }
    
    private fun setupRecyclerViews() {
        // Categories RecyclerView
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = CategoryAdapter(categories)
            setHasFixedSize(true)
        }

        // Near Me RecyclerView
        binding.rvNearMe.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = NearMeAdapter(nearMeCafes)
            setHasFixedSize(true)
        }

        // Featured RecyclerView
        binding.rvFeatured.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = FeaturedAdapter(featuredItems)
            setHasFixedSize(true)
        }
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
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvCafeName)
        val distance: TextView = view.findViewById(R.id.tvCafeDistance)
        val rating: TextView = view.findViewById(R.id.tvCafeRating)
        val image: ImageView = view.findViewById(R.id.ivCafeImage)
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
    }

    override fun getItemCount() = cafes.size
}

class FeaturedAdapter(private val items: List<FeaturedItem>) : 
    RecyclerView.Adapter<FeaturedAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvCafeName)
        val distance: TextView = view.findViewById(R.id.tvCafeDistance)
        val rating: TextView = view.findViewById(R.id.tvCafeRating)
        val image: ImageView = view.findViewById(R.id.ivFeaturedImage)
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
    }

    override fun getItemCount() = items.size
}

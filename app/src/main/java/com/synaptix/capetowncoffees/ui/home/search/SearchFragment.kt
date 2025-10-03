package com.synaptix.capetowncoffees.ui.home.search

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeSearchBinding
import com.synaptix.capetowncoffees.ui.home.adapter.FilterCategory
import com.synaptix.capetowncoffees.ui.home.adapter.SearchCategoryAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentHomeSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: SearchCategoryAdapter
    private var isMasterFilterExpanded = false

    private var radiusOptions = (1..50).map { "$it km" }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.masterFilterButton.setOnClickListener {
            isMasterFilterExpanded = !isMasterFilterExpanded
            updateMasterFilterView()
        }

        val filterCategories = listOf(
            FilterCategory("Search Radius", radiusOptions),
            FilterCategory("Speciality Filters", listOf("Cat Cafes", "Dessert Focused", "Pet Friendly", "Study Spots", "Cozy Vibes", "Artisan Roasters", "Vegan Options", "Local Chains")),
            FilterCategory("Quick Filters", listOf("Open Now", "Top Rated", "Wi-Fi", "Pet Friendly", "Outdoor Seating", "Drive-Thru", "Cashless Payment")),
            FilterCategory("Amenities", listOf("Charging Ports", "Wheelchair Accessible", "Parking Available", "Restrooms")),
            FilterCategory("Ambience", listOf("Quiet", "Lively", "Romantic", "Family Friendly"))
        )

        // The onSizeChanged callback is no longer needed for this logic.
        categoryAdapter = SearchCategoryAdapter(filterCategories) {
            // This now only handles sub-category changes after the initial open.
            binding.filtersContainer.slideDown(true)
        }


        binding.recyclerCategories.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun updateMasterFilterView() {
        if (isMasterFilterExpanded) {
            // This is the key change: Make the container visible with zero height first.
            // Then, in the next frame (doOnPreDraw), calculate the real height and animate.
            // This ensures all sub-categories are measured correctly on the initial open.
            val view = binding.filtersContainer
            view.visibility = View.VISIBLE
            view.updateLayoutParams<ViewGroup.LayoutParams> { height = 0 }
            view.doOnPreDraw {
                view.slideDown()
            }
            binding.masterFilterArrow.setImageResource(R.drawable.ic_arrow_down)
        } else {
            binding.filtersContainer.slideUp()
            binding.masterFilterArrow.setImageResource(R.drawable.ic_arrow_forward)
        }
    }

    private fun View.slideDown(forceAnimate: Boolean = false) {
        val view = this

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val targetHeight = view.measuredHeight
        val startHeight = if (forceAnimate) view.height else 0

        if (view.visibility == View.VISIBLE && startHeight == targetHeight && !forceAnimate) {
            return
        }

        view.visibility = View.VISIBLE
        val animator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            addUpdateListener {
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
            }
            duration = 300
        }
        animator.start()
    }

    private fun View.slideUp() {
        val view = this
        val startHeight = view.height
        if (startHeight == 0) return

        val animator = ValueAnimator.ofInt(startHeight, 0).apply {
            addUpdateListener {
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
                if (it.animatedValue as Int == 0) {
                    view.visibility = View.GONE
                }
            }
            duration = 300
        }
        animator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

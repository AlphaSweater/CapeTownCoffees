package com.synaptix.capetowncoffees.ui.home.search

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            FilterCategory("Sort By", listOf("Top Rated", "Most Reviewed", "Distance")),
            FilterCategory("Price", listOf("$", "$$", "$$$", "$$$$")),
            FilterCategory("Features", listOf("Serves Alcohol", "Accepts Reservations", "Dog Friendly"))
        )

        // --- THIS IS THE FIX: Pass the callback to the adapter's constructor ---
        categoryAdapter = SearchCategoryAdapter(filterCategories) {
            // This code runs every time a sub-category is expanded/collapsed.
            // Re-run the slideDown animation to adjust the parent's height.
            if (isMasterFilterExpanded) {
                binding.filtersContainer.slideDown(forceAnimate = true)
            }
        }
        // --- END OF FIX ---

        binding.recyclerCategories.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
        }
    }

    private fun updateMasterFilterView() {
        if (isMasterFilterExpanded) {
            binding.filtersContainer.slideDown()
            binding.masterFilterArrow.setImageResource(R.drawable.ic_arrow_down)
        } else {
            binding.filtersContainer.slideUp()
            binding.masterFilterArrow.setImageResource(R.drawable.ic_arrow_forward)
        }
    }

    // --- THIS IS THE FIX: Add a 'forceAnimate' parameter to slideDown ---
    private fun View.slideDown(forceAnimate: Boolean = false) {
        val view = this
        // If not forcing, and it's already visible, do nothing.
        if (!forceAnimate && view.visibility == View.VISIBLE) return

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val targetHeight = view.measuredHeight

        // Animate from current height to target height
        val startHeight = if (forceAnimate) view.height else 0

        val animator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            addUpdateListener {
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
            }
            duration = 300
        }

        if (!forceAnimate) {
            view.updateLayoutParams<ViewGroup.LayoutParams> { height = 0 }
        }
        view.visibility = View.VISIBLE
        animator.start()
    }
    // --- END OF FIX ---

    // slideUp function remains the same
    private fun View.slideUp() {
        val view = this
        val startHeight = view.height
        if (startHeight == 0) return // Already collapsed

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

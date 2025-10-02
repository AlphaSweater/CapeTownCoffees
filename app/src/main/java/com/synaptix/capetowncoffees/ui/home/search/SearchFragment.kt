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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
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

        categoryAdapter = SearchCategoryAdapter(filterCategories)

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

    private fun View.slideDown() {
        val view = this
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val targetHeight = view.measuredHeight

        val animator = ValueAnimator.ofInt(0, targetHeight).apply {
            addUpdateListener {
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
            }
            duration = 300
        }

        view.updateLayoutParams<ViewGroup.LayoutParams> { height = 0 }
        view.visibility = View.VISIBLE
        animator.start()
    }

    private fun View.slideUp() {
        val view = this
        val startHeight = view.height

        val animator = ValueAnimator.ofInt(startHeight, 0).apply {
            addUpdateListener {
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
                if (it.animatedValue as Int == 0) {
                    view.visibility = View.GONE
                }
            }
            duration = 300 // Animation duration in milliseconds
        }
        animator.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.synaptix.capetowncoffees.ui.savedLists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synaptix.capetowncoffees.R

class CafeDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_cafe_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get cafe details from arguments
        val cafeName = arguments?.getString("cafeName") ?: "Cafe"
        val cafeRating = arguments?.getFloat("cafeRating") ?: 0.0f
        val cafeDistance = arguments?.getFloat("cafeDistance") ?: 0.0f

        // Update UI with cafe details
        view.findViewById<TextView>(R.id.tvCafeName)?.text = cafeName
        view.findViewById<TextView>(R.id.tvRating)?.text = String.format("%.1f", cafeRating)
        view.findViewById<TextView>(R.id.tvDistance)?.text =
            String.format("%.1f km away", cafeDistance)

        // Set up click listeners
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnHeart)?.setOnClickListener {
            // TODO: Implement favorite functionality
        }

        view.findViewById<View>(R.id.btnCall)?.setOnClickListener {
            // TODO: Implement call functionality
        }

        view.findViewById<View>(R.id.btnReview)?.setOnClickListener {
            findNavController().navigate(R.id.reviewStep1Fragment)
        }
    }
}

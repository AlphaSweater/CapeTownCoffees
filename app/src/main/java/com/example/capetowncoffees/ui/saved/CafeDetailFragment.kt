package com.example.capetowncoffees.ui.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.capetowncoffees.R
import androidx.navigation.fragment.findNavController

class CafeDetailFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_cafe_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnBack).setOnClickListener { findNavController().navigateUp() }
        view.findViewById<View>(R.id.btnHeart).setOnClickListener { /* UI only */ }
        view.findViewById<View>(R.id.btnCall).setOnClickListener { /* UI only */ }
        view.findViewById<View>(R.id.btnReview).setOnClickListener {
            findNavController().navigate(R.id.reviewStep1Fragment)
        }
    }
}

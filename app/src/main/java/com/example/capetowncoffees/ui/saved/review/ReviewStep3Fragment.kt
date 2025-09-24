package com.example.capetowncoffees.ui.saved.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.capetowncoffees.R

class ReviewStep3Fragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_review_step3, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnClose).setOnClickListener { findNavController().navigateUp() }
        view.findViewById<View>(R.id.btnNext).setOnClickListener { findNavController().navigate(R.id.reviewCompleteFragment) }
    }
}

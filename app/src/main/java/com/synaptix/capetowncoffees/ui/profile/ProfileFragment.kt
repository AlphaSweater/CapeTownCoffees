package com.synaptix.capetowncoffees.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R

class ProfileFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvReviews)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val demo = listOf(
            ReviewItem(
                "Truth Coffee",
                "2 days ago",
                4.5f,
                "This place is the best!! So many different options",
                1
            ),
            ReviewItem(
                "Origin Coffee",
                "1 week ago",
                4.0f,
                "Great ambiance and solid espresso.",
                3
            ),
            ReviewItem(
                "Deluxe Coffeeworks",
                "3 weeks ago",
                5.0f,
                "My favorite flat white in town!",
                5
            )
        )

        rv.adapter = ReviewAdapter(demo)

        // Settings button navigation
        view.findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        // Edit profile button navigation
        view.findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }
}

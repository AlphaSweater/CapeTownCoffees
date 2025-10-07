package com.synaptix.capetowncoffees.ui.lists.favouriteList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R

class FavouritesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_favourites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView with static sample data
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerFavourites)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val items = listOf(
            FavouriteItem(
                name = "Platō Coffee",
                location = "Cape Town, Rondebosch",
                ratingText = "4,6 (96)"
            ),
            FavouriteItem(
                name = "Ground Culture Cafe",
                location = "Cape Town, Rondebosch",
                ratingText = "4,8 (301)"
            ),
            FavouriteItem(
                name = "Truth Coffee Roasting",
                location = "CBD, Buitenkant St",
                ratingText = "4,7 (1.2k)"
            )
        )
        recycler.adapter = FavouriteAdapter(items) {
            // UI-only navigation to detail screen
            findNavController().navigate(R.id.cafeDetailFragment)
        }

        // Back button
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Sort button is non-functional for now
        view.findViewById<View>(R.id.btnSort).setOnClickListener { /* no-op */ }
    }
}

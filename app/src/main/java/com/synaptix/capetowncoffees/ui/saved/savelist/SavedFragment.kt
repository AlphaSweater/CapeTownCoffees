package com.synaptix.capetowncoffees.ui.saved.savelist

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synaptix.capetowncoffees.R

class SavedFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved, container, false)

        // Create new list navigation
        view.findViewById<View>(R.id.rowCreateList).setOnClickListener {
            findNavController().navigate(R.id.createListFragment)
        }

        // Navigate to Favourites
        view.findViewById<View>(R.id.rowFavourites).setOnClickListener {
            findNavController().navigate(R.id.favouritesFragment)
        }

        // Tabs UI elements
        val tabList = view.findViewById<View>(R.id.tabList)
        val tabDownloads = view.findViewById<View>(R.id.tabDownloads)
        val tvList = view.findViewById<TextView>(R.id.tvTabList)
        val tvDownloads = view.findViewById<TextView>(R.id.tvTabDownloads)
        val underlineList = view.findViewById<View>(R.id.underlineList)
        val underlineDownloads = view.findViewById<View>(R.id.underlineDownloads)
        val sectionList = view.findViewById<View>(R.id.sectionList)
        val sectionDownloads = view.findViewById<View>(R.id.sectionDownloads)

        fun selectTab(listSelected: Boolean) {
            if (listSelected) {
                tvList.setTypeface(tvList.typeface, Typeface.BOLD)
                tvList.setTextColor(Color.BLACK)
                underlineList.visibility = View.VISIBLE
                sectionList.visibility = View.VISIBLE
                sectionDownloads.visibility = View.GONE

                tvDownloads.setTypeface(tvDownloads.typeface, Typeface.NORMAL)
                tvDownloads.setTextColor(Color.parseColor("#7A7A7A"))
                underlineDownloads.visibility = View.GONE
            } else {
                tvDownloads.setTypeface(tvDownloads.typeface, Typeface.BOLD)
                tvDownloads.setTextColor(Color.BLACK)
                underlineDownloads.visibility = View.VISIBLE
                sectionDownloads.visibility = View.VISIBLE
                sectionList.visibility = View.GONE

                tvList.setTypeface(tvList.typeface, Typeface.NORMAL)
                tvList.setTextColor(Color.parseColor("#7A7A7A"))
                underlineList.visibility = View.GONE
            }
        }

        tabList.setOnClickListener { selectTab(true) }
        tabDownloads.setOnClickListener { selectTab(false) }

        // Ensure default selection is List
        selectTab(true)

        return view
    }
}

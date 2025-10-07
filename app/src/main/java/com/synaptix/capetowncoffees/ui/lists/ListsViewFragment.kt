package com.synaptix.capetowncoffees.ui.lists

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ListsViewFragment : Fragment() {
    private val viewModel: ListsViewViewModel by viewModels()
    private lateinit var recyclerSaved: RecyclerView
    private val adapter = ListsViewAdapter { savedList ->
        Timber.d("Clicked on list: ${savedList.name}")
        val args = Bundle().apply { putString("listId", savedList.id) }
        findNavController().navigate(R.id.action_savedListsFragment_to_listDetailsFragment, args)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.d("onCreateView: Initializing SavedListsFragment UI")
        return inflater.inflate(R.layout.fragment_lists_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerSaved = view.findViewById(R.id.recyclerSavedLists)
        recyclerSaved.layoutManager = LinearLayoutManager(requireContext())
        recyclerSaved.adapter = adapter

        // Set up tab selection
        val tabList = view.findViewById<LinearLayout>(R.id.tabList)
        val tabDownloads = view.findViewById<LinearLayout>(R.id.tabDownloads)
        val tvTabList = view.findViewById<TextView>(R.id.tvTabList)
        val tvTabDownloads = view.findViewById<TextView>(R.id.tvTabDownloads)
        val underlineList = view.findViewById<View>(R.id.underlineList)
        val underlineDownloads = view.findViewById<View>(R.id.underlineDownloads)

        // Set up tab click listeners
        tabList.setOnClickListener {
            Timber.d("List tab clicked")
            selectTab(tvTabList, underlineList, true)
            selectTab(tvTabDownloads, underlineDownloads, false)
            observeSavedLists()
        }

        tabDownloads.setOnClickListener {
            Timber.d("Downloads tab clicked")
            selectTab(tvTabDownloads, underlineDownloads, true)
            selectTab(tvTabList, underlineList, false)
            // Clear the adapter when switching to downloads
            Timber.d("Clearing saved lists from UI (showing downloads)")
            adapter.submit(emptyList())
        }

        // Set up create list button click listener
        view.findViewById<View>(R.id.rowCreateList).setOnClickListener {
            Timber.d("Create list button clicked")
            findNavController().navigate(R.id.action_savedListsFragment_to_createListFragment)
        }

        // Initially select the List tab and load data
        selectTab(tvTabList, underlineList, true)
        observeSavedLists()
    }
    
    private fun observeSavedLists() {
        viewModel.userId.observe(viewLifecycleOwner) { uid ->
            if (uid != null) {
                viewModel.getLists().observe(viewLifecycleOwner) { lists ->
                    Timber.d("Observed ${lists.size} saved lists in UI")
                    if (lists.isNotEmpty()) {
                        Timber.d("First list: ${lists[0]}")
                    } else {
                        Timber.d("No saved lists found")
                    }
                    adapter.submit(lists)
                }
            } else {
                Timber.d("Waiting for userId to load...")
                adapter.submit(emptyList())
            }
        }
    }

    private fun selectTab(tab: TextView, underline: View, isSelected: Boolean) {
        tab.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        tab.setTextColor(if (isSelected) 
            resources.getColor(R.color.coffee_light, null)
        else 
            resources.getColor(R.color.text_primary, null))
        underline.visibility = if (isSelected) View.VISIBLE else View.GONE
    }
}
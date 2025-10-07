//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT assisted in designing and structuring this Fragment, including lifecycle
//handling, navigation setup, and interaction with the ViewModel.
//* It also provided guidance on ConstraintLayout usage and UI event handling.
//* It also helped generate useful comments
//======================================================================================


package com.synaptix.capetowncoffees.ui.lists

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    // ─────────── ViewModel / Adapters ───────────
    // VM produces the user's lists; adapter renders them and forwards row clicks.
    private val viewModel: ListsViewViewModel by viewModels()
    private lateinit var recyclerSaved: RecyclerView
    private val adapter = ListsViewAdapter { savedList ->
        Timber.d("Clicked on list: ${savedList.name}")
        val args = Bundle().apply { putString("listId", savedList.id) }
        findNavController().navigate(R.id.action_savedListsFragment_to_listDetailsFragment, args)
    }

    // ─────────── Lifecycle: Create View ───────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.d("onCreateView: Initializing SavedListsFragment UI")
        return inflater.inflate(R.layout.fragment_lists_view, container, false)
    }

    // ─────────── Lifecycle: View Created ───────────
    // Wires tabs, list, and actions; default shows the Saved Lists tab.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerSaved = view.findViewById(R.id.recyclerSavedLists)
        recyclerSaved.layoutManager = LinearLayoutManager(requireContext())
        recyclerSaved.adapter = adapter

        val tabList = view.findViewById<LinearLayout>(R.id.tabList)
        val tabDownloads = view.findViewById<LinearLayout>(R.id.tabDownloads)
        val tvTabList = view.findViewById<TextView>(R.id.tvTabList)
        val tvTabDownloads = view.findViewById<TextView>(R.id.tvTabDownloads)
        val underlineList = view.findViewById<View>(R.id.underlineList)
        val underlineDownloads = view.findViewById<View>(R.id.underlineDownloads)

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
            adapter.submit(emptyList())
        }

        view.findViewById<View>(R.id.rowCreateList).setOnClickListener {
            Timber.d("Create list button clicked")
            findNavController().navigate(R.id.action_savedListsFragment_to_createListFragment)
        }

        selectTab(tvTabList, underlineList, true)
        observeSavedLists()
    }

    // ─────────── Data Binding ───────────
    // Observes user id, then the lists stream; paints whenever data changes.
    private fun observeSavedLists() {
        viewModel.userId.observe(viewLifecycleOwner) { uid ->
            if (uid != null) {
                viewModel.getLists().observe(viewLifecycleOwner) { lists ->
                    Timber.d("Observed ${lists.size} saved lists in UI")
                    adapter.submit(lists)
                }
            } else {
                Timber.d("Waiting for userId to load…")
                adapter.submit(emptyList())
            }
        }
    }

    // ─────────── UI Helpers ───────────
    // Applies selected styling to the active tab and resets the other.
    private fun selectTab(tab: TextView, underline: View, isSelected: Boolean) {
        tab.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        val color = if (isSelected)
            ContextCompat.getColor(requireContext(), R.color.coffee_light)
        else
            ContextCompat.getColor(requireContext(), R.color.text_primary)
        tab.setTextColor(color)
        underline.visibility = if (isSelected) View.VISIBLE else View.GONE
    }
}

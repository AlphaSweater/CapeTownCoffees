package com.synaptix.capetowncoffees.ui.savedLists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
class ListDetailsFragment : Fragment() {

    private val viewModel: ListDetailsViewModel by viewModels()

    private lateinit var tvTitle: TextView
    private lateinit var ivPrivacy: ImageView
    private lateinit var recycler: RecyclerView

    private val placesAdapter = ListDetailsPlacesAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_list_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvTitle)
        ivPrivacy = view.findViewById(R.id.ivPrivacy)
        recycler = view.findViewById(R.id.recyclerPlaces)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = placesAdapter

        val listId = arguments?.getString("listId")
        if (listId.isNullOrBlank()) {
            Timber.w("ListDetailsFragment received empty listId; navigating up")
            findNavController().navigateUp()
            return
        }

        // Observe list details
        viewModel.observeList(listId).observe(viewLifecycleOwner) { list ->
            if (list == null) {
                Timber.d("List not found; finishing")
                findNavController().navigateUp()
                return@observe
            }
            tvTitle.text = list.name
            ivPrivacy.setImageResource(if (list.isPublic) R.drawable.ic_explore else R.drawable.ic_lock)
            // Load places
            viewModel.loadPlacesForIds(list.placeIds)
        }

        // Observe places
        viewModel.places.observe(viewLifecycleOwner) { places ->
            placesAdapter.submit(places)
        }

        // Close button
        view.findViewById<View>(R.id.btnClose).setOnClickListener {
            findNavController().navigateUp()
        }

        // Delete button at bottom
        view.findViewById<View>(R.id.btnDeleteList).setOnClickListener {
            // Show confirmation dialog
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete List")
                .setMessage("Are you sure you want to delete this list? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteListAndReturn(
                        id = listId,
                        onDone = { findNavController().navigateUp() },
                        onError = { e -> 
                            Timber.e(e, "Failed to delete list")
                            // Show error message
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Failed to delete list: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

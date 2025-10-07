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

package com.synaptix.capetowncoffees.ui.lists.listDetails

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ListDetailsFragment : Fragment() {

    private val viewModel: ListDetailsViewModel by viewModels()

    private lateinit var tvTitle: TextView
    private lateinit var ivPrivacy: ImageView
    private lateinit var recycler: RecyclerView

    // Inject the Assisted factory for the adapter
    @Inject lateinit var listDetailsAdapterFactory: ListDetailsPlacesAdapter.Factory
    @Inject lateinit var locationUtil: LocationUtil
    private lateinit var placesAdapter: ListDetailsPlacesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list_details, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle   = view.findViewById(R.id.tvTitle)
        ivPrivacy = view.findViewById(R.id.ivPrivacy)
        recycler  = view.findViewById(R.id.recyclerPlaces)

        // Hook up toolbar back navigation (layout uses MaterialToolbar with navigationIcon)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Create adapter with lifecycle-aware scope + click navigation
        placesAdapter = listDetailsAdapterFactory.create(
            viewLifecycleOwner.lifecycleScope
        ) { place ->
            Timber.d("Navigating to detail for placeId: ${place.id}")
            val args = Bundle().apply { putString("placeId", place.id) }
            findNavController().navigate(R.id.cafeDetailFragment, args)
        }

        recycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = placesAdapter
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        // Fetch user location (optional) and provide to adapter for distance display
        viewLifecycleOwner.lifecycleScope.launch {
            val result = locationUtil.getCurrentLatLng()
            result.onSuccess { latLng ->
                Timber.d("Got user location: $latLng")
                placesAdapter.setUserLocation(latLng)
            }.onFailure { e ->
                Timber.i(e, "Could not obtain user location (permissions?); distances hidden")
            }
        }

        val listId = arguments?.getString("listId")
        if (listId.isNullOrBlank()) {
            Timber.w("ListDetailsFragment received empty listId; navigating up")
            findNavController().navigateUp()
            return
        }

        // Observe userId first, then the list; when list arrives, load its places
        viewModel.userId.observe(viewLifecycleOwner) { uid ->
            if (uid != null) {
                viewModel.observeList(listId).observe(viewLifecycleOwner) { list ->
                    if (list == null) {
                        Timber.d("List not found; finishing")
                        findNavController().navigateUp()
                        return@observe
                    }
                    tvTitle.text = list.name
                    ivPrivacy.setImageResource(
                        if (list.isPublic) R.drawable.ic_ctc_earth_public
                        else R.drawable.ic_ctc_earth_private
                    )
                    viewModel.loadPlacesForIds(list.placeIds)
                }
            } else {
                Timber.d("Waiting for userId to load...")
            }
        }

        viewModel.places.observe(viewLifecycleOwner) { places ->
            placesAdapter.submit(places)
        }

        // Delete list
        view.findViewById<View>(R.id.btnDeleteList).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete List")
                .setMessage("Are you sure you want to delete this list? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteListAndReturn(
                        id = listId,
                        onDone = { findNavController().navigateUp() },
                        onError = { e ->
                            Timber.e(e, "Failed to delete list")
                            Toast.makeText(
                                requireContext(),
                                "Failed to delete list: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

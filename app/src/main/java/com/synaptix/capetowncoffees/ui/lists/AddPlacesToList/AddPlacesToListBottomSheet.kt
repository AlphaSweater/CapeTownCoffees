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
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.lists.AddPlacesToList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.synaptix.capetowncoffees.databinding.FragmentSavedAddPlacesToListBinding
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
public class AddPlacesToListBottomSheet : BottomSheetDialogFragment() {

    // ─────────── Args / Factory ───────────
    // Static entry to show the sheet for a specific place id.
    public companion object {
        private const val ARG_PLACE_ID: String = "arg_place_id"

        public fun new(placeId: String): AddPlacesToListBottomSheet =
            AddPlacesToListBottomSheet().apply { arguments = bundleOf(ARG_PLACE_ID to placeId) }
    }

    // ─────────── View & VM ───────────
    // ViewBinding is scoped to the view lifecycle to avoid leaks.
    private var _binding: FragmentSavedAddPlacesToListBinding? = null
    private val binding get() = _binding!!
    private val vm: AddPlacesToListViewModel by viewModels()

    // ─────────── UI State / Adapters ───────────
    // Adapter emits selected ids; we keep a local source list for filtering.
    private val adapter = AddPlacesToListAdapter { _, _ -> }
    private var fullList: List<CoffeeList> = emptyList()

    // ─────────── Lifecycle ───────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedAddPlacesToListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Expand to full height for better list browsing.
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Place id is required for the confirm action; we read once here.
        val placeId = requireArguments().getString(ARG_PLACE_ID).orEmpty()

        // ── Recycler setup
        binding.recyclerSavePlaceToList.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSavePlaceToList.adapter = adapter

        // ── Search filter (simple client-side name match)
        binding.etSearch.addTextChangedListener { q ->
            val term = q?.toString()?.trim()?.lowercase().orEmpty()
            val filtered = if (term.isEmpty()) fullList else fullList.filter { it.name.lowercase().contains(term) }
            adapter.submitList(filtered)
        }

        // ── Confirm selection (no-op if nothing chosen)
        binding.btnAdd.setOnClickListener {
            val selected = adapter.getSelected()
            if (selected.isNotEmpty()) vm.confirm(placeId, selected) else dismiss()
        }

        // ── Observe VM state and render lists / progress
        vm.state.observe(viewLifecycleOwner) { st ->
            when (st) {
                is AddListsUiState.Loading -> binding.btnAdd.isEnabled = false
                is AddListsUiState.Loaded  -> {
                    binding.btnAdd.isEnabled = true
                    fullList = st.lists
                    adapter.submitList(fullList)
                }
                is AddListsUiState.Error   -> {
                    binding.btnAdd.isEnabled = true
                    // keep UX simple; host may show a toast/snackbar if needed
                }
                is AddListsUiState.Done    -> dismiss()
            }
        }

        // Trigger initial load once the view exists.
        vm.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

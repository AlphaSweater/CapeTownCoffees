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

package com.synaptix.capetowncoffees.ui.lists.AddPlacesToList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.synaptix.capetowncoffees.databinding.FragmentSavedAddPlacesToListBinding
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
public class AddPlacesToListFragment : Fragment() {

    // ─────────── Args / Factory ───────────
    // Static entry to display this flow for a specific place.
    public companion object {
        private const val ARG_PLACE_ID: String = "arg_place_id"
        public fun newInstance(placeId: String): AddPlacesToListFragment =
            AddPlacesToListFragment().apply { arguments = bundleOf(ARG_PLACE_ID to placeId) }
    }

    // ─────────── View / VM / Adapters ───────────
    // ViewBinding is cleared in onDestroyView to match the view lifecycle.
    private var _binding: FragmentSavedAddPlacesToListBinding? = null
    private val binding get() = _binding!!
    private val vm: AddPlacesToListViewModel by viewModels()

    // Adapter emits ids of lists to add the place to; we keep a source list for filtering.
    private val adapter = AddPlacesToListAdapter { _, _ -> }
    private var fullList: List<CoffeeList> = emptyList()

    // ─────────── Lifecycle: create view ───────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedAddPlacesToListBinding.inflate(inflater, container, false)
        return binding.root
    }

    // ─────────── Lifecycle: view created ───────────
    // Wires UI, handles search filtering, observes state, and triggers initial load.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val placeId = requireArguments().getString(ARG_PLACE_ID).orEmpty()

        binding.recyclerSavePlaceToList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AddPlacesToListFragment.adapter
        }

        // Client-side filter on list name; ignore case to avoid allocations.
        binding.etSearch.addTextChangedListener { q ->
            val term = q?.toString()?.trim().orEmpty()
            val filtered =
                if (term.isEmpty()) fullList
                else fullList.filter { it.name.contains(term, ignoreCase = true) }
            adapter.submitList(filtered)
        }

        // Confirm adds the place to selected lists; if none selected, just close.
        binding.btnAdd.setOnClickListener {
            val selected = adapter.getSelected()
            if (selected.isNotEmpty()) vm.confirm(placeId, selected) else findNavController().navigateUp()
        }

        // Render state changes from the ViewModel.
        vm.state.observe(viewLifecycleOwner) { st ->
            when (st) {
                is AddListsUiState.Loading -> binding.btnAdd.isEnabled = false
                is AddListsUiState.Loaded -> {
                    binding.btnAdd.isEnabled = true
                    fullList = st.lists
                    adapter.submitList(fullList)
                }
                is AddListsUiState.Error -> {
                    binding.btnAdd.isEnabled = true
                    // Host can surface error UI (toast/snackbar) if desired.
                }
                is AddListsUiState.Done -> findNavController().navigateUp()
            }
        }

        vm.load()
    }

    // ─────────── Lifecycle: destroy view ───────────
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

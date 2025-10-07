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
class AddPlacesToListBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_PLACE_ID = "arg_place_id"
        fun new(placeId: String) = AddPlacesToListBottomSheet().apply {
            arguments = bundleOf(ARG_PLACE_ID to placeId)
        }
    }

    private var _binding: FragmentSavedAddPlacesToListBinding? = null
    private val binding get() = _binding!!
    private val vm: AddPlacesToListViewModel by viewModels()

    private val adapter = AddPlacesToListAdapter { _, _ -> }
    private var fullList: List<CoffeeList> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedAddPlacesToListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val placeId = requireArguments().getString(ARG_PLACE_ID).orEmpty()

        binding.recyclerSavePlaceToList.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSavePlaceToList.adapter = adapter

        binding.etSearch.addTextChangedListener { q ->
            val term = q?.toString()?.trim()?.lowercase().orEmpty()
            val filtered = if (term.isEmpty()) fullList else fullList.filter {
                it.name.lowercase().contains(term)
            }
            adapter.submitList(filtered)
        }

        binding.btnAdd.setOnClickListener {
            val selected = adapter.getSelected()
            if (selected.isNotEmpty()) vm.confirm(placeId, selected) else dismiss()
        }

        vm.state.observe(viewLifecycleOwner) { st ->
            when (st) {
                is AddListsUiState.Loading -> binding.btnAdd.isEnabled = false
                is AddListsUiState.Loaded  -> { binding.btnAdd.isEnabled = true; fullList = st.lists; adapter.submitList(fullList) }
                is AddListsUiState.Error   -> { binding.btnAdd.isEnabled = true /* show toast/snackbar if you want */ }
                is AddListsUiState.Done    -> dismiss()
            }
        }

        vm.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


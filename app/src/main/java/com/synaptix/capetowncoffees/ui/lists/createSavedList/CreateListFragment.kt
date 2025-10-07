package com.synaptix.capetowncoffees.ui.lists.createSavedList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentSavedCreateListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateListFragment : Fragment() {

    private var _binding: FragmentSavedCreateListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateListViewModel by viewModels()

    private var isPublic: Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSavedCreateListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Hook up toolbar back navigation (layout uses MaterialToolbar with navigationIcon)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.privacyChip.setOnClickListener {
            val popup = PopupMenu(requireContext(), binding.privacyChip)
            popup.menu.add("Public")
            popup.menu.add("Private")
            popup.setOnMenuItemClickListener { item ->
                val title = item.title.toString()
                binding.privacyChip.text = "$title ▾"
                isPublic = title.equals("Public", ignoreCase = true)
                true
            }
            popup.show()
        }

        binding.btnSaveList.setOnClickListener {
            val name = binding.etListName.text?.toString().orEmpty()
            val description = binding.etDescription.text?.toString()
            viewModel.saveList(name, description, isPublic)
        }

        viewModel.state.observe(viewLifecycleOwner, Observer { s ->
            when (s) {
                is CreateListUiState.Idle -> {
                    binding.btnSaveList.isEnabled = true
                    binding.etListName.error = null
                }
                is CreateListUiState.Loading -> binding.btnSaveList.isEnabled = false
                is CreateListUiState.ValidationError -> {
                    binding.btnSaveList.isEnabled = true
                    binding.etListName.error = s.nameError
                }
                is CreateListUiState.Success -> {
                    binding.btnSaveList.isEnabled = true
                    findNavController().navigateUp()
                    viewModel.resetState()
                }
                is CreateListUiState.Error -> {
                    binding.btnSaveList.isEnabled = true
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

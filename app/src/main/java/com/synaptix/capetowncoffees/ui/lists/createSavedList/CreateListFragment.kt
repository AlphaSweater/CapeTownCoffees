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

package com.synaptix.capetowncoffees.ui.lists.createSavedList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentSavedCreateListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateListFragment : Fragment() {

    // ─────────── View & VM ───────────
    // Binding is tied to the view lifecycle; ViewModel owns create-list logic.
    private var _binding: FragmentSavedCreateListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateListViewModel by viewModels()

    // ─────────── State ───────────
    // We keep chosen visibility; default is public.
    private var isPublic: Boolean = true

    // ─────────── Lifecycle ───────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedCreateListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Back arrow on the toolbar closes the screen.
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupPrivacyChooser()
        setupSaveAction()
        collectState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────── UI Wiring ───────────
    // Simple popup toggles list privacy; we reflect choice on the chip.
    private fun setupPrivacyChooser() = with(binding) {
        val labelPublic = getString(R.string.public_label).ifEmpty { "Public" }
        val labelPrivate = getString(R.string.private_label).ifEmpty { "Private" }
        val suffix = " ▾"

        privacyChip.setOnClickListener {
            PopupMenu(requireContext(), privacyChip).apply {
                menu.add(labelPublic)
                menu.add(labelPrivate)
                setOnMenuItemClickListener { item ->
                    val title = item.title.toString()
                    privacyChip.text = title + suffix
                    isPublic = title.equals(labelPublic, ignoreCase = true)
                    true
                }
            }.show()
        }
    }

    // Sends user inputs to the ViewModel; early-returns keep path clear.
    private fun setupSaveAction() = with(binding) {
        btnSaveList.setOnClickListener {
            val name = etListName.text?.toString().orEmpty()
            val description = etDescription.text?.toString()
            viewModel.saveList(name, description, isPublic)
        }
    }

    // ─────────── Collectors ───────────
    // Reacts to ViewModel state and updates controls accordingly.
    private fun collectState() {
        viewModel.state.observe(viewLifecycleOwner) { s ->
            when (s) {
                is CreateListUiState.Idle -> {
                    binding.btnSaveList.isEnabled = true
                    binding.etListName.error = null
                }
                is CreateListUiState.Loading -> {
                    binding.btnSaveList.isEnabled = false
                }
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
        }
    }
}

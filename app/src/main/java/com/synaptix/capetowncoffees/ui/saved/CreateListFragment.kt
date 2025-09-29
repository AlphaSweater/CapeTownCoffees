package com.synaptix.capetowncoffees.ui.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.synaptix.capetowncoffees.R

class CreateListFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val privacyChip = view.findViewById<TextView>(R.id.privacyChip)
        val btnClose = view.findViewById<TextView>(R.id.btnClose)
        val btnSave = view.findViewById<Button>(R.id.btnSaveList)

        // Close and Save both go back to Saved
        btnClose.setOnClickListener { findNavController().navigateUp() }
        btnSave.setOnClickListener { findNavController().navigateUp() }

        // Privacy popup: Public / Private
        privacyChip.setOnClickListener {
            val popup = PopupMenu(requireContext(), privacyChip)
            popup.menu.add("Public")
            popup.menu.add("Private")
            popup.setOnMenuItemClickListener { item ->
                privacyChip.text = "${item.title} ▾"
                true
            }
            popup.show()
        }
    }
}

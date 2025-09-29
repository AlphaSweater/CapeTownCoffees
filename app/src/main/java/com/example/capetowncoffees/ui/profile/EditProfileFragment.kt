package com.example.capetowncoffees.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.capetowncoffees.R

class EditProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up back button click listener
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            // Navigate back to profile
            findNavController().navigateUp()
        }

        // Set up change photo button click listener
        view.findViewById<View>(R.id.btnChangePhoto).setOnClickListener {
            // Photo change functionality would go here
        }

        // Set up save changes button click listener
        view.findViewById<View>(R.id.btnSaveChanges).setOnClickListener {
            // Save changes functionality would go here
            // For now, just navigate back to profile
            findNavController().navigateUp()
        }
    }
}

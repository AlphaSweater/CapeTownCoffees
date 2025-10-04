package com.synaptix.capetowncoffees.ui._simple

import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * Safely start a SimpleViewModel with fragment arguments.
 *
 * Marks the VM as "started" for debug guardrails and then calls vm.start(args).
 *
 * ### Example
 * ```
 * class PlaceDetailsFragment : Fragment(R.layout.fragment_place_details) {
 *   private val vm: PlaceDetailsViewModel by viewModels()
 *
 *   override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *     start(vm) // equivalent to vm.start(arguments) + debug mark
 *   }
 * }
 * ```
 */
fun Fragment.start(vm: SimpleViewModel, args: Bundle? = this.arguments) {
    vm._debugMarkStarted()
    vm.start(args)
}
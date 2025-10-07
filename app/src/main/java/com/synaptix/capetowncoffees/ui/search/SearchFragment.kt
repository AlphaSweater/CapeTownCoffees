package com.synaptix.capetowncoffees.ui.search

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.collect
import com.synaptix.capetowncoffees.ui.common.viewmodel.start
import com.synaptix.capetowncoffees.util.LocationFormattingUtil
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_search) {

    private val vm: SearchViewModel by activityViewModels()

    @Inject lateinit var locationUtils: LocationUtil

    // Top bar
    private lateinit var topBar: MaterialToolbar

    // Search
    private lateinit var etSearch: TextInputEditText

    // Filters
    private lateinit var filtersCard: MaterialCardView
    private lateinit var filtersHeader: View
    private lateinit var filtersDivider: View
    private lateinit var filtersContent: View
    private lateinit var ivChevron: ImageView
    private lateinit var sliderRadius: Slider
    private lateinit var tvRadiusValue: TextView
    private lateinit var switchStrictCoffee: MaterialSwitch

    // Suggestions
    private lateinit var tvSuggestionsHeader: TextView
    private lateinit var rvSuggestions: RecyclerView
    private val suggestionsAdapter by lazy { SuggestionsAdapter(::onSuggestionClicked) }

    companion object {
        const val FILTERS_TOGGLE = 0
        const val FILTERS_OPEN   = 1
        const val FILTERS_CLOSE  = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindViews(view)
        setupToolbar()
        setupFiltersRestoreThenWire()
        getCurrentLocation()
        setupSuggestionsList()

        start(vm, arguments)

        showBottomSuggestions(vm.suggestions.value is Loadable.Data)

        // Autofocus only if there's no cached text
        view.post {
            if (vm.query.value.isBlank()) {
                etSearch.requestFocus()
                showKeyboard(etSearch)
            }
        }

        // ── Collect VM state ────────────────────────────────────────────────
        collect(vm.suggestions.flow) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    suggestionsAdapter.submitListDistinct(emptyList())
                    showBottomSuggestions(true)
                }
                is Loadable.Data -> {
                    val items = loadable.value
                    val newSig = listSignature(items)
                    val shouldScroll = newSig != lastSuggestionsSignature && items.isNotEmpty()

                    suggestionsAdapter.submitListDistinct(items)

                    if (shouldScroll) {
                        rvSuggestions.post { scrollSuggestionsToTop() }
                        lastSuggestionsSignature = newSig
                    }

                    showBottomSuggestions(items.isNotEmpty())
                }
                is Loadable.Error -> {
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                    showBottomSuggestions(false)
                }
            }
        }

        // Effects (navigation + messages)
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Navigate -> when (eff.route) {
                    "search.openPlace" -> {
                        hideKeyboard(etSearch)
                        findNavController().navigate(
                            R.id.action_searchFragment_to_cafeDetailFragment,
                            eff.args
                        )
                    }
                }
                is Effect.Message -> Unit
            }
        }

        // ── IME “Search” → close keyboard ─────────────────
        etSearch.addTextChangedListener { s -> vm.onQueryTyping(s?.toString().orEmpty()) }
        etSearch.setOnEditorActionListener { _, actionId, event ->
            val imeGo = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (actionId == EditorInfo.IME_NULL && event?.keyCode == KeyEvent.KEYCODE_ENTER)
            if (imeGo) {
                hideKeyboard(etSearch)
                etSearch.clearFocus()
                true
            } else false
        }
    }

    private fun bindViews(root: View) {
        topBar = root.findViewById(R.id.topBar)
        etSearch = root.findViewById(R.id.etSearch)

        filtersCard = root.findViewById(R.id.filtersCard)
        filtersHeader = root.findViewById(R.id.filtersHeader)
        filtersDivider = root.findViewById(R.id.filtersDivider)
        filtersContent = root.findViewById(R.id.filtersContent)
        ivChevron = root.findViewById(R.id.ivChevron)
        sliderRadius = root.findViewById(R.id.sliderRadius)
        tvRadiusValue = root.findViewById(R.id.tvRadiusValue)
        switchStrictCoffee = root.findViewById(R.id.switchStrictCoffee)

        tvSuggestionsHeader = root.findViewById(R.id.tvSuggestionsHeader)
        rvSuggestions = root.findViewById(R.id.recyclerSuggestionsBelow)
    }

    private fun getCurrentLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                @Suppress("MissingPermission")
                runCatching { locationUtils.getCurrentLatLng().getOrNull() }
                    .onSuccess { loc -> loc?.let { vm.setUserLocation(it) } }
                    .onFailure { Timber.e(it, "Failed to get current location") }
            }
        }
    }

    private fun setupToolbar() {
        topBar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Restore UI controls from VM first, then attach listeners */
    private fun setupFiltersRestoreThenWire() {
        // 1) Restore values FROM ViewModel (which is seeded from SearchParamsUseCase)
        etSearch.setText(vm.query.value)
        etSearch.setSelection(etSearch.text?.length ?: 0)

        val km = (vm.radiusM.value / 1000f).coerceIn(sliderRadius.valueFrom, sliderRadius.valueTo)
        if (sliderRadius.value != km) sliderRadius.value = km

        switchStrictCoffee.isChecked = vm.strict.value

        tvRadiusValue.text = LocationFormattingUtil.formatDistance(vm.radiusM.value.toFloat())

        // 2) Wire listeners AFTER restoring values (forward events to VM only)
        filtersHeader.setOnClickListener { setFilters(FILTERS_TOGGLE) }

        sliderRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val meters = value.toInt() * 1000
                tvRadiusValue.text = LocationFormattingUtil.formatDistance(meters.toFloat())
                vm.onRadiusChanged(meters)
            }
        }
        switchStrictCoffee.setOnCheckedChangeListener { _, checked ->
            vm.onStrictChanged(checked)
        }
    }

    /** Filters controller: action = 0(toggle), 1(open), 2(close) */
    private fun setFilters(action: Int) {
        val currentlyExpanded = filtersContent.isVisible
        val targetExpanded = when (action) {
            FILTERS_OPEN  -> true
            FILTERS_CLOSE -> false
            else          -> !currentlyExpanded
        }
        if (currentlyExpanded == targetExpanded) return

        TransitionManager.beginDelayedTransition(filtersCard, AutoTransition().apply { duration = 180 })
        filtersDivider.isVisible = targetExpanded
        filtersContent.isVisible = targetExpanded

        ivChevron.animate().cancel()
        ivChevron.rotation = if (targetExpanded) 90f else 0f
        ivChevron.animate().rotation(ivChevron.rotation).setDuration(0).start()

        filtersCard.requestLayout()
    }

    private fun setupSuggestionsList() {
        rvSuggestions.layoutManager = LinearLayoutManager(requireContext())
        rvSuggestions.adapter = suggestionsAdapter
        (rvSuggestions.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        rvSuggestions.setHasFixedSize(true)
    }

    private var lastSuggestionsSignature: String? = null

    private fun listSignature(items: List<CoffeePlaceSuggestion>): String {
        val head = items.take(3).joinToString("|") { it.id }
        return "${items.size}#$head"
    }

    private fun scrollSuggestionsToTop() {
        rvSuggestions.stopScroll()
        (rvSuggestions.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(0, 0)
    }

    private fun showBottomSuggestions(show: Boolean) {
        tvSuggestionsHeader.isVisible = show
        rvSuggestions.isVisible = show
    }

    private fun onSuggestionClicked(item: CoffeePlaceSuggestion) {
        vm.onSuggestionClicked(item)
    }

    private fun showKeyboard(v: View) {
        v.post {
            v.requestFocus()
            val imm = requireContext().getSystemService<InputMethodManager>()
            imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(v: View) {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }
}
// ui/search/SearchFragment.kt
package com.synaptix.capetowncoffees.ui.search

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.ui.common.viewmodel.*
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_search) {

    private val vm: SearchViewModel by viewModels()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindViews(view)
        setupToolbar()
        setupFilters(view as ViewGroup)
        getCurrentLocation()
        setupSuggestionsList()

        start(vm, arguments)

        showBottomSuggestions(false)

        // Autofocus search
        view.post {
            etSearch.requestFocus()
            showKeyboard(etSearch)
        }

        // ── Collect VM state ────────────────────────────────────────────────
        // Collect suggestions (Loadable)
        collect(vm.suggestions.flow) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    // show skeleton if you have one, else clear
                    suggestionsAdapter.submitList(emptyList())
                    showBottomSuggestions(true)
                }
                is Loadable.Data -> {
                    val items = loadable.value.map { it.toAdapterItem() }
                    suggestionsAdapter.submitList(items)
                    showBottomSuggestions(items.isNotEmpty())
                }
                is Loadable.Error -> {
                    // optional: show error/snack
                    showBottomSuggestions(false)
                }
            }
        }

        // Effects (e.g., submit navigation)
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Navigate -> if (eff.route == "search.submit") {
                    hideKeyboard(etSearch)
                    // findNavController().navigate(R.id.action_search_to_results, eff.args)
                }
                is Effect.Message -> { /* snackbar(eff.text) */ }
            }
        }

        // ── Hooks ───────────────────────────────────────────────────────────
        // Text changes go to VM
        etSearch.addTextChangedListener { s -> vm.onQueryTyping(s?.toString().orEmpty()) }

        etSearch.setOnEditorActionListener { _, actionId, event ->
            val imeGo = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (actionId == EditorInfo.IME_NULL && event?.keyCode == KeyEvent.KEYCODE_ENTER)
            if (imeGo) { vm.submitSearch(); true } else false
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
        // Fetch and let the VM compute distance text
        viewLifecycleOwner.lifecycleScope.launch {
            @Suppress("MissingPermission")
            runCatching { locationUtils.getCurrentLatLng().getOrNull() }
                .onSuccess { location -> location?.let { vm.setUserLocation(it) } }
                .onFailure {
                    Timber.e(it, "Failed to get current location")
                    // VM will hide distance when it can't compute it next tick
                }
        }
    }

    private fun setupToolbar() {
        topBar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupFilters(container: ViewGroup) {
        // Expand/collapse
        filtersHeader.setOnClickListener { toggleFilters(container) }

        // Radius label
        fun updateRadiusLabel(v: Float) { tvRadiusValue.text = locationUtils.formatDistance(v) }
        updateRadiusLabel(sliderRadius.value)

        sliderRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) updateRadiusLabel(value)
            vm.onRadiusChanged(value.toInt())
        }

        switchStrictCoffee.setOnCheckedChangeListener { _, checked ->
            vm.onStrictChanged(checked)
        }
    }

    private fun toggleFilters(container: ViewGroup) {
        val expand = filtersContent.visibility != View.VISIBLE
        TransitionManager.beginDelayedTransition(container, AutoTransition().apply { duration = 180 })
        filtersDivider.isVisible = expand
        filtersContent.isVisible = expand
        ivChevron.animate().rotation(if (expand) 90f else 0f).setDuration(180).start()
    }

    private fun setupSuggestionsList() {
        rvSuggestions.layoutManager = LinearLayoutManager(requireContext())
        rvSuggestions.adapter = suggestionsAdapter
        (rvSuggestions.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        rvSuggestions.setHasFixedSize(true)
    }

    private fun showBottomSuggestions(show: Boolean) {
        tvSuggestionsHeader.isVisible = show
        rvSuggestions.isVisible = show
    }

    private fun onSuggestionClicked(item: SuggestionItem) {
        etSearch.setText(item.title)
        etSearch.setSelection(item.title.length)
        vm.submitSearch()
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

/* ---------------- Adapter + Model (UI-only) ---------------- */

private data class SuggestionItem(
    val title: String,
    val address: String? = null
)

private fun CoffeePlaceSuggestion.toAdapterItem() =
    SuggestionItem(
        title = name.orEmpty(),
        address = address
    )

private class SuggestionsAdapter(
    private val onClick: (SuggestionItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<SuggestionItem, SuggestionsAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<SuggestionItem>() {
        override fun areItemsTheSame(oldItem: SuggestionItem, newItem: SuggestionItem) =
            oldItem.title == newItem.title && oldItem.address == newItem.address
        override fun areContentsTheSame(oldItem: SuggestionItem, newItem: SuggestionItem) =
            oldItem == newItem
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val subtitle: TextView? = itemView.findViewById(R.id.tvSubtitle)

        fun bind(item: SuggestionItem) {
            title.text = item.title
            subtitle?.apply {
                isVisible = !item.address.isNullOrBlank()
                text = item.address.orEmpty()
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_suggestion, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
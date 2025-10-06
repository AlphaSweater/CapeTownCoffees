package com.synaptix.capetowncoffees.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : androidx.fragment.app.Fragment(R.layout.fragment_search) {

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

    // Bottom suggestions
    private lateinit var tvSuggestionsHeader: TextView
    private lateinit var rvSuggestions: RecyclerView
    private val suggestionsAdapter by lazy { SuggestionsAdapter(::onSuggestionClicked) }

    // Debounce
    private var typingJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Optional: subtle enter/return motion for niceness
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward = */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward = */ false)

        bindViews(view)
        setupToolbar()
        setupFilters(view as ViewGroup)
        setupSuggestionsList()

        // Initial state (show “discover” area only if you want)
        showBottomSuggestions(false)

        hookSearchTyping()
        hookImeAction()

        // Autofocus search (optional)
        view.post {
            etSearch.requestFocus()
            showKeyboard(etSearch)
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

    private fun setupToolbar() {
        topBar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupFilters(container: ViewGroup) {
        // Expand/collapse
        filtersHeader.setOnClickListener {
            val expand = filtersContent.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(container, AutoTransition().apply { duration = 180 })
            filtersDivider.isVisible = expand
            filtersContent.isVisible = expand
            ivChevron.animate().rotation(if (expand) 90f else 0f).setDuration(180).start()
        }

        // Radius label
        fun updateRadiusLabel(v: Float) {
            tvRadiusValue.text = "${v.toInt()} km"
        }
        updateRadiusLabel(sliderRadius.value)

        sliderRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) updateRadiusLabel(value)
            // Re-query on radius change if you want live updates:
            fetchSuggestions(etSearch.text?.toString().orEmpty())
        }

        switchStrictCoffee.setOnCheckedChangeListener { _, _ ->
            // Re-query on strict toggle:
            fetchSuggestions(etSearch.text?.toString().orEmpty())
        }
    }

    private fun setupSuggestionsList() {
        rvSuggestions.layoutManager = LinearLayoutManager(requireContext())
        rvSuggestions.adapter = suggestionsAdapter
        (rvSuggestions.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        rvSuggestions.setHasFixedSize(true)
    }

    private fun hookSearchTyping() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                typingJob?.cancel()
                typingJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(220) // debounce
                    fetchSuggestions(s?.toString().orEmpty())
                }
            }
        })
    }

    private fun hookImeAction() {
        etSearch.setOnEditorActionListener { v, actionId, event ->
            val imeGo = actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (actionId == EditorInfo.IME_NULL && event?.keyCode == KeyEvent.KEYCODE_ENTER)
            if (imeGo) {
                submitSearch(v.text?.toString().orEmpty())
                true
            } else false
        }
    }

    private fun showBottomSuggestions(show: Boolean) {
        tvSuggestionsHeader.isVisible = show
        rvSuggestions.isVisible = show
    }

    /** Replace this with your VM call (Google Places, your backend, etc.) */
    private fun fetchSuggestions(query: String) {
        val radiusKm = sliderRadius.value.toInt()
        val strictOnly = switchStrictCoffee.isChecked

        if (query.isBlank()) {
            suggestionsAdapter.submitList(emptyList())
            showBottomSuggestions(false)
            return
        }

        // TODO: vm.fetchSuggestions(query, radiusKm, strictOnly)
        // Mocked suggestions for now:
        val items = buildList {
            add(Suggestion("“$query” near me"))
            add(Suggestion("$query coffee"))
            add(Suggestion("$query roasters"))
            if (!strictOnly) add(Suggestion("$query cafés"))
            add(Suggestion("$query in ${radiusKm}km"))
        }

        suggestionsAdapter.submitList(items)
        showBottomSuggestions(items.isNotEmpty())
    }

    private fun submitSearch(query: String) {
        hideKeyboard(etSearch)
        if (query.isBlank()) return

        val radiusKm = sliderRadius.value.toInt()
        val strictOnly = switchStrictCoffee.isChecked

        // TODO: vm.search(query, radiusKm, strictOnly)
        // For now, just close screen or navigate to results list:
        // findNavController().navigate(R.id.action_search_to_results, bundleOf("q" to query, "radius" to radiusKm, "strict" to strictOnly))
    }

    private fun onSuggestionClicked(item: Suggestion) {
        etSearch.setText(item.title)
        etSearch.setSelection(item.title.length)
        submitSearch(item.title)
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

/* ---------------------------- Adapter + Model ---------------------------- */

private data class Suggestion(
    val title: String,
    val subtitle: String? = null
)

private class SuggestionsAdapter(
    private val onClick: (Suggestion) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Suggestion, SuggestionsAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Suggestion>() {
        override fun areItemsTheSame(oldItem: Suggestion, newItem: Suggestion) =
            oldItem.title == newItem.title && oldItem.subtitle == newItem.subtitle
        override fun areContentsTheSame(oldItem: Suggestion, newItem: Suggestion) =
            oldItem == newItem
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val subtitle: TextView? = itemView.findViewById(R.id.tvSubtitle) // optional

        fun bind(item: Suggestion) {
            title.text = item.title
            subtitle?.apply {
                isVisible = !item.subtitle.isNullOrBlank()
                text = item.subtitle.orEmpty()
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(
            R.layout.item_search_suggestion, parent, false
        )
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}

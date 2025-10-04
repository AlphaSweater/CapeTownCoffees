package com.synaptix.capetowncoffees.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Effect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collectLoadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.start
import com.synaptix.capetowncoffees.ui.common.SkeletonAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.CategoryAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.FeaturedAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.NearMeItemAdapter
import com.synaptix.capetowncoffees.ui.savedLists.AddPlacesToList.AddPlacesToListBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by activityViewModels()

    private lateinit var categoryAdapter: CategoryAdapter
    @Inject lateinit var featuredAdapterFactory: FeaturedAdapter.Factory
    private lateinit var featuredAdapter: FeaturedAdapter

    @Inject lateinit var nearMeAdapterFactory: NearMeItemAdapter.Factory
    private lateinit var nearMeAdapter: NearMeItemAdapter

    private lateinit var featuredSkeletonAdapter: SkeletonAdapter
    private lateinit var nearSkeletonAdapter:   SkeletonAdapter

    // Permissions launcher
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) fetchLocation() else Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // SimpleVM lifecycle entry
        start(vm)

        initAdapters()
        setupRecyclerViews()
        setupCollectors()
        setupClicks()

        ensureLocation()

        (binding.rvFeatured.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false
        (binding.rvNearMe.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false
    }

    override fun onResume() {
        super.onResume()
        val comingBack = (featuredAdapter.itemCount > 0 || nearMeAdapter.itemCount > 0)
        if (comingBack) {
            if (binding.rvFeatured.adapter !== featuredSkeletonAdapter) {
                binding.rvFeatured.adapter = featuredSkeletonAdapter
            }
            if (binding.rvNearMe.adapter !== nearSkeletonAdapter) {
                binding.rvNearMe.adapter = nearSkeletonAdapter
            }
            showFeaturedSkeleton(show = true, hideContent = false) // content is skeleton list
            showNearMeSkeleton(show = true, hideContent = false)
            // vm.refreshIfStale(maxAgeMs = 30_000) // optional
        }
    }



    private fun initAdapters() {
        categoryAdapter = CategoryAdapter { category: Category ->
            vm.onCategorySelected(category)
        }
        featuredAdapter = featuredAdapterFactory.create(
            currentLocation = null,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onItemClick = { item -> navigateToCafeDetailsId(item.id) }
        )
        nearMeAdapter = nearMeAdapterFactory.create(
            currentLocation = null,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onClick = ::onNearMeClick
        )

        featuredSkeletonAdapter = SkeletonAdapter(
            count = 5,
            layoutResId = R.layout.item_place_skeleton
        )
        nearSkeletonAdapter = SkeletonAdapter(
            count = 5,
            layoutResId = R.layout.item_place_skeleton
        )
    }

    private fun setupRecyclerViews() = with(binding) {
        rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
        rvFeatured.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = featuredAdapter
            setHasFixedSize(true)
        }
        rvNearMe.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = nearMeAdapter
            setHasFixedSize(true)
        }

    }

    private fun setupClicks() = with(binding) {
        searchBar.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
        }
    }

    private fun setupCollectors() {
        // Effects (snackbars, nav, etc.)
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message  -> Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
                else               -> { /* none yet */}
            }
        }

        // Bind UI (categories, selection, current location, refreshing)
        collect(vm.ui.flow) { ui ->
            categoryAdapter.updateCategories(ui.categories)
            // Do NOT toggle the center spinner here; let section shimmers show instead.
            binding.progressBar.visibility = View.GONE
        }

        // Featured
        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    if (binding.rvFeatured.adapter !== featuredSkeletonAdapter) {
                        binding.rvFeatured.adapter = featuredSkeletonAdapter
                    }
                    showFeaturedSkeleton(true, hideContent = false)
                }
                is Loadable.Data -> {
                    // swap back to real adapter before hiding shimmer
                    if (binding.rvFeatured.adapter !== featuredAdapter) {
                        binding.rvFeatured.adapter = featuredAdapter
                    }
                    featuredAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    showFeaturedSkeleton(false)
                }
                is Loadable.Error -> {
                    // still swap back so user sees last good data / empty state
                    if (binding.rvFeatured.adapter !== featuredAdapter) {
                        binding.rvFeatured.adapter = featuredAdapter
                    }
                    showFeaturedSkeleton(false)
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

// Near Me
        collectLoadable(vm.nearMe) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    if (binding.rvNearMe.adapter !== nearSkeletonAdapter) {
                        binding.rvNearMe.adapter = nearSkeletonAdapter
                    }
                    showNearMeSkeleton(true, hideContent = false)
                }
                is Loadable.Data -> {
                    if (binding.rvNearMe.adapter !== nearMeAdapter) {
                        binding.rvNearMe.adapter = nearMeAdapter
                    }
                    nearMeAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    showNearMeSkeleton(false)
                }
                is Loadable.Error -> {
                    if (binding.rvNearMe.adapter !== nearMeAdapter) {
                        binding.rvNearMe.adapter = nearMeAdapter
                    }
                    showNearMeSkeleton(false)
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ───────── collectors call these ─────────
    private fun showFeaturedSkeleton(show: Boolean, hideContent: Boolean = false) = with(binding) {
        if (show) {
            crossfadeInShimmer(
                shimmer = shimmerFeatured,
                content = rvFeatured,
                minContainer = featuredContainer,
                peekDimen = R.dimen.home_featured_peek_height,
                hideContent = hideContent
            )
        } else {
            crossfadeOutShimmer(
                shimmer = shimmerFeatured,
                content = rvFeatured,
                minContainer = featuredContainer
            )
        }
    }

    private fun showNearMeSkeleton(show: Boolean, hideContent: Boolean = false) = with(binding) {
        if (show) {
            crossfadeInShimmer(
                shimmer = shimmerNear,
                content = rvNearMe,
                minContainer = nearContainer,
                peekDimen = R.dimen.home_near_peek_height,
                hideContent = hideContent
            )
        } else {
            crossfadeOutShimmer(
                shimmer = shimmerNear,
                content = rvNearMe,
                minContainer = nearContainer
            )
        }
    }

    private val crossFadeDuration = 220L

    private fun crossfadeInShimmer(
        shimmer: View,
        content: View,
        minContainer: ViewGroup,
        peekDimen: Int,
        hideContent: Boolean
    ) {
        // if true, fully hide content while shimmer runs; else dim it (peek)
        content.alpha = if (hideContent) 0f else 0.3f
        content.visibility = View.VISIBLE

        minContainer.minimumHeight = resources.getDimensionPixelSize(peekDimen)

        shimmer.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().cancel()
            animate()
                .alpha(1f)
                .setDuration(crossFadeDuration)
                .withStartAction { if (this is com.facebook.shimmer.ShimmerFrameLayout) startShimmer() }
                .start()
        }
    }

    private fun crossfadeOutShimmer(
        shimmer: View,
        content: View,
        minContainer: ViewGroup
    ) {
        content.post {
            shimmer.animate().cancel()
            shimmer.animate()
                .alpha(0f)
                .setDuration(crossFadeDuration)
                .withEndAction {
                    if (shimmer is com.facebook.shimmer.ShimmerFrameLayout) shimmer.stopShimmer()
                    shimmer.visibility = View.GONE
                    shimmer.alpha = 1f

                    content.animate().cancel()
                    content.animate()
                        .alpha(1f)
                        .setDuration(160L)
                        .start()

                    minContainer.minimumHeight = 0
                }
                .start()
        }
    }


    // ───────────────────── Location permissions & fetch ─────────────────────

    private fun ensureLocation() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) fetchLocation() else requestPerms.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun fetchLocation() {
        @Suppress("MissingPermission")
        LocationServices
            .getFusedLocationProviderClient(requireContext())
            .lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) vm.onUserLocation(LatLng(loc.latitude, loc.longitude))
                else fetchFreshLocationFallback()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to get last location", Toast.LENGTH_SHORT).show()
                fetchFreshLocationFallback()
            }
    }

    private fun fetchFreshLocationFallback() {
        // If you have a suspend util (like LocationUtil.getCurrentLatLng()), call it here in a coroutine.
        // For brevity, we just give up silently; VM shows a message if it can’t fetch without location.
    }

    // ─────────────────────────── Click routing ───────────────────────────

    private fun onNearMeClick(nearMeItemClick: NearMeItemAdapter.Click) = when (nearMeItemClick) {
        is NearMeItemAdapter.Click.Open -> navigateToCafeDetailsId(nearMeItemClick.id)
        is NearMeItemAdapter.Click.ToggleFavorite -> {
            // TODO: hook up to VM if you track favorites
            // vm.toggleFavorite(c.id, c.newValue)
            Toast.makeText(requireContext(), "Fav ${nearMeItemClick.id}: ${nearMeItemClick.newValue}", Toast.LENGTH_SHORT).show()
        }
        is NearMeItemAdapter.Click.AddToList -> showAddToListBottomSheet(nearMeItemClick.id)
    }

    private fun showAddToListBottomSheet(id: String) {
        val bottomSheet = AddPlacesToListBottomSheet.new(id)
        bottomSheet.show(childFragmentManager, "AddPlacesToListBottomSheet")
    }

    private fun navigateToCafeDetailsId(id: String) {
        val bundle = Bundle().apply { putString("placeId", id) }
        findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment, bundle)
    }

    // ─────────────────────────── Lifecycle ───────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

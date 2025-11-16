# 📡 Offline Mode & Connectivity Detection

This document explains how to use the offline mode and connectivity detection features in Cape Town Coffees.

## Overview

The app includes a comprehensive offline mode system that:

- ✅ Monitors real network connectivity (WiFi, mobile data, internet reachability)
- ✅ Allows users to manually force offline mode via Settings
- ✅ Provides reactive state updates via Kotlin Flow
- ✅ Makes it easy for repositories and UI to check online status
- ✅ Includes helper utilities for offline-aware behavior

## Architecture Components

### 1. NetworkStatusService

Monitors actual network connectivity using:
- Android ConnectivityManager callbacks for immediate network changes
- Periodic HTTP HEAD requests to verify internet reachability
- Combines both to provide accurate online/offline status

**Location:** `data/connectivity/NetworkStatusService.kt`

**Usage:**
```kotlin
@Inject lateinit var networkStatusService: NetworkStatusService

// Check current status
val isOnline = networkStatusService.isOnline.value

// Observe changes
lifecycleScope.launch {
    networkStatusService.isOnline.collect { online ->
        if (online) {
            // Network is available
        } else {
            // Network is unavailable
        }
    }
}

// Force immediate check
val status = networkStatusService.checkNow()
```

### 2. OfflineModeManager

Manages user's offline mode preference and combines it with network status to compute "effective" online status.

**Formula:** `effectiveIsOnline = networkIsOnline AND NOT userForcedOffline`

**Location:** `data/connectivity/OfflineModeManager.kt`

**Usage:**
```kotlin
@Inject lateinit var offlineModeManager: OfflineModeManager

// Check effective status
val isOnline = offlineModeManager.effectiveIsOnline.value

// Toggle user offline mode
lifecycleScope.launch {
    offlineModeManager.setUserOfflineMode(true)  // Force offline
    offlineModeManager.setUserOfflineMode(false) // Allow online
}

// Check user preference
val userWantsOffline = offlineModeManager.isUserOfflineMode.value
```

### 3. CheckConnectivityUseCase

Simple use case that provides a clean interface for checking connectivity from UI and business logic.

**Location:** `domain/usecase/connectivity/CheckConnectivityUseCase.kt`

**Usage:**
```kotlin
@Inject lateinit var checkConnectivity: CheckConnectivityUseCase

// Quick check
if (checkConnectivity.isEffectivelyOnline()) {
    // Make network request
} else {
    // Show cached data
}

// Get full state
val state = checkConnectivity.getCurrentState()
println("Online: ${state.isOnline}")
println("User forced offline: ${state.isUserForcedOffline}")
println("Effective online: ${state.effectiveIsOnline}")
println("Reason: ${state.getOfflineReason()}")

// Observe changes
lifecycleScope.launch {
    checkConnectivity.observeConnectivityState().collect { state ->
        updateUI(state)
    }
}
```

### 4. OfflineAwareRepository Interface

Interface that repositories can implement to easily check connectivity before making network calls.

**Location:** `data/connectivity/OfflineAwareRepository.kt`

**Usage:**
```kotlin
class MyCoffeeRepository @Inject constructor(
    private val api: CoffeeApi,
    override val offlineModeManager: OfflineModeManager
) : OfflineAwareRepository {

    suspend fun fetchCoffees(): Result<List<Coffee>> {
        // Throws OfflineException if offline
        return executeIfOnlineAsResult {
            api.getCoffees()
        }
    }

    suspend fun fetchCoffeesWithCache(cached: List<Coffee>): List<Coffee> {
        // Returns cached data if offline
        return executeOrFallback(
            operation = { api.getCoffees() },
            fallback = cached
        )
    }
}
```

### 5. UI Helper Extensions

Fragment extensions that make it easy to show offline indicators and react to connectivity changes.

**Location:** `ui/common/ConnectivityExtensions.kt`

**Usage:**
```kotlin
class MyFragment : Fragment() {
    @Inject lateinit var checkConnectivity: CheckConnectivityUseCase

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Option 1: Show automatic offline banner
        showOfflineBannerWhenNeeded(
            checkConnectivity = checkConnectivity,
            rootView = binding.root
        )

        // Option 2: Custom connectivity handling
        observeConnectivity(checkConnectivity) { state ->
            if (!state.effectiveIsOnline) {
                disableNetworkFeatures()
            } else {
                enableNetworkFeatures()
            }
        }

        // Option 3: Execute only when online
        binding.refreshButton.setOnClickListener {
            executeIfOnline(checkConnectivity, binding.root) {
                refreshData()
            }
        }
    }
}
```

## User Settings

Users can toggle offline mode in **Settings → Data → Offline Mode**.

When enabled:
- All network requests are blocked at the repository level
- Cached data is used when available
- A persistent banner is shown in the UI explaining the offline state
- The preference is saved and persists across app restarts

## Testing

Unit tests are provided for the core connectivity logic:

**Location:** `app/src/test/java/com/synaptix/capetowncoffees/connectivity/`

Run tests:
```bash
./gradlew test
```

## Best Practices

### For Repositories

1. **Implement OfflineAwareRepository** for repositories that make network calls
2. **Use executeIfOnline()** for operations that must be online
3. **Use executeOrFallback()** when you have cached data
4. **Handle OfflineException** gracefully in calling code

```kotlin
// Good
suspend fun fetchData(): Result<Data> {
    return executeIfOnlineAsResult {
        api.getData()
    }
}

// Also good
suspend fun fetchDataSmart(cached: Data?): Data {
    return executeOrFallback(
        operation = { api.getData() },
        fallback = cached ?: Data.empty()
    )
}
```

### For ViewModels

1. **Inject CheckConnectivityUseCase** to check before operations
2. **Observe connectivity state** to update UI accordingly
3. **Show appropriate error messages** when offline

```kotlin
class MyViewModel @Inject constructor(
    private val repository: MyRepository,
    private val checkConnectivity: CheckConnectivityUseCase
) : ViewModel() {

    fun loadData() {
        viewModelScope.launch {
            if (!checkConnectivity.isEffectivelyOnline()) {
                _uiState.value = UiState.OfflineError
                return@launch
            }

            repository.fetchData()
                .onSuccess { data -> _uiState.value = UiState.Success(data) }
                .onFailure { error -> _uiState.value = UiState.Error(error) }
        }
    }
}
```

### For Fragments

1. **Use the helper extensions** for showing offline banners
2. **Disable network features** when offline
3. **Show helpful messages** explaining why features are unavailable

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Auto-show banner
    showOfflineBannerWhenNeeded(checkConnectivity, binding.root)

    // Disable features when offline
    observeConnectivity(checkConnectivity) { state ->
        binding.refreshButton.isEnabled = state.effectiveIsOnline
        binding.syncButton.isEnabled = state.effectiveIsOnline
    }
}
```

## Configuration

The network monitoring behavior can be configured in `NetworkStatusService.kt`:

```kotlin
private val pingIntervalMs = 30_000L      // Check every 30 seconds
private val pingTimeoutMs = 5_000         // 5 second timeout
private val pingUrl = "https://www.google.com" // Reliable endpoint
```

For production, consider:
- Increasing ping interval to save battery (e.g., 60 seconds)
- Using your own backend health endpoint for privacy
- Adjusting timeout based on expected network conditions

## Troubleshooting

### Service not starting
Ensure `NetworkStatusService.start()` is called in `CapeTownCoffeesApp.onCreate()`

### User preference not persisting
Check that SharedPreferences is properly provided via Hilt in `AppModule`

### Offline banner not showing
Ensure you're calling `showOfflineBannerWhenNeeded()` after view is created

### Repository still making network calls
Verify the repository implements `OfflineAwareRepository` and wraps calls with `executeIfOnline()`

## Future Enhancements

Potential improvements for the offline mode system:

- [ ] Request queue for operations performed while offline (sync when back online)
- [ ] Cache-first strategies with background refresh
- [ ] Granular control over which features work offline
- [ ] Download manager for explicit offline content
- [ ] Network quality indicators (slow/fast/offline)
- [ ] Smart sync when on WiFi only
- [ ] Data usage tracking and warnings

## Related Files

- `NetworkStatusService.kt` - Network monitoring
- `OfflineModeManager.kt` - User preference + effective status
- `CheckConnectivityUseCase.kt` - Use case layer
- `OfflineAwareRepository.kt` - Repository interface
- `ConnectivityExtensions.kt` - UI helpers
- `NetworkState.kt` - Domain model
- `SettingsFragment.kt` - User toggle UI
- `CheckConnectivityUseCaseTest.kt` - Unit tests

## Support

For questions or issues with the offline mode feature, contact the development team or check the Architecture.md document for more details on the overall app architecture.

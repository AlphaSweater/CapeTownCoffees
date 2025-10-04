# CapeTownCoffees – Simple Architecture Guide

A lightweight, pragmatic UI architecture for this project. It trims boilerplate while keeping structure clear and testable.

Core goals:
- 90% of screens should be expressible with a tiny ViewModel + a few state holders.
- Uniform loading / error handling (no ad‑hoc flags everywhere).
- Explicit separation: business work in suspend/Flow use cases; UI orchestration in SimpleViewModel subclasses.
- Fragments stay dumb: only collect state + render.

---
## Table of Contents
1. TL;DR Quick Start
2. Folder / File Map
3. Core Primitives
4. ViewModel Patterns
5. Fragment Patterns
6. Loading & Error Strategy
7. Effects (One‑shot UI Events)
8. Choosing StateVar vs LoadableVar
9. Naming & Labeling Conventions
10. Concurrency & Cancellation
11. Troubleshooting / FAQ
12. Future Extensions (ideas)

---
## 1. TL;DR Quick Start
Minimal screen setup:
``` kotlin
@HiltViewModel
class CoffeeDetailsViewModel @Inject constructor(
    private val getCoffee: GetCoffee,              // suspend (id) -> Coffee
    private val observeReviews: ObserveReviews     // (id) -> Flow<List<Review>>
) : SimpleViewModel() {
    val isLoading = booleanState()                     // simple spinner flag
    val coffee = state<Coffee?>(null)                  // main entity
    val reviews = loadableState<List<Review>>()        // secondary list with load/error UI

    override fun start(args: Bundle?) {
        val id = args?.getString(ScreenArgs.COFFEE_ID) ?: return
        fetchInto(coffee, showLoading = isLoading, label = "coffee") { getCoffee(id) }
        observeInto(reviews, observeReviews(id), label = "reviews")
    }

    fun refresh() = coffee.value?.id?.let { id ->
        fetchInto(coffee, showLoading = isLoading, label = "coffee-refresh") { getCoffee(id, force = true) }
        fetchInto(reviews, label = "reviews-refresh") { getCoffeeReviews(id) }
    }
}
```
Fragment usage:
``` kotlin
class CoffeeDetailsFragment : Fragment(R.layout.fragment_coffee_details) {
    private val vm: CoffeeDetailsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        start(vm) // ALWAYS call

        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message  -> showSnackbar(eff.text)
                is Effect.Navigate -> findNavController().navigate(eff.route, eff.args)
            }
        }
        collect(vm.isLoading.flow) { loading -> progress.isVisible = loading }
        collect(vm.coffee.flow) { coffee -> renderCoffee(coffee) }
        collectLoadable(vm.reviews) { loadable -> renderReviews(loadable) }
    }
}
```

---
## 2. Folder / File Map
All architecture code lives under:
`app/src/main/java/.../ui/_simple/`

| File                    | Purpose                                                                                                                   |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `SimplePrimitives.kt`   | UiError, Loadable, Effect, StateVar/ListStateVar/LoadableVar, factory helpers, result adapters                            |
| `SimpleViewModel.kt`    | Base VM with fetchInto / observeInto / effect channel                                                                     |
| `FragmentExtensions.kt` | Fragment helpers: collect(), collectLoadable(), start()                                                                   |
| `ScreenArgs.kt`         | Central keys / helpers for Bundle arguments                                                                               |
| `SimpleVmDebug.kt`      | Logging, guard rails (warn before start, last error tracking)                                                             |
| (Deprecated stubs)      | `Core.kt`, `StateVars.kt`, `UseCaseAdapters.kt`, `FragmentCollectors.kt`, `FragmentStart.kt` – safe to delete once merged |

---
## 3. Core Primitives
### UiError
A normalized UI-friendly error wrapper.
``` kotlin
data class UiError(val message: String, val cause: Throwable? = null, val recoverable: Boolean = true)
fun Throwable.toUiError(defaultMsg: String = "Something went wrong"): UiError
```
Behavior: CancellationExceptions rethrown; message fallback applied.

### Loadable<T>
Represents a sub-resource with lifecycle states.
States: Uninitialized, Loading, Data(value), Error(uiError)

### Effect
One-shot events (snackbar, navigation). Collected separately from continuous state.
``` kotlin
sealed class Effect { data class Message(val text: String) : Effect(); data class Navigate(val route: String, val args: Bundle? = null) : Effect() }
```

### State Holders
| Type              | Use For                              | UI Concern                | Notes                                  |
|-------------------|--------------------------------------|---------------------------|----------------------------------------|
| `StateVar<T>`     | Main/simple state (entity, form)     | No internal loading/error | MutableStateFlow wrapper               |
| `ListStateVar<T>` | Mutable list patterns                | Convenience add/remove    | Avoid for paged lists (use paging lib) |
| `LoadableVar<T>`  | Secondary loads (lists, subsections) | Built-in Loading/Error    | Great for showing skeletons            |

Factories: `state(initial)`, `booleanState()`, `listState()`, `loadableState()`

---
## 4. ViewModel Patterns
You extend `SimpleViewModel` and override `start(args)`. Do all initial wiring there.

Primary helpers:
- `fetchInto(StateVar, showLoading?, task, label)`
- `fetchInto(LoadableVar, keepOldOnError=true, task, label)`
- `observeInto(StateVar, flow, showLoading?, label)`
- `observeInto(LoadableVar, flow, label)`
- `send(Effect)` (in a `main {}` block most commonly)

Recommended layout inside a VM:
``` kotlin
class ExampleVm(...) : SimpleViewModel() {
    // 1. State declarations
    val isLoading = booleanState()
    val item = state<Item?>(null)
    val related = loadableState<List<Related>>()

    // 2. start()
    override fun start(args: Bundle?) {
        val id = args?.getString(ScreenArgs.ITEM_ID) ?: return
        fetchInto(item, showLoading = isLoading, label = "item") { repo.getItem(id) }
        observeInto(related, repo.observeRelated(id), label = "related")
    }

    // 3. User actions
    fun refresh() = item.value?.id?.let { id ->
        fetchInto(item, showLoading = isLoading, label = "item-refresh") { repo.getItem(id, true) }
    }

    fun notify(msg: String) = main { send(Effect.Message(msg)) }
}
```

Label tips: Keep them short; they appear in debug logs. Prefer `entity`, `entity-refresh`, `reviews-page1`, etc.

---
## 5. Fragment Patterns
ALWAYS call `start(vm)` in `onViewCreated` (before collecting if possible). Use provided helpers:
``` kotlin
start(vm)
collect(vm.effects) { ... }
collect(stateVar.flow) { ... }
collectLoadable(loadableVar) { ... }
```
Rendering a Loadable:
``` kotlin
fun renderReviews(loadable: Loadable<List<Review>>) = when (loadable) {
    Loadable.Uninitialized, Loadable.Loading -> showSkeleton()
    is Loadable.Data -> showList(loadable.value)
    is Loadable.Error -> showError(loadable.error.message)
}
```

---
## 6. Loading & Error Strategy
| Scenario                         | Tool                               | UI                      | Error Path                                       |
|----------------------------------|------------------------------------|-------------------------|--------------------------------------------------|
| Single important resource        | `fetchInto(StateVar, showLoading)` | Central spinner         | Snackbar via Effect.Message                      |
| Secondary / list                 | `fetchInto(LoadableVar)`           | Per-section skeleton    | Loadable.Error + Effect.Message (if not keepOld) |
| Live stream (no skeleton wanted) | `observeInto(StateVar)`            | Optional global spinner | Snackbar via Effect.Message                      |
| Live stream w/ skeleton          | `observeInto(LoadableVar)`         | Section skeleton        | Loadable.Error                                   |

`keepOldOnError=true` (default) prevents jarring UI regressions; old Data remains visible.

---
## 7. Effects (One‑shot UI Events)
Pattern:
``` kotlin
main { send(Effect.Message("Saved")) }
collect(vm.effects) { eff -> /* when(eff) { ... } */ }
```
Add your own sealed subclasses (e.g., `Effect.OpenUrl(url)`) inside `SimplePrimitives.kt` if needed.

---
## 8. Choosing StateVar vs LoadableVar
| Ask Yourself                                          | If YES              | Use                                             |
|-------------------------------------------------------|---------------------|-------------------------------------------------|
| Do I need explicit Loading state on screen?           | For this piece only | LoadableVar                                     |
| Is it the root/primary entity for the screen?         | Usually             | StateVar (+ separate isLoading)                 |
| Will prior data remain on refresh during brief loads? | Desirable           | StateVar + isLoading OR LoadableVar (choose UX) |
| Need to show per-item shimmer skeleton?               | Yes                 | LoadableVar                                     |

Rule of thumb: Start simple with StateVar; upgrade to LoadableVar when you add a sectional skeleton or error placeholder.

---
## 9. Naming & Labeling Conventions
- Boolean flags: `isLoading`, `isSaving`, `isRefreshing`.
- Loadables: plural or descriptive (`reviews`, `stats`, `relatedItems`).
- Labels: `entity`, `entity-refresh`, `reviews`, `reviews-refresh`.
- Args keys live in `ScreenArgs` (centralized constants + helper accessors).

---
## 10. Concurrency & Cancellation
- All `fetchInto` work runs on a single coroutine launched via `io {}` or `viewModelScope.launch`.
- A second fetch with same target does NOT cancel the first automatically; if you need cancellation, add your own Job tracking or use a Flow + `observeInto`.
- `observeInto` uses `viewModelScope` and cancels automatically when VM clears.
- Avoid launching raw coroutines; prefer provided helpers for consistent error handling.

Optional custom cancellation pattern:
``` kotlin
private var refreshJob: Job? = null
fun refresh() {
    refreshJob?.cancel()
    refreshJob = fetchInto(item, showLoading = isLoading, label = "item-refresh") { repo.getItem(id, true) }
}
```

---
## 12. Troubleshooting / FAQ
Q: I see log: `WARN: io{} before start()`.
A: You called a helper before `start(vm)`. Ensure `start(vm)` runs in `onViewCreated` before triggers.

Q: UI never leaves Loading.
A: Did you collect the right thing (`collectLoadable(var)` vs `collect(var.flow)`)? Did your suspend function return? Check logs with label.

Q: Lost old data after error.
A: Use `keepOldOnError=true` (default) for `fetchInto(LoadableVar)` or switch to separate `StateVar` + spinner.

Q: Need a custom one-shot event type.
A: Add another subclass to `Effect` sealed class; handle in Fragment collector.

Q: Can I test this easily?
A: Inject fake use cases returning test Flows; assert state Flow emits expected sequence (Loadable.Loading → Data(...)). Effects can be collected from `effects` Flow.

---
## 13. Future Extensions (Ideas)
- Paging integration helper: `observePagedInto(loadable)`.
- Retry policy wrapper (exponential backoff on fetchInto).
- Unified analytics hook inside `SimpleViewModel`.
- DSL for building composite UI states (combining multiple flows).

---
## Summary
You now have a minimal set of predictable tools:
- State: `state()`, `loadableState()`, `booleanState()`
- Data movement: `fetchInto`, `observeInto`
- UI events: `effects` + `send(Effect)`
- Fragment glue: `start(vm)`, `collect()`, `collectLoadable()`

Keep ViewModels readable: declare state, wire in `start`, add user actions. Nothing more unless necessary.

Happy coding – keep it simple.


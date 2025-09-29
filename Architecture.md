# 📱 Project Architecture & File Structure

This document outlines the high-level architecture and file structure for our Android app.
We are following MVVM + Clean Architecture with Jetpack Navigation as our navigation framework.

# 🔑 Architecture Overview

- Single-Activity, Multi-Fragment App

  - MainActivity hosts the NavHostFragment.
  - All navigation between screens is handled by Jetpack Navigation.

- MVVM + Repository Pattern

  - Keeps UI, business logic, and data handling cleanly separated.

- Hilt Dependency Injection

  - Provides dependencies across layers.

- Firebase

  - Authentication → Firebase Auth
  - Database → Firestore

- Google Maps API

  - Integrated in the MapsFragment.

- Dynamic Theming
  - Managed via a ThemeManager class.

# 📂 Project File Structure

```
app/
├── data/       # Data layer
│   ├── auth/
│   │   ├── AuthRepository.kt
│   │   ├── FirebaseAuthDataSource.kt
│   │   └── AuthResult.kt
│   ├── user/
│   │   ├── UserRepository.kt
│   │   └── FirestoreUserDataSource.kt
│   ├── maps/
│   │   └── MapsRepository.kt
│   └── common/
│       └── Resource.kt     # Wrapper for success/error loading
│
├── domain/     # Business logic layer
│   ├── models/
│   │   ├── User.kt
│   │   └── Location.kt
│   ├── usecases/
│   │   ├── LoginUserUseCase.kt
│   │   ├── RegisterUserUseCase.kt
│   │   └── GetUserLocationUseCase.kt
│   └── utils/
│       └── ValidationUtils.kt
│
├── ui/     # Presentation layer
│   ├── auth/
│   │   ├── LoginFragment.kt
│   │   ├── RegisterFragment.kt
│   │   ├── AuthViewModel.kt
│   │   └── AuthNavigator.kt        # Handles auth navigation logic
│   ├── home/
│   │   ├── HomeFragment.kt
│   │   ├── HomeViewModel.kt
│   │   └── HomeAdapter.kt
│   ├── maps/
│   │   ├── MapsFragment.kt
│   │   └── MapsViewModel.kt
│   └── common/
│       ├── BaseFragment.kt
│       └── extensions/
│           └── ViewExtensions.kt
│
├── di/     # Hilt modules
│   ├── AppModule.kt
│   └── RepositoryModule.kt
│
├── navigation/
│   └── nav_graph.xml       # Jetpack Navigation graph
│
├── theme/
│   ├── ThemeManager.kt     # Handle theme switching
│   ├── themes.xml
│   ├── colors.xml
│   └── styles.xml
│
├── MainActivity.kt     # Hosts NavHostFragment
└── App.kt      # Application class (Hilt + setup)
```

🚪 Entry Points

App.kt (Application class)

Annotated with @HiltAndroidApp.

Initializes Firebase, theme manager, and global configs.

MainActivity.kt

Contains NavHostFragment tied to nav_graph.xml.

Handles top-level navigation and theme changes.

navigation/nav_graph.xml

Defines navigation flow:

LoginFragment → RegisterFragment → HomeFragment → MapsFragment.

Can enforce authentication guards (redirect to login if not signed in).

🔄 App Flow

Auth Flow

User sees LoginFragment or RegisterFragment.

On success → navigate to HomeFragment.

Home Screen

Displays user data from Firestore.

Acts as entry point to feature areas.

Maps Screen

MapsFragment integrates Google Maps API.

Theming

ThemeManager toggles between light, dark, or custom themes.

✅ Best Practices

MVVM + Repository keeps layers clean.

Single Activity + Jetpack Navigation avoids fragment transaction boilerplate.

Hilt manages dependencies.

Resource Wrappers handle success/error/loading states in UI.

Encapsulated Theme Manager makes theme swapping simple.

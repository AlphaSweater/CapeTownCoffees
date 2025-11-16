
![Logo](https://i.postimg.cc/L6MYhpN8/Logo-With-Title.png)


<h1 align="center">📊 Cape Town Coffees</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Made%20with-Kotlin-blue?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-UI%20Toolkit-blue?logo=android" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Firebase-Authentication-orange?stylelogo=firebase" alt="Firebase">
  <img src="https://img.shields.io/badge/Material%203-Design%20System-purple?&logo=material-design" alt="Material 3">
</p>

## 📝 Overview
Cape Town Coffees is a sleek Android application designed for coffee lovers across Cape Town.
The app helps users discover nearby coffee shops, explore reviews, share their own experiences, and save favourites for easy access later.

Whether you’re searching for a cozy spot to study, a trendy café to meet friends, or the perfect latte, Cape Town Coffees connects you to the city’s best brews.

## 📚 Table of Contents
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Security Features](#-security-features)
- [Installation](#-Installation)
- [Video Demonstration](#-video-demo)
- [Screenshots](#-screenshots)
- [Contributors](#-contributors)
- [Learning Outcomes](#-learning-outcomes)
- [References](#-references)


## ✨ Key Highlights
- 🗺️ **Location-based Discovery** - Find cafés near you with real-time distance and status
- ⭐ **Community Reviews** - Share your experiences and read others' opinions
- 💾 **Offline Access** - Download café details for when you're off the grid
- 🔐 **Secure Authentication** - Firebase Auth with Google Sign-In integration
- 🎨 **Modern UI** - Built with Jetpack Compose and Material Design 3

---

## 🚀 Features

<table style="border: none; border-collapse: collapse; width: 100%;">
<tr>
<td width="50%" valign="top" style="border: none;">

### 🔐 Authentication & Security
<ul style="margin-top: 0; padding-left: 20px;">
<li><strong>Secure Sign-up/Login</strong> with Firebase Authentication</li>
<li><strong>Google Single Sign-On</strong> for quick access</li>
<li><strong>Input Validation</strong> and security measures</li>
</ul>

### ☕ Coffee Shop Discovery
<ul style="margin-top: 0; padding-left: 20px;">
<li><strong>Nearby Cafés</strong> with distance, ratings, and open/closed status</li>
<li><strong>Advanced Search & Filters</strong> by name and radius</li>
<li><strong>Real-time Location</strong> services integration</li>
</ul>

</td>
<td width="50%" valign="top" style="border: none;">

### 💫 Personalization
<ul style="margin-top: 0; padding-left: 20px;">
<li><strong>Favorites System</strong> - Save your go-to spots</li>
<li><strong>Downloads</strong> - Access café info offline</li>
<li><strong>Detailed Café Views</strong> - Comprehensive information including ratings, descriptions, and hours</li>
</ul>

### 🛠️ Technical Excellence
<ul style="margin-top: 0; padding-left: 20px;">
<li><strong>Modern Architecture</strong> with clean separation of concerns</li>
<li><strong>Smooth Performance</strong> with Kotlin Coroutines and Flow</li>
<li><strong>Responsive UI</strong> that adapts to different screen sizes</li>
</ul>

</td>
</tr>
</table>


## 🔧 Tech Stack
### Core

- [Kotlin](https://kotlinlang.org/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material3 design](https://m3.material.io/) (UI components)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) (structured concurrency)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html)
- [Hilt](https://dagger.dev/hilt/) (Dependency Injection)

### Database
- [Firestore](https://console.firebase.google.com) (firebase no-sql database)
- [FireBase Authentication](https://console.firebase.google.com) (Google SSO sign in, Email & Password)

### Build & CI
- [Gradle KTS](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Gradle version catalogs](https://developer.android.com/build/migrate-to-catalogs) (dependencies versions)
- [GitHub Actions](https://github.com/AlphaSweater/BudgetBuddy-Project/actions)

### API
- [Google Places](https://developers.google.com/maps/documentation/places/android-sdk?hl=en) (for grabbing cafes based on location) (new places API through SDK)

## 🏗️ Architecture

<pre>
app/
├─ data/
│  ├─ common/        # networking/config helpers, result wrappers
│  ├─ mapper/        # DTO ↔ domain model mappers
│  ├─ model/         # DTOs from Places/Firebase responses
│  └─ repository/    # Repository implementations (Places, Firestore, Auth)
│
├─ di/               # Hilt modules (binds repositories, SDK providers)
│
├─ domain/
│  ├─ model/         # Pure domain models (UI-agnostic)
│  ├─ repository/    # Repository interfaces (ports)
│  └─ usecase/       # Interactors (single responsibility operations)
│
├─ ui/
│  ├─ auth/          # Sign-in/Sign-up screens, AuthViewModel
│  ├─ coffeeDetail/  # Detail screen (ratings, distance, open-hours)
│  ├─ common/        # BaseAdapter, View utils, composables/views
│  ├─ home/          # Home feed, sections, skeletons
│  ├─ profile/       # Profile & account management
│  ├─ review/        # Add/read reviews flows
│  ├─ savedLists/    # Favorites / downloads (offline)
│  ├─ search/        # Search UI, suggestions, filters
│  └─ settings/      # App settings (permissions, privacy)
│
├─ util/             # Cross-cutting utilities (e.g., LocationUtil)
│
├─ CapeTownCoffeesApp  # Application class (Hilt entry)
└─ MainActivity        # NavHost, edge-to-edge, theming bridge
</pre>

## ⚡ Installation

### Prerequisites

- **[Android Studio](https://developer.android.com/studio)** 
- **Android SDK** 26+ (API 36+ recommended)
- **Gradle** 8.0+

### 🛠️ Build & Run

1. Download and install Android Studio (Giraffe or newer) from the official site:
https://developer.android.com/studio

2. Open Android Studio on your computer.

3. Get the project files:

- Option 1 - Clone the Repository: Click "Get from Version Control" in the github repository and paste the repository link: https://github.com/AlphaSweater/CapeTownCoffees.git

- Option 2 - Download Zip File: If you downloaded a ZIP file, extract it, then click "Open" in Android Studio and select the extracted project folder.

- Wait for Gradle sync to complete. Android Studio will automatically download required dependencies.
(This may take a few minutes the first time.)

- Ensure you have the correct SDK versions installed:

- Minimum SDK version needed is 26 (36+ recommended)
(You can check or install SDKs via SDK Manager in Android Studio.)

4. Connect a device to run the app:

- Option 1: Plug in a physical Android device with USB debugging enabled.

- Option 2: Create and start an Android Emulator via Device Manager in Android Studio.

5. Click the green "Run" button at the top (or press Shift + F10) to build and launch the app.

6. The app will install and launch on your selected device, showing the BudgetBuddy application.


## 🎥 Video Demo

📺 **Watch the full walkthrough of Cape Town Coffees on YouTube:**
👉 [Click here to view](https://youtu.be/dnFBR1-XPvo?si=4kqEIKo2v5o-dgIs)
- 


## 📸 Screenshots

<div align="center">
  
| Login | Sign Up | Home Page | Cafe Details |
|-----------------|------------------------|-------------------|-----------------|
| <img src="https://i.postimg.cc/V6BhJT5p/Login-Cape-Town-Coffees.jpg" width="200"/> | <img src="https://i.postimg.cc/cLnXKjb5/Register-Cape-Town-Coffees.jpg" width="200"/> | <img src="https://i.postimg.cc/vmyL4m9c/Home-Page-Cape-Town-Coffees.jpg" width="200"/> | <img src="https://i.postimg.cc/FKzC4Gjz/cafe-Screen-Cape-Town-Coffees.jpg" width="200"/> |

| List Screen | Saved Cafes | Profile Page | Settings Page |
|-----------------|-------------------|--------------------|-----------|
| <img src="https://i.postimg.cc/d0g6KFQF/saved-Lists-Cape-Town-Coffees.jpg" width="200"/> | <img src="https://i.postimg.cc/g0Lys7Vq/list-Screen-Cape-Town-Coffees.jpg" width="200"/> | <img src="https://i.postimg.cc/0jZ5j3ds/Profile-Cape-Town-Coffees.jpg" width="200"/> | <img src="https://i.postimg.cc/1XWPsFss/Settings-Cape-Town-Coffees.jpg" width="200"/> |
</div>


## 👥 contributors
<a href="https://github.com/AlphaSweater/BudgetBuddy-Project/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=AlphaSweater/BudgetBuddy-Project" />
</a>

- Chad Fairlie ST10269509
- Dhiren Ruthenavelu ST10256859
- Kayla Ferreira ST10259527
- Nathan Teixeira ST10249266


## 📚 Learning Outcomes

This project provided hands-on experience with:

<table style="border: none; border-collapse: collapse; width: 100%;">
<tr>
<td width="50%" valign="top" style="border: none;">

### 🏗️ Architecture & Patterns
<ul style="margin-top: 0; padding-left: 20px;">
<li>Clean Architecture implementation</li>
<li>Repository Pattern for data abstraction</li>
<li>MVVM with state management</li>
</ul>

### 🔧 Technical Skills  
<ul style="margin-top: 0; padding-left: 20px;">
<li>Firestore Integration for real-time data syncing</li>
<li>Custom Mappers between database entities and domain models</li>
<li>State-driven UIs with Kotlin StateFlow</li>
<li>Coroutine Management for efficient threading</li>
</ul>

</td>
<td width="50%" valign="top" style="border: none;">

### 🎨 UI/UX Development
<ul style="margin-top: 0; padding-left: 20px;">
<li>Jetpack Compose for modern declarative UI</li>
<li>Material Design 3 implementation</li>
<li>Responsive Layouts for various screen sizes</li>
</ul>

### ⚡ Performance Optimization
<ul style="margin-top: 0; padding-left: 20px;">
<li>API Caching strategies to reduce network calls</li>
<li>Efficient Data Loading with pagination</li>
<li>Offline Capabilities with local storage</li>
</ul>

</td>
</tr>
</table>

## 📚 Learning Outcomes
This project provided hands-on experience with:

### 🏗️ Architecture & Patterns
- Clean Architecture implementation
- Repository Pattern for data abstraction
- MVVM with state management

### 🔧 Technical Skills
- Firestore Integration for real-time data syncing
- Custom Mappers between database entities and domain models
- State-driven UIs with Kotlin StateFlow
- Coroutine Management for efficient threading

### 🎨 UI/UX Development
- Jetpack Compose for modern declarative UI
- Material Design 3 implementation
- Responsive Layouts for various screen sizes

### ⚡ Performance Optimization
- API Caching strategies to reduce network calls
- Efficient Data Loading with pagination
- Offline Capabilities with local storage


## 📚 References

- https://www.youtube.com/watch?v=A_tPafV23DM&list=PLPgs125_L-X9H6J7x4beRU-AxJ4mXe5vX
- https://www.geeksforgeeks.org/kotlin-android-tutorial/
- https://www.geeksforgeeks.org/textview-in-kotlin/
- https://www.geeksforgeeks.org/scrollview-in-android/
- https://www.geeksforgeeks.org/horizontalscrollview-in-kotlin/
- https://www.geeksforgeeks.org/cardview-in-android-with-example/
- https://www.geeksforgeeks.org/switch-in-kotlin/
- https://www.geeksforgeeks.org/spinner-in-kotlin/
- https://www.youtube.com/watch?v=KwDSkSBDyfQ
- ChatGPT was used to help with the design and planning. As well as assisted with finding and fixing errors in the code.
- ChatGPT also helped with the forming of comments for the code.
##
![Cost Breakdown](https://i.postimg.cc/sx2d627M/wee.png)
##
![App Demo](https://i.postimg.cc/HWtLyjr6/kerchoo-kachow.gif)
##



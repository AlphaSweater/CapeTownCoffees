
![Logo](.png)


<h1 align="center">📊 Cape Town Coffees</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Made%20with-Kotlin-blue?logo=kotlin">
</p>

## 📝 Overview
Cape Town Coffees is a sleek Android application designed for coffee lovers across Cape Town.
The app helps users discover nearby coffee shops, explore reviews, share their own experiences, and save favourites for easy access later.

Whether you’re searching for a cozy spot to study, a trendy café to meet friends, or the perfect latte, Cape Town Coffees connects you to the city’s best brews.

## 📚 Table of Contents
- [Features](#-features)
- [Own Features](#own-features)
- [Security Features](#-security-features)
- [Prerequisites](#️-prerequisites)
- [How to Compile and Run](#-how-to-compile-and-run-the-application)
- [Video Demonstration](#-video-demo)
- [Tech Stack](#-tech-stack)
- [Screenshots](#-screenshots)
- [Contributors](#-contributors)
- [Learning Outcomes](#-learning-outcomes)
- [References](#-references)

## 🌟 Features

- SignUp and Login: Users can securely create an account and log in to access their personal budget data from anywhere. This ensures that all personal information is protected and personalized for each user.

- Google SignUp: Users can choose to sign up through Google's secure authentication.

- Find Nearby Coffee Shops: The app uses location services to show users nearby cafés with distance, ratings, and open/closed status.

- Advanced Search and Filters: The app allows users to search for coffee shops by name, and radius range from 1–50 km

- Favourites List: For ease of access users can save their favourite cafés for quick access later on.

- Detailed View: Users can see a detailed overview of different coffee shops, showing information such as the shop's rating, description, distance away, etc.

## 🔐 Security Features
- Firebase Authentication for secure login and identity management.
- Input validation and protection against improper input.

## 🛠️ Prerequisites 

1. **Ensure that you have Android Studio downloaded or you won't be able to run the project.**
Ensure that you have Android Studio installed on your computer.

2. If you do not have Android Studio, you can download it here:
👉 https://developer.android.com/studio

3. Make sure you have the following installed within Android Studio:
- Android SDK 26+
- Gradle 8.0+

4. Install an Android emulator or use a real device for testing.
## 🚀 How to Compile and Run The Application

1. Download and install Android Studio (Giraffe or newer) from the official site:
https://developer.android.com/studio

2. Open Android Studio on your computer.

3. Get the project files:

- Option 1 - Clone the Repository: Click "Get from Version Control" in the github repository and paste the repository link: https://github.com/AlphaSweater/CapeTownCoffees.git

- Option 2 - Download Zip File: If you downloaded a ZIP file, extract it, then click "Open" in Android Studio and select the extracted project folder.

- Wait for Gradle sync to complete. Android Studio will automatically download required dependencies.
(This may take a few minutes the first time.)

- Ensure you have the correct SDK versions installed:

- Minimum SDK version needed is 25 (35+ recommended)
(You can check or install SDKs via SDK Manager in Android Studio.)

4. Connect a device to run the app:

- Option 1: Plug in a physical Android device with USB debugging enabled.

- Option 2: Create and start an Android Emulator via Device Manager in Android Studio.

5. Click the green "Run" button at the top (or press Shift + F10) to build and launch the app.

6. The app will install and launch on your selected device, showing the BudgetBuddy application.


## 🎥 Video Demo

📺 **Watch the full walkthrough of BudgetBuddy on YouTube:**
👉 [Click here to view]()
- 


## 🔧 Tech Stack
### Core

- 100% [Kotlin](https://kotlinlang.org/)
- 100% [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material3 design](https://m3.material.io/) (UI components)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) (structured concurrency)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html)
- [Hilt](https://dagger.dev/hilt/) (DI)

### Database
- [Firestore](https://console.firebase.google.com) (firebase no-sql database)
- [Imgur API](https://console.firebase.google.com) (Image uploading and storage)

### Build & CI
- [Gradle KTS](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Gradle version catalogs](https://developer.android.com/build/migrate-to-catalogs) (dependencies versions)
- [GitHub Actions](https://github.com/AlphaSweater/BudgetBuddy-Project/actions)


## 📸 Screenshots

<div align="center">
  
| Landing Page | Sign Up | Login | Home page |
|-----------------|------------------------|-------------------|-----------------|
| <img src="https://i.postimg.cc/tT1634RM/Screenshot-20250609-152724-Budget-Buddy.jpg" width="200"/> | <img src="https://i.postimg.cc/BnLt7Vfj/Screenshot-20250609-152740-Budget-Buddy.jpg" width="200"/> | <img src="https://i.postimg.cc/nc8DbxTv/Screenshot-20250609-153217-Budget-Buddy.jpg" width="200"/> | <img src="https://i.postimg.cc/3Jp1HYzp/Screenshot-20250609-210010-Budget-Buddy.jpg" width="200"/> |

| Wallet Overview | Budget Overview | Category Reports | Transactions |
|-----------------|-------------------|--------------------|-----------|
| <img src="https://i.postimg.cc/52M7R67D/Screenshot-20250609-205611-Budget-Buddy.jpg" width="200"/> | <img src="https://i.postimg.cc/tCmRdGB1/Screenshot-20250609-205330-Budget-Buddy.jpg" width="200"/> | <img src="https://i.postimg.cc/B68RGRxz/Screenshot-20250609-205340-Budget-Buddy.jpg" width="200"/> | <img src="https://i.postimg.cc/hts3HK7s/Screenshot-20250609-205343-Budget-Buddy.jpg" width="200"/> |
</div>


## 👥 contributors


- Chad Fairlie ST10269509
- Dhiren Ruthenavelu ST10256859
- Kayla Ferreira ST10259527
- Nathan Teixeira ST10249266

## 🧠 Learning Outcomes

- Advanced use of Jetpack Compose for UI
- Integration of Firestore for real-time data syncing
- Creating custom mappers between database entities and domain models
- Building responsive layouts and implementing state-driven UIs with Kotlin StateFlow
- Using and managing coroutines for efficient thread utilization

## 📚 References

- https://www.youtube.com/watch?v=A_tPafV23DM&list=PLPgs125_L-X9H6J7x4beRU-AxJ4mXe5vX
- https://www.geeksforgeeks.org/kotlin-android-tutorial/
- https://www.geeksforgeeks.org/textview-in-kotlin/
- https://www.geeksforgeeks.org/scrollview-in-android/
- https://www.geeksforgeeks.org/horizontalscrollview-in-kotlin/
- https://www.geeksforgeeks.org/cardview-in-android-with-example/
- https://www.geeksforgeeks.org/switch-in-kotlin/
- https://www.geeksforgeeks.org/spinner-in-kotlin/
- ChatGPT was used to help with the design and planning. As well as assisted with finding and fixing errors in the code.
- ChatGPT also helped with the forming of comments for the code.

##
![App Demo]()
##



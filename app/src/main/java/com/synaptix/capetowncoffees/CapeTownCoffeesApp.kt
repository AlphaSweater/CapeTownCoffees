//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees

import android.app.Application
import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.synaptix.capetowncoffees.notifications.NotificationHelper
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager

@HiltAndroidApp
class CapeTownCoffeesApp : Application() {

    @Inject
    lateinit var offlineModeManager: OfflineModeManager

    private var reactionsListener: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            // Plant the custom tree
            Timber.plant(Timber.DebugTree())
        }

        CoffeeTimeUtils.defaultPrefsProvider = { CoffeeTimeUtils.DisplayPrefs.from(applicationContext) }

        // Apply saved theme choice (defaults to SYSTEM)
        ThemeManager.applySavedTheme(this)

        // Start connectivity monitoring for app-wide use
        try {
            offlineModeManager.start()
            Timber.d("OfflineModeManager started from Application")
        } catch (t: Throwable) {
            Timber.w(t, "Failed to start OfflineModeManager")
        }

        // Fetch and persist initial FCM token (if user logged in)
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            try {
                val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
                CoroutineScope(Dispatchers.IO).launch {
                    Timber.d("FCM initial token: $token")
                    runCatching { entryPoint.userRepository().updateFcmToken(token) }
                        .onSuccess { Timber.d("Saved FCM token on app start") }
                        .onFailure { Timber.w(it, "Failed to update FCM token on app start (likely no user yet)") }
                }
            } catch (t: Throwable) {
                Timber.w(t, "Hilt entry point not available yet for token update")
            }
        }

        // Also reactively save token once a user logs in
        try {
            val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
            CoroutineScope(Dispatchers.IO).launch {
                entryPoint.userRepository().observeAuthState().collectLatest { loggedIn ->
                    if (loggedIn) {
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            Timber.d("FCM token on login: $token")
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching { entryPoint.userRepository().updateFcmToken(token) }
                                    .onSuccess { Timber.d("Saved FCM token after login") }
                                    .onFailure { Timber.w(it, "Failed to save FCM token after login") }
                            }
                        }.addOnFailureListener { e ->
                            Timber.w(e, "Failed to get FCM token after login")
                        }

                        val currentUserId = entryPoint.userRepository().getCurrentUserId()
                        if (currentUserId != null) {
                            startReviewLikeListener(currentUserId)
                        } else {
                            stopReviewLikeListener()
                        }
                    } else {
                        stopReviewLikeListener()
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Auth observer setup failed")
        }
    }

    override fun onTerminate() {
        try {
            offlineModeManager.stop()
            stopReviewLikeListener()
            Timber.d("OfflineModeManager stopped from Application")
        } catch (t: Throwable) {
            Timber.w(t, "Failed to stop OfflineModeManager")
        }
        super.onTerminate()
    }

    private fun startReviewLikeListener(authorUserId: String) {
        reactionsListener?.remove()
        Timber.d("Starting review like listener for authorUserId=%s", authorUserId)

        val prefs = getSharedPreferences("review_like_notifications", Context.MODE_PRIVATE)
        val lastSeenKey = "last_seen_like_ts_" + authorUserId
        if (!prefs.contains(lastSeenKey)) {
            val nowSeconds = System.currentTimeMillis() / 1000L
            prefs.edit().putLong(lastSeenKey, nowSeconds).apply()
            Timber.d("Review like: initializing lastSeen for %s to %d", authorUserId, nowSeconds)
        }

        reactionsListener = FirebaseFirestore.getInstance()
            .collectionGroup("reactions")
            .whereEqualTo("reviewAuthorId", authorUserId)
            .whereEqualTo("type", "like")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Timber.w(e, "Failed to listen for review like reactions")
                    return@addSnapshotListener
                }
                if (snapshots == null) {
                    return@addSnapshotListener
                }

                val lastSeen = prefs.getLong(lastSeenKey, 0L)
                var maxSeen = lastSeen

                for (change in snapshots.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        Timber.d(
                            "Review like change: type=ADDED path=%s data=%s",
                            change.document.reference.path,
                            change.document.data
                        )

                        val updatedAt = change.document.getTimestamp("updatedAt")
                        val tsSeconds = updatedAt?.seconds ?: 0L
                        if (tsSeconds != 0L && tsSeconds <= lastSeen) {
                            Timber.d(
                                "Review like: skipping old reaction ts=%d lastSeen=%d path=%s",
                                tsSeconds,
                                lastSeen,
                                change.document.reference.path
                            )
                            continue
                        }
                        if (tsSeconds > maxSeen) {
                            maxSeen = tsSeconds
                        }

                        val likerId = change.document.getString("userId")
                        if (likerId.isNullOrBlank()) {
                            Timber.d("Review like: missing likerId, showing generic notification")
                            NotificationHelper.showSimple(
                                applicationContext,
                                "Your comment was liked",
                                "Someone liked your comment."
                            )
                            continue
                        }

                        // Skip self-likes (when author likes their own comment)
                        if (likerId == authorUserId) {
                            Timber.d("Review like: skipping self-like for userId=%s", likerId)
                            continue
                        }

                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(likerId)
                            .get()
                            .addOnSuccessListener { snap ->
                                val likerName = snap.getString("fullName")
                                    ?: snap.getString("email")
                                    ?: "Someone"
                                Timber.d(
                                    "Review like: resolved likerId=%s to name=%s (exists=%s)",
                                    likerId,
                                    likerName,
                                    snap.exists()
                                )
                                NotificationHelper.showSimple(
                                    applicationContext,
                                    "Your comment was liked",
                                    "$likerName liked your comment."
                                )
                            }
                            .addOnFailureListener { ex ->
                                Timber.w(ex, "Review like: failed to load liker user doc for userId=%s", likerId)
                                NotificationHelper.showSimple(
                                    applicationContext,
                                    "Your comment was liked",
                                    "Someone liked your comment."
                                )
                            }
                    }
                }

                if (maxSeen > lastSeen) {
                    prefs.edit().putLong(lastSeenKey, maxSeen).apply()
                    Timber.d(
                        "Review like: updated lastSeen for %s from %d to %d",
                        authorUserId,
                        lastSeen,
                        maxSeen
                    )
                }
            }
    }

    private fun stopReviewLikeListener() {
        Timber.d("Stopping review like listener")
        reactionsListener?.remove()
        reactionsListener = null
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun userRepository(): ICoffeeUserRepository
}

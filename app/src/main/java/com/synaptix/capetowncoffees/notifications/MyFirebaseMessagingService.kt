package com.synaptix.capetowncoffees.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: ICoffeeUserRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token for the logged-in user
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { userRepository.updateFcmToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Cape Town Coffees"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new update"

        NotificationHelper.showSimple(applicationContext, title, body)
    }
}

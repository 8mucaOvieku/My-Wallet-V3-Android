package piuk.blockchain.android.data.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.blockchain.deeplinking.navigation.DeeplinkRedirector
import com.blockchain.koin.scopedInject
import com.blockchain.lifecycle.AppState
import com.blockchain.lifecycle.LifecycleObservable
import com.blockchain.notifications.NotificationTokenManager
import com.blockchain.notifications.NotificationsUtil
import com.blockchain.notifications.NotificationsUtil.Companion.ID_BACKGROUND_NOTIFICATION
import com.blockchain.notifications.NotificationsUtil.Companion.ID_BACKGROUND_NOTIFICATION_2FA
import com.blockchain.notifications.NotificationsUtil.Companion.ID_FOREGROUND_NOTIFICATION
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.models.NotificationPayload
import com.blockchain.preferences.WalletStatus
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.serialization.encodeToString
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.auth.newlogin.SecureChannelManager
import piuk.blockchain.android.ui.home.MainActivity
import piuk.blockchain.android.ui.launcher.LauncherActivity
import piuk.blockchain.androidcore.data.rxjava.RxBus
import timber.log.Timber

class FcmCallbackService : FirebaseMessagingService() {

    private val notificationManager: NotificationManager by inject()
    private val notificationTokenManager: NotificationTokenManager by scopedInject()
    private val rxBus: RxBus by inject()
    private val analytics: Analytics by inject()
    private val walletPrefs: WalletStatus by inject()
    private val secureChannelManager: SecureChannelManager by scopedInject()
    private val compositeDisposable = CompositeDisposable()
    private val lifecycleObservable: LifecycleObservable by inject()
    private var isAppOnForegrounded = true
    private val deeplinkRedirector: DeeplinkRedirector by scopedInject()

    init {
        compositeDisposable += lifecycleObservable.onStateUpdated.subscribe {
            isAppOnForegrounded = it == AppState.FOREGROUNDED
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("Message data payload: %s", remoteMessage.data)

            // Parse data, emit events
            val payload = NotificationPayload(remoteMessage.data)
            rxBus.emitEvent(NotificationPayload::class.java, payload)
            sendNotification(
                payload = payload,
                foreground = true
            )
        } else {
            // If there is no data field, provide this default behaviour
            NotificationsUtil(
                context = applicationContext,
                notificationManager = notificationManager,
                analytics = analytics
            ).triggerNotification(
                title = remoteMessage.notification?.title ?: "",
                marquee = remoteMessage.notification?.title ?: "",
                text = remoteMessage.notification?.body ?: "",
                // Don't want to launch an activity
                pendingIntent = PendingIntent.getActivity(
                    applicationContext, 0, Intent(), PendingIntent.FLAG_UPDATE_CURRENT
                ),
                id = ID_BACKGROUND_NOTIFICATION_2FA,
                appName = R.string.app_name,
                colorRes = R.color.primary_navy_medium
            )
        }
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        notificationTokenManager.storeAndUpdateToken(newToken)
    }

    /**
     * Redirects the user to the [LauncherActivity] if [foreground] is set to true, otherwise to
     * the [MainActivity] unless it is a new device login, in which case [MainActivity] is
     * going to load the [piuk.blockchain.android.ui.auth.newlogin.AuthNewLoginSheet] .
     */
    private fun sendNotification(payload: NotificationPayload, foreground: Boolean) {
        compositeDisposable += createIntentForNotification(payload, foreground)
            .subscribeOn(Schedulers.io())
            .subscribeBy(
                onSuccess = { notifyIntent ->
                    val intent = PendingIntent.getActivity(
                        applicationContext,
                        0,
                        notifyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val notificationId = if (foreground) ID_FOREGROUND_NOTIFICATION else ID_BACKGROUND_NOTIFICATION

                    if (isSecureChannelMessage(payload)) {
                        if (foreground) {
                            startActivity(notifyIntent)
                        } else {
                            NotificationsUtil(
                                context = applicationContext,
                                notificationManager = notificationManager,
                                analytics = analytics
                            ).triggerNotification(
                                title = getString(R.string.secure_channel_notif_title),
                                marquee = getString(R.string.secure_channel_notif_title),
                                text = getString(R.string.secure_channel_notif_summary),
                                pendingIntent = intent,
                                id = notificationId,
                                appName = R.string.app_name,
                                colorRes = R.color.primary_navy_medium
                            )
                        }
                    }

                    else if (payload.deeplinkURL != null) {
                        deeplinkRedirector.processDeeplinkURL(Uri.parse(payload.deeplinkURL), payload)
                    }

                    else {
                        triggerHeadsUpNotification(
                            payload,
                            intent,
                            notificationId
                        )
                    }
                },
                onError = {}
            )
    }

    private fun createIntentForNotification(payload: NotificationPayload, foreground: Boolean): Maybe<Intent> {
        return when {
            isSecureChannelMessage(payload) -> createSecureChannelIntent(payload.payload, foreground)
            foreground -> Maybe.just(
                MainActivity.newIntent(
                    context = applicationContext,
                    intentFromNotification = true
                )
            )
            else -> Maybe.just(
                LauncherActivity.newInstance(context = applicationContext, intentFromNotification = true)
            )
        }
    }

    private fun isSecureChannelMessage(payload: NotificationPayload) =
        payload.type == NotificationPayload.NotificationType.SECURE_CHANNEL_MESSAGE

    private fun isDeeplinkMessage(payload: NotificationPayload) =
        payload.type == NotificationPayload.NotificationType.DEEPLINK_CHANNEL_MESSAGE

    private fun createSecureChannelIntent(payload: MutableMap<String, String>, foreground: Boolean): Maybe<Intent> {
        val pubKeyHash = payload[NotificationPayload.PUB_KEY_HASH]
            ?: return Maybe.empty()
        val messageRawEncrypted = payload[NotificationPayload.DATA_MESSAGE]
            ?: return Maybe.empty()

        val message = secureChannelManager.decryptMessage(pubKeyHash, messageRawEncrypted)
            ?: return Maybe.empty()

        return Maybe.just(
            MainActivity.newIntent(
                context = applicationContext,
                launchAuthFlow = true,
                pubKeyHash = pubKeyHash,
                message = SecureChannelManager.jsonBuilder.encodeToString(message),
                originIp = payload[NotificationPayload.ORIGIN_IP],
                originLocation = payload[NotificationPayload.ORIGIN_COUNTRY],
                originBrowser = payload[NotificationPayload.ORIGIN_BROWSER],
                forcePin = !foreground,
                shouldBeNewTask = foreground
            )
        )
    }

    /**
     * Triggers a notification with the "Heads Up" feature on >21, with the "beep" sound and a short
     * vibration enabled.
     *
     * @param payload A [NotificationPayload] object from the Notification Service
     * @param pendingIntent The [PendingIntent] that you wish to be called when the
     * notification is selected
     * @param notificationId The ID of the notification
     */
    private fun triggerHeadsUpNotification(
        payload: NotificationPayload,
        pendingIntent: PendingIntent,
        notificationId: Int
    ) {

        NotificationsUtil(
            context = applicationContext,
            notificationManager = notificationManager,
            analytics = analytics
        ).triggerNotification(
            title = payload.title ?: "",
            marquee = payload.title ?: "",
            text = payload.body ?: "",
            pendingIntent = pendingIntent,
            id = notificationId,
            appName = R.string.app_name,
            colorRes = R.color.primary_navy_medium
        )
    }
}

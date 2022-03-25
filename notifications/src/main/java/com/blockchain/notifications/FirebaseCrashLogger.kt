package com.blockchain.notifications

import android.content.Context
import com.blockchain.logging.CrashLogger
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class FirebaseCrashLogger : CrashLogger {

    private val firebaseInstance
        get() = FirebaseCrashlytics.getInstance()

    override val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    override fun init(ctx: Any) {
        if (ctx is Context) {
            if (BuildConfig.USE_CRASHLYTICS) {
                // Init crash reporting
                firebaseInstance.setCrashlyticsCollectionEnabled(true)
            } else {
                firebaseInstance.setCrashlyticsCollectionEnabled(false)
            }
        } else {
            throw IllegalStateException("Unable to init Crashlytics. No context provided")
        }
    }

    override fun logEvent(msg: String) {
        Timber.e(msg)
        if (BuildConfig.USE_CRASHLYTICS) {
            firebaseInstance.log(msg)
        }
    }

    override fun logState(name: String, data: String) {
        if (BuildConfig.USE_CRASHLYTICS) {
            firebaseInstance.setCustomKey(name, data)
        }
    }

    override fun onlineState(isOnline: Boolean) {
        if (BuildConfig.USE_CRASHLYTICS) {
            firebaseInstance.setCustomKey(KEY_ONLINE_STATE, isOnline)
        }
    }

    override fun userLanguageLocale(locale: String) {
        if (BuildConfig.USE_CRASHLYTICS) {
            firebaseInstance.setCustomKey(KEY_LOCALE_LANGUAGE, locale)
        }
    }

    override fun logException(throwable: Throwable, logMsg: String) {
        if (BuildConfig.USE_CRASHLYTICS) {
            firebaseInstance.recordException(throwable)
        }
        Timber.e(throwable, logMsg)
    }

    override fun logAndRethrowException(throwable: Throwable, logMsg: String) {
        logException(throwable, logMsg)
        throw throwable
    }

    companion object {
        const val KEY_ONLINE_STATE = "online status"
        const val KEY_LOCALE_LANGUAGE = "user language"
    }
}

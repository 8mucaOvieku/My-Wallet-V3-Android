package piuk.blockchain.android.ui.login.auth

import com.blockchain.featureflag.FeatureFlag
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcore.utils.PrefsUtil

class LoginAuthInteractor(
    private val authDataManager: AuthDataManager,
    private val payloadDataManager: PayloadDataManager,
    private val prefs: PersistentPrefs,
    private val accountUnificationFF: FeatureFlag
) {
    fun getAuthInfo(json: String): Single<LoginAuthInfo> {
        return Single.fromCallable {
            val jsonBuilder = Json {
                ignoreUnknownKeys = true
            }
            try {
                jsonBuilder.decodeFromString<LoginAuthInfo.ExtendedAccountInfo>(json)
            } catch (throwable: Throwable) {
                jsonBuilder.decodeFromString<LoginAuthInfo.SimpleAccountInfo>(json)
            }
        }
    }

    fun getSessionId() = prefs.sessionId

    fun clearSessionId() = prefs.clearSessionId()

    fun authorizeApproval(authToken: String, sessionId: String): Single<JsonObject> {
        return authDataManager.authorizeSessionObject(authToken, sessionId)
    }

    fun getPayload(guid: String, sessionId: String): Single<JsonObject> =
        authDataManager.getEncryptedPayloadObject(guid, sessionId, resend2FASms = false)

    fun verifyPassword(payload: String, password: String): Completable {
        return payloadDataManager.initializeFromPayload(payload, password)
            .doOnComplete {
                payloadDataManager.wallet?.let { wallet ->
                    prefs.apply {
                        sharedKey = wallet.sharedKey
                        walletGuid = wallet.guid
                        emailVerified = true
                        pinId = ""
                    }
                }
            }
    }

    fun getRemaining2FaRetries() = prefs.resendSmsRetries

    private fun consume2FaRetry() = prefs.setResendSmsRetries(prefs.resendSmsRetries - 1)

    fun reset2FaRetries(): Completable =
        Completable.fromCallable { prefs.setResendSmsRetries(PrefsUtil.MAX_ALLOWED_RETRIES) }

    fun requestNew2FaCode(guid: String, sessionId: String): Single<JsonObject> =
        if (getRemaining2FaRetries() > 0) {
            consume2FaRetry()
            authDataManager.getEncryptedPayloadObject(guid, sessionId, resend2FASms = true)
        } else {
            Single.error(LoginAuthModel.TimeLockException())
        }

    fun submitCode(
        guid: String,
        sessionId: String,
        code: String,
        payloadJson: String
    ): Single<ResponseBody> {
        return Single.fromObservable(
            authDataManager.submitTwoFactorCode(sessionId, guid, code).map { response ->
                val responseObject = JSONObject(payloadJson).apply {
                    put(LoginAuthIntents.PAYLOAD, response.string())
                }
                responseObject.toString()
                    .toResponseBody("application/json".toMediaTypeOrNull())
            }
        )
    }

    fun updateMobileSetup(isMobileSetup: Boolean, deviceType: Int): Single<Boolean> =
        authDataManager.updateMobileSetup(
            guid = prefs.walletGuid,
            sharedKey = prefs.sharedKey,
            isMobileSetup = isMobileSetup,
            deviceType = deviceType
        ).toSingle { accountUnificationFF.isEnabled }
}

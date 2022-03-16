package piuk.blockchain.androidcore.data.auth

import com.blockchain.api.ApiException
import info.blockchain.wallet.api.WalletApi
import info.blockchain.wallet.api.data.Status
import info.blockchain.wallet.api.data.WalletOptions
import info.blockchain.wallet.exceptions.InvalidCredentialsException
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import okhttp3.ResponseBody
import retrofit2.Response

class WalletAuthService(private val walletApi: WalletApi) {

    /**
     * Returns a [WalletOptions] object, which amongst other things contains information
     * needed for determining buy/sell regions.
     */
    fun getWalletOptions(): Observable<WalletOptions> =
        walletApi.walletOptions

    /**
     * Get encrypted copy of Payload
     *
     * @param guid A user's GUID
     * @param sessionId The session ID, retrieved from [.getSessionId]
     * @return [<] wrapping an encrypted Payload
     */
    fun getEncryptedPayload(
        guid: String,
        sessionId: String,
        resend2FASms: Boolean
    ): Observable<Response<ResponseBody>> = walletApi.fetchEncryptedPayload(guid, sessionId, resend2FASms)

    /**
     * Posts a user's 2FA code to the server. Will return an encrypted copy of the Payload if
     * successful.
     *
     * @param sessionId The current session ID
     * @param guid The user's GUID
     * @param twoFactorCode The user's generated (or received) 2FA code
     * @return An [Observable] which may contain an encrypted Payload
     */
    fun submitTwoFactorCode(
        sessionId: String,
        guid: String,
        twoFactorCode: String
    ): Observable<ResponseBody> = walletApi.submitTwoFactorCode(sessionId, guid, twoFactorCode)

    /**
     * Gets a session ID from the server
     *
     * @param guid A user's GUID
     * @return An [Observable] wrapping a [String] response
     */
    fun getSessionId(guid: String): Observable<String> = walletApi.getSessionId(guid)
        .map { responseBodyResponse ->
            val headers = responseBodyResponse.headers().get("Set-Cookie")
            if (headers != null) {
                val fields = headers.split(";\\s*".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                for (field in fields) {
                    if (field.startsWith("SID=")) {
                        return@map field.substring(4)
                    }
                }
            } else {
                throw ApiException("Session ID not found in headers")
            }
            ""
        }

    /**
     * Get the encryption password for pairing
     *
     * @param guid A user's GUID
     * @return An [Observable] wrapping the pairing encryption password
     */
    fun getPairingEncryptionPassword(guid: String): Observable<ResponseBody> =
        walletApi.fetchPairingEncryptionPassword(guid)

    /**
     * Sends the access key to the server
     *
     * @param key The PIN identifier
     * @param value The value, randomly generated
     * @param pin The user's PIN
     * @return An [Observable] where the boolean represents success
     */
    fun setAccessKey(
        key: String,
        value: String,
        pin: String
    ): Observable<Response<Status>> =
        walletApi.setAccess(key, value, pin)

    /**
     * Validates a user's PIN with the server
     *
     * @param key The PIN identifier
     * @param pin The user's PIN
     * @return A [Response] which may or may not contain the field "success"
     */
    fun validateAccess(key: String, pin: String): Observable<Response<Status>> =
        walletApi.validateAccess(key, pin)
            .doOnError {
                if (it.message?.contains("Incorrect PIN") == true) {
                    throw InvalidCredentialsException(
                        "Incorrect PIN"
                    )
                }
            }

    /**
     * Create a session ID for the given email for authorization
     *
     * @param email The user's email
     * @return A [Single] wrapping the result
     */
    fun createSessionId(email: String): Single<ResponseBody> =
        walletApi.createSessionId(email)

    /**
     * Authorize the request for the given session ID
     *
     * @param authToken The token required for auth from the email
     * @param sessionId The current session ID
     * @return A [Single] wrapping the result
     */
    fun authorizeSession(authToken: String, sessionId: String): Single<Response<ResponseBody>> =
        walletApi.authorizeSession(authToken, sessionId)

    /**
     * Update the account model fields for mobile setup
     *
     * @param guid The user's GUID
     * @param sharedKey The shared key of the specified GUID
     * @param isMobileSetup has mobile device linked
     * @param deviceType the type of the linked device: 1 - iOS, 2 - Android
     * @return A [Single] wrapping the result
     */
    fun updateMobileSetup(
        guid: String,
        sharedKey: String,
        isMobileSetup: Boolean,
        deviceType: Int
    ): Completable = walletApi.updateMobileSetup(guid, sharedKey, isMobileSetup, deviceType)

    /**
     * Update the mnemonic backup date (calculated on the backend)
     *
     * @param guid The user's GUID
     * @param sharedKey The shared key of the specified GUID
     * @return A [Completable] wrapping the result
     */
    fun updateMnemonicBackup(guid: String, sharedKey: String): Completable =
        Completable.fromSingle(walletApi.updateMnemonicBackup(guid, sharedKey))

    /**
     * Verify that the cloud backup has been completed
     *
     * @param guid The user's GUID
     * @param sharedKey The shared key of the specified GUID
     * @param hasCloudBackup has cloud backup
     * @param deviceType the type of the linked device: 1 - iOS, 2 - Android
     * @return A [Single] wrapping the result
     */
    fun verifyCloudBackup(
        guid: String,
        sharedKey: String,
        hasCloudBackup: Boolean,
        deviceType: Int
    ): Single<ResponseBody> =
        walletApi.verifyCloudBackup(guid, sharedKey, hasCloudBackup, deviceType)

    fun getDeeplinkPayload(
        sessionId: String
    ): Single<ResponseBody> = walletApi.getDeeplinkPayload(sessionId)

    fun updateLoginApprovalStatus(
        sessionId: String,
        payload: String,
        confirmDevice: Boolean
    ): Completable = walletApi.updateLoginApprovalStatus(sessionId, payload, confirmDevice)
}

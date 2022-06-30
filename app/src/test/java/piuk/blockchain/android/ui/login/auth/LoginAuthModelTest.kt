package piuk.blockchain.android.ui.login.auth

import com.blockchain.android.testutils.rxInit
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.featureflag.FeatureFlag
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.wallet.exceptions.DecryptionException
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import piuk.blockchain.androidcore.utils.extensions.AuthRequiredException

class LoginAuthModelTest {
    private lateinit var model: LoginAuthModel

    private val environmentConfig: EnvironmentConfig = mock {
        on { isRunningInDebugMode() }.thenReturn(false)
    }

    private val interactor: LoginAuthInteractor = mock {
        on { reset2FaRetries() }.thenReturn(Completable.complete())
    }
    private val unifiedSignInFlag: FeatureFlag = mock()

    @get:Rule
    val rx = rxInit {
        ioTrampoline()
        computationTrampoline()
    }

    @Before
    fun setUp() {
        model = LoginAuthModel(
            initialState = LoginAuthState(),
            mainScheduler = Schedulers.io(),
            environmentConfig = environmentConfig,
            remoteLogger = mock(),
            interactor = interactor
        )
    }

    @Test
    fun `get session ID and payload`() {
        // Arrange
        val sessionId = "SESSION_ID"
        whenever(interactor.getAuthInfo(INPUT_JSON)).thenReturn(
            Single.just(testAuthInfo)
        )
        whenever(interactor.getSessionId()).thenReturn(sessionId)
        whenever(interactor.authorizeApproval(AUTH_TOKEN, sessionId)).thenReturn(
            Single.just(mock())
        )
        val payload: kotlinx.serialization.json.JsonObject = mock()
        whenever(interactor.getPayload(GUID, sessionId)).thenReturn(
            Single.just(payload)
        )
        whenever(interactor.getRemaining2FaRetries()).thenReturn(0)

        val testState = model.state.test()
        model.process(LoginAuthIntents.InitLoginAuthInfo(INPUT_JSON))

        // Assert
        testState.assertValueAt(0, LoginAuthState())
        testState.assertValueAt(1, LoginAuthState(authStatus = AuthStatus.InitAuthInfo))
        testState.assertValueAt(
            2,
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.GetSessionId,
                authInfoForAnalytics = testAuthInfo
            )
        )
        testState.assertValueAt(
            3,
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                sessionId = sessionId,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.AuthorizeApproval,
                authInfoForAnalytics = testAuthInfo
            )
        )
        testState.assertValueAt(
            4,
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                sessionId = sessionId,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.GetPayload,
                authInfoForAnalytics = testAuthInfo
            )
        )
    }

    @Test
    fun `auth fail to get payload`() {
        // Arrange
        val sessionId = "SESSION_ID"
        val twoFaState = TwoFaCodeState.TwoFaRemainingTries(remainingRetries = 3)

        whenever(interactor.getAuthInfo(INPUT_JSON)).thenReturn(
            Single.just(testAuthInfo)
        )
        whenever(interactor.getSessionId()).thenReturn(sessionId)
        whenever(interactor.authorizeApproval(AUTH_TOKEN, sessionId)).thenReturn(
            Single.just(mock())
        )
        whenever(interactor.getPayload(GUID, sessionId)).thenReturn(Single.error(AuthRequiredException()))
        whenever(interactor.reset2FaRetries()).thenReturn(Completable.complete())
        whenever(interactor.getRemaining2FaRetries()).thenReturn(3)

        val testState = model.state.test()
        model.process(LoginAuthIntents.InitLoginAuthInfo(INPUT_JSON))

        // Assert
        testState.assertValues(
            LoginAuthState(),
            LoginAuthState(authStatus = AuthStatus.InitAuthInfo),
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.GetSessionId,
                authInfoForAnalytics = testAuthInfo
            ),
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                sessionId = sessionId,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.AuthorizeApproval,
                authInfoForAnalytics = testAuthInfo
            ),
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                sessionId = sessionId,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.GetPayload,
                authInfoForAnalytics = testAuthInfo
            ),
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                sessionId = sessionId,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.AuthRequired,
                authInfoForAnalytics = testAuthInfo
            ),
            LoginAuthState(
                guid = GUID,
                userId = USER_ID,
                email = EMAIL,
                recoveryToken = RECOVERY_TOKEN,
                sessionId = sessionId,
                authToken = AUTH_TOKEN,
                authStatus = AuthStatus.AuthRequired,
                twoFaState = twoFaState,
                authInfoForAnalytics = testAuthInfo
            )
        )
    }

    @Test
    fun `verify password without 2fa`() {
        // Arrange
        val password = "password"
        val isMobileSetup = true
        val deviceType = DEVICE_TYPE_ANDROID
        whenever(interactor.verifyPassword(anyString(), anyString())).thenReturn(
            Completable.complete()
        )
        whenever(interactor.updateMobileSetup(isMobileSetup, deviceType)).thenReturn(
            Single.just(true)
        )
        whenever(unifiedSignInFlag.enabled).thenReturn(Single.just(false))

        val testState = model.state.test()
        model.process(LoginAuthIntents.VerifyPassword(password))

        // Assert
        testState.assertValues(
            LoginAuthState(),
            LoginAuthState(
                authStatus = AuthStatus.VerifyPassword,
                password = password
            ),
            LoginAuthState(
                authStatus = AuthStatus.UpdateMobileSetup,
                password = password,
                isMobileSetup = isMobileSetup,
                deviceType = deviceType
            ),
            LoginAuthState(
                authStatus = AuthStatus.Complete,
                password = password,
                isMobileSetup = isMobileSetup,
                deviceType = deviceType
            )
        )
    }

    @Test
    fun `fail to verify password`() {
        // Arrange
        val password = "password"
        whenever(interactor.verifyPassword(anyString(), anyString())).thenReturn(
            Completable.error(
                DecryptionException()
            )
        )

        val testState = model.state.test()
        model.process(LoginAuthIntents.VerifyPassword(password))

        // Assert
        testState.assertValues(
            LoginAuthState(),
            LoginAuthState(
                authStatus = AuthStatus.VerifyPassword,
                password = password
            ),
            LoginAuthState(
                authStatus = AuthStatus.InvalidPassword,
                password = password
            )
        )
    }

    @Test
    fun `verify password with 2fa`() {
        // Arrange
        val password = "password"
        val twoFACode = "code"
        val isMobileSetup = true
        val deviceType = DEVICE_TYPE_ANDROID
        whenever(interactor.submitCode(anyString(), anyString(), anyString(), anyString())).thenReturn(
            Single.just(TWO_FA_PAYLOAD.toResponseBody((JSON_HEADER).toMediaTypeOrNull()))
        )
        whenever(interactor.verifyPassword(anyString(), anyString())).thenReturn(
            Completable.complete()
        )
        whenever(interactor.updateMobileSetup(isMobileSetup, deviceType)).thenReturn(
            Single.just(true)
        )
        whenever(unifiedSignInFlag.enabled).thenReturn(Single.just(false))

        val testState = model.state.test()
        model.process(LoginAuthIntents.SubmitTwoFactorCode(password, twoFACode))

        // Assert
        testState.assertValues(
            LoginAuthState(),
            LoginAuthState(
                authStatus = AuthStatus.Submit2FA,
                password = password,
                code = twoFACode
            ),
            LoginAuthState(
                authStatus = AuthStatus.VerifyPassword,
                password = password,
                code = twoFACode,
                payloadJson = TWO_FA_PAYLOAD
            ),
            LoginAuthState(
                authStatus = AuthStatus.UpdateMobileSetup,
                password = password,
                code = twoFACode,
                payloadJson = TWO_FA_PAYLOAD,
                isMobileSetup = isMobileSetup,
                deviceType = deviceType
            ),
            LoginAuthState(
                authStatus = AuthStatus.Complete,
                password = password,
                code = twoFACode,
                payloadJson = TWO_FA_PAYLOAD,
                isMobileSetup = isMobileSetup,
                deviceType = deviceType
            )
        )
    }

    @Test
    fun `fail to verify 2fa`() {
        val password = "password"
        val twoFACode = "code"
        whenever(interactor.submitCode(anyString(), anyString(), anyString(), anyString())).thenReturn(
            Single.error(Exception())
        )

        val testState = model.state.test()
        model.process(LoginAuthIntents.SubmitTwoFactorCode(password, twoFACode))

        // Assert
        testState.assertValues(
            LoginAuthState(),
            LoginAuthState(
                authStatus = AuthStatus.Submit2FA,
                password = password,
                code = twoFACode
            ),
            LoginAuthState(
                authStatus = AuthStatus.Invalid2FACode,
                password = password,
                code = twoFACode
            )
        )
    }

    @Test
    fun `request new 2fa code reduces counter`() {
        val retries = 3
        val reducedRetry = 2
        whenever(interactor.requestNew2FaCode(anyString(), anyString())).thenReturn(
            Single.just(mock())
        )
        whenever(interactor.getRemaining2FaRetries())
            .thenReturn(retries)
            .thenReturn(reducedRetry)

        val testState = model.state.test()
        model.process(LoginAuthIntents.RequestNew2FaCode)
        model.process(LoginAuthIntents.RequestNew2FaCode)

        testState.assertValues(
            LoginAuthState(),
            LoginAuthState(
                twoFaState = TwoFaCodeState.TwoFaRemainingTries(retries)
            ),
            LoginAuthState(
                twoFaState = TwoFaCodeState.TwoFaRemainingTries(reducedRetry)
            )
        )
    }

    @Test
    fun `request new 2fa retries exhausted`() {
        whenever(interactor.requestNew2FaCode(anyString(), anyString())).thenReturn(
            Single.just(mock())
        )

        val retries = 0
        whenever(interactor.getRemaining2FaRetries())
            .thenReturn(retries)

        val testState = model.state.test()
        model.process(LoginAuthIntents.RequestNew2FaCode)

        testState.assertValues(
            LoginAuthState(),
            LoginAuthState(
                twoFaState = TwoFaCodeState.TwoFaTimeLock
            )
        )
    }

    companion object {
        private const val JSON_HEADER = "application/json"

        private const val TWO_FA_PAYLOAD = "{\"payload\":\"{auth_type: 4}\"}"

        private const val DEVICE_TYPE_ANDROID = 2

        private const val GUID = "guid"
        private const val EMAIL = "email"
        private const val AUTH_TOKEN = "authToken"
        private const val USER_ID = "userId"
        private const val RECOVERY_TOKEN = "guid"

        private val testAuthInfo = LoginAuthInfo.ExtendedAccountInfo(
            accountWallet = AccountWalletInfo(
                guid = GUID,
                email = EMAIL,
                authToken = AUTH_TOKEN,
                nabuAccountInfo = NabuAccountInfo(
                    userId = USER_ID,
                    recoveryToken = RECOVERY_TOKEN
                ),
                isMobileSetUp = true,
                mobileDeviceType = 1,
                lastMnemonicBackup = 0L,
                hasCloudBackup = false,
                twoFaType = 1,
                sessionId = "123"
            ),
            isUpgradeable = false,
            isMergeable = false,
            userType = "123"
        )

        private const val INPUT_JSON = "{\"wallet\":{...}}"
    }
}

package piuk.blockchain.android.ui.kyc.reentry

import androidx.navigation.NavDirections
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.models.responses.nabu.NabuUser
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.KYCAnalyticsEvents
import com.blockchain.notifications.analytics.LaunchOrigin
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.KycNavXmlDirections
import piuk.blockchain.android.ui.kyc.navhost.toProfileModel

interface ReentryDecision {

    fun findReentryPoint(user: NabuUser): Single<ReentryPoint>
}

interface KycNavigator {

    /**
     * Will fetch user, if you have it, user overload.
     */
    fun findNextStep(): Single<NavDirections>

    fun findNextStep(user: NabuUser): Single<NavDirections>

    fun userAndReentryPointToDirections(user: NabuUser, reentryPoint: ReentryPoint): NavDirections
}

class ReentryDecisionKycNavigator(
    private val token: NabuToken,
    private val dataManager: NabuDataManager,
    private val reentryDecision: ReentryDecision,
    private val analytics: Analytics
) : KycNavigator {

    override fun findNextStep(): Single<NavDirections> =
        token.fetchNabuToken()
            .flatMap(dataManager::getUser)
            .flatMap { findNextStep(it) }

    override fun findNextStep(user: NabuUser): Single<NavDirections> =
        reentryDecision.findReentryPoint(user)
            .map { userAndReentryPointToDirections(user, it) }

    override fun userAndReentryPointToDirections(user: NabuUser, reentryPoint: ReentryPoint): NavDirections =
        when (reentryPoint) {
            ReentryPoint.EmailEntry -> {
                analytics.logEvent(KYCAnalyticsEvents.EmailVeriffRequested(LaunchOrigin.VERIFICATION))
                KycNavXmlDirections.actionStartEmailVerification(true)
            }
            ReentryPoint.CountrySelection -> KycNavXmlDirections.actionStartCountrySelection()
            ReentryPoint.Profile -> KycNavXmlDirections.actionStartProfile(
                user.requireCountryCode(), user.address?.state ?: "", user.address?.state ?: ""
            )
            ReentryPoint.Address -> {
                KycNavXmlDirections.actionStartAutocompleteAddressEntry(user.toProfileModel())
            }
            is ReentryPoint.AdditionalInfo ->
                KycNavXmlDirections.actionStartAdditionalInfoEntry(reentryPoint.root, user.requireCountryCode())
            ReentryPoint.MobileEntry -> KycNavXmlDirections.actionStartMobileVerification(user.requireCountryCode())
            ReentryPoint.Veriff -> {
                val countryCode = user.requireCountryCode()
                KycNavXmlDirections.actionStartVeriff(countryCode)
            }
        }
}

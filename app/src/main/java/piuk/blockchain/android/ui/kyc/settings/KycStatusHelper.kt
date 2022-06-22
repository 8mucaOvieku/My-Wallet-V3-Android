package piuk.blockchain.android.ui.kyc.settings

import androidx.annotation.VisibleForTesting
import com.blockchain.exceptions.MetadataNotFoundException
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.datamanagers.NabuDataUserProvider
import com.blockchain.nabu.models.responses.nabu.KycState
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.Scope
import com.blockchain.nabu.models.responses.nabu.UserState
import com.blockchain.nabu.service.TierService
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Singles
import io.reactivex.rxjava3.schedulers.Schedulers
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import timber.log.Timber

class KycStatusHelper(
    private val nabuDataManager: NabuDataManager,
    private val nabuDataUserProvider: NabuDataUserProvider,
    private val nabuToken: NabuToken,
    private val settingsDataManager: SettingsDataManager,
    private val tierService: TierService
) {

    private val fetchOfflineToken
        get() = nabuToken.fetchNabuToken()

    fun getSettingsKycStateTier(): Single<KycTiers> =
        shouldDisplayKyc().flatMap {
            if (it) {
                getKycTierStatus()
            } else {
                Single.just(KycTiers.default())
            }
        }.onErrorReturn {
            KycTiers.default()
        }

    fun shouldDisplayKyc(): Single<Boolean> = Singles.zip(
        isInKycRegion(), hasAccount()
    ) { allowedRegion, hasAccount -> allowedRegion || hasAccount }

    @Deprecated("Use NabuUserSync")
    fun syncPhoneNumberWithNabu(): Completable = nabuDataManager.requestJwt()
        .subscribeOn(Schedulers.io())
        .flatMap { jwt ->
            fetchOfflineToken.flatMap {
                nabuDataManager.updateUserWalletInfo(it, jwt)
                    .subscribeOn(Schedulers.io())
            }
        }
        .ignoreElement()
        .doOnError { Timber.e(it) }
        .onErrorResumeNext {
            if (it is MetadataNotFoundException) {
                // Allow users who aren't signed up to Nabu to ignore this failure
                return@onErrorResumeNext Completable.complete()
            } else {
                return@onErrorResumeNext Completable.error { it }
            }
        }

    fun getKycStatus(): Single<KycState> =
        nabuDataUserProvider.getUser()
            .subscribeOn(Schedulers.io())
            .map { it.kycState }
            .doOnError { Timber.e(it) }
            .onErrorReturn { KycState.None }

    fun getKycTierStatus(): Single<KycTiers> =
        tierService.tiers()
            .onErrorReturn { KycTiers.default() }
            .doOnError { Timber.e(it) }

    fun getUserState(): Single<UserState> =
        nabuDataUserProvider.getUser()
            .subscribeOn(Schedulers.io())
            .map { it.state }
            .doOnError { Timber.e(it) }
            .onErrorReturn { UserState.None }

    @VisibleForTesting
    internal fun hasAccount(): Single<Boolean> = fetchOfflineToken
        .map { true }
        .onErrorReturn { false }

    @VisibleForTesting
    internal fun isInKycRegion(): Single<Boolean> =
        settingsDataManager.getSettings()
            .subscribeOn(Schedulers.io())
            .map { it.countryCode }
            .flatMapSingle { isInKycRegion(it) }
            .singleOrError()

    private fun isInKycRegion(countryCode: String?): Single<Boolean> =
        nabuDataManager.getCountriesList(Scope.Kyc)
            .subscribeOn(Schedulers.io())
            .map { countries ->
                countries.asSequence()
                    .map { it.code }
                    .contains(countryCode)
            }
}

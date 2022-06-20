package piuk.blockchain.android.ui.dashboard.announcements

import com.blockchain.coincore.Coincore
import com.blockchain.nabu.Feature
import com.blockchain.nabu.NabuToken
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.nabu.datamanagers.NabuDataUserProvider
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.Limits
import com.blockchain.nabu.models.responses.nabu.LimitsJson
import com.blockchain.nabu.models.responses.nabu.Tier
import com.blockchain.nabu.models.responses.nabu.Tiers
import com.blockchain.nabu.service.TierService
import com.blockchain.remoteconfig.RemoteConfig
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.CryptoCurrency
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementQueries.Companion.NEW_ASSET_TICKER
import piuk.blockchain.android.ui.tiers
import piuk.blockchain.androidcore.data.settings.SettingsDataManager

class AnnouncementQueriesTest {

    private val nabuToken: NabuToken = mock()
    private val settings: SettingsDataManager = mock()
    private val nabu: NabuDataManager = mock()
    private val nabuDataUserProvider: NabuDataUserProvider = mock()
    private val tierService: TierService = mock()
    private val userIdentity: UserIdentity = mock()
    private val coincore: Coincore = mock()
    private val assetCatalogue: AssetCatalogue = mock()
    private val remoteConfig: RemoteConfig = mock()

    private val sbSync: SimpleBuySyncFactory = mock()

    private val sampleLimits = LimitsJson("", 0.toBigDecimal(), 0.toBigDecimal())

    private lateinit var subject: AnnouncementQueries

    @Before
    fun setUp() {
        subject = AnnouncementQueries(
            nabuToken = nabuToken,
            settings = settings,
            nabu = nabu,
            nabuDataUserProvider = nabuDataUserProvider,
            tierService = tierService,
            sbStateFactory = sbSync,
            userIdentity = userIdentity,
            coincore = coincore,
            assetCatalogue = assetCatalogue,
            remoteConfig = remoteConfig
        )
    }

    @Test
    fun `asset ticker raw json is empty`() {
        whenever(remoteConfig.getRawJson(NEW_ASSET_TICKER)).thenReturn(Single.just(""))
        whenever(assetCatalogue.fromNetworkTicker(any())).thenReturn(null)

        subject.getAssetFromCatalogue().test().assertComplete()
    }

    @Test
    fun `asset ticker raw json doesn't exist`() {
        val testException = Throwable()
        whenever(remoteConfig.getRawJson(NEW_ASSET_TICKER)).thenReturn(Single.error(testException))

        subject.getAssetFromCatalogue().test().assertError(testException)
    }

    @Test
    fun `asset ticker raw json returns unknown ticker`() {
        val moonToken = "TTM"
        whenever(remoteConfig.getRawJson(NEW_ASSET_TICKER)).thenReturn(Single.just(moonToken))
        whenever(assetCatalogue.fromNetworkTicker(moonToken)).thenReturn(null)

        subject.getAssetFromCatalogue().test().assertComplete()
    }

    @Test
    fun `asset ticker raw json returns known ticker`() {
        whenever(remoteConfig.getRawJson(NEW_ASSET_TICKER))
            .thenReturn(Single.just(CryptoCurrency.BTC.networkTicker))
        whenever(assetCatalogue.assetInfoFromNetworkTicker(CryptoCurrency.BTC.networkTicker))
            .thenReturn(CryptoCurrency.BTC)

        subject.getAssetFromCatalogue().test().assertValue(CryptoCurrency.BTC)
    }

    @Test
    fun `isTier1Or2Verified returns true for tier1 verified`() {

        whenever(tierService.tiers()).thenReturn(
            Single.just(
                KycTiers(
                    Tiers(
                        mapOf(
                            KycTierLevel.BRONZE to
                                Tier(
                                    KycTierState.None,
                                    Limits(null, null)
                                ),
                            KycTierLevel.SILVER to
                                Tier(
                                    KycTierState.Verified,
                                    Limits(null, null)
                                ),
                            KycTierLevel.GOLD to
                                Tier(
                                    KycTierState.None,
                                    Limits(null, null)
                                )
                        )
                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isTier1Or2Verified returns true for tier2 verified`() {
        whenever(tierService.tiers()).thenReturn(
            Single.just(
                KycTiers(
                    Tiers(
                        mapOf(
                            KycTierLevel.BRONZE to
                                Tier(
                                    KycTierState.None,
                                    Limits(null, null)
                                ),
                            KycTierLevel.SILVER to
                                Tier(
                                    KycTierState.Verified,
                                    Limits(null, null)
                                ),
                            KycTierLevel.GOLD to
                                Tier(
                                    KycTierState.Verified,
                                    Limits(null, null)
                                )
                        )
                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isTier1Or2Verified returns false if not verified`() {
        whenever(tierService.tiers()).thenReturn(
            Single.just(
                KycTiers(
                    Tiers(
                        mapOf(
                            KycTierLevel.BRONZE to
                                Tier(
                                    KycTierState.None,
                                    Limits(null, null)
                                ),
                            KycTierLevel.SILVER to
                                Tier(
                                    KycTierState.None,
                                    Limits(null, null)
                                ),
                            KycTierLevel.GOLD to
                                Tier(
                                    KycTierState.None,
                                    Limits(null, null)
                                )
                        )

                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - no local simple buy state exists, return false`() {
        whenever(sbSync.currentState()).thenReturn(null)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - local simple buy state exists but has finished kyc, return false`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(false)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.Verified)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - local simple buy state exists and has finished kyc, return true`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(state.kycVerificationState).thenReturn(null)

        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.None)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - simple buy state is not finished, and kyc state is pending - as expected`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.None)))
        whenever(sbSync.currentState()).thenReturn(state)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.None)))

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    // Belt and braces checks: add double check that the SB state doesn't think kyc data has been submitted
    // to patch AND-2790, 2801. This _may_ be insufficient, though. If it doesn't solve the problem, we may have to
    // check backend kyc state ourselves...

    @Test
    fun `isSimpleBuyKycInProgress - SB state reports unfinished, but kyc docs are submitted - belt & braces case`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)

        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.UnderReview)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - SB state reports unfinished, but kyc docs are submitted - belt & braces case 2`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.Verified)))
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `user isSddEligible but verified`() {
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(true))
        whenever(userIdentity.isVerifiedFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(true))

        subject.isSimplifiedDueDiligenceEligibleAndNotVerified()
            .test()
            .assertValue { !it }
    }

    @Test
    fun `user not SddEligible neither verified`() {
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(false))
        whenever(userIdentity.isVerifiedFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(false))

        subject.isSimplifiedDueDiligenceEligibleAndNotVerified()
            .test()
            .assertValue { !it }
    }

    @Test
    fun `user  SddEligible and not verified`() {
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(true))
        whenever(userIdentity.isVerifiedFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(false))

        subject.isSimplifiedDueDiligenceEligibleAndNotVerified()
            .test()
            .assertValue { it }
    }

    companion object {
        private const val BUY_ORDER_ID = "1234567890"
    }
}

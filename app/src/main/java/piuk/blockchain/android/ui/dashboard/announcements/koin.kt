package piuk.blockchain.android.ui.dashboard.announcements

import com.blockchain.koin.payloadScope
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.koin.replaceGsonKtxFeatureFlag
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.ui.dashboard.announcements.rule.AaveYfiDotAvailableAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.BackupPhraseAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.BitpayAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.CeloEurAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.CloudBackupAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.FiatFundsKycAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.FiatFundsNoKycAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.IncreaseLimitsAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.InterestAvailableAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.KycIncompleteAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.KycMoreInfoAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.KycRecoveryResubmissionAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.KycResubmissionAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.MajorProductBlockedAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.NewAssetAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.PaxRenamedAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.RecurringBuysAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.RegisterBiometricsAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.RegisteredForAirdropMiniAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.SellIntroAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.SendToDomainAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.SimpleBuyFinishSignupAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.SwapAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.TaxCenterAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.TransferCryptoAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.TwoFAAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.VerifyEmailAnnouncement
import piuk.blockchain.android.ui.dashboard.announcements.rule.WalletConnectAnnouncement

val dashboardAnnouncementsModule = module {

    scope(payloadScopeQualifier) {

        scoped {
            val availableAnnouncements = getAllAnnouncements()

            AnnouncementList(
                mainScheduler = AndroidSchedulers.mainThread(),
                availableAnnouncements = availableAnnouncements,
                orderAdapter = get(),
                dismissRecorder = get()
            )
        }

        factory {
            AnnouncementConfigAdapterImpl(
                config = get(),
                json = get(),
                replaceGsonKtxFF = get(replaceGsonKtxFeatureFlag)
            )
        }.bind(AnnouncementConfigAdapter::class)

        factory {
            AnnouncementQueries(
                nabuToken = get(),
                settings = get(),
                nabu = get(),
                tierService = get(),
                sbStateFactory = get(),
                userIdentity = get(),
                coincore = get(),
                remoteConfig = get(),
                assetCatalogue = get()
            )
        }

        factory {
            PaxRenamedAnnouncement(
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            KycResubmissionAnnouncement(
                userIdentity = get(),
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            KycIncompleteAnnouncement(
                userIdentity = get(),
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            KycMoreInfoAnnouncement(
                tierService = get(),
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            TaxCenterAnnouncement(
                userIdentity = get(),
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            MajorProductBlockedAnnouncement(
                userIdentity = get(),
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            BitpayAnnouncement(
                dismissRecorder = get(),
                walletStatus = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            VerifyEmailAnnouncement(
                dismissRecorder = get(),
                walletSettings = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            TwoFAAnnouncement(
                dismissRecorder = get(),
                walletStatus = get(),
                walletSettings = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            SwapAnnouncement(
                dismissRecorder = get(),
                queries = get(),
                identity = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            BackupPhraseAnnouncement(
                dismissRecorder = get(),
                walletStatus = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            IncreaseLimitsAnnouncement(
                dismissRecorder = get(),
                announcementQueries = get(),
                simpleBuyPrefs = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            RegisterBiometricsAnnouncement(
                dismissRecorder = get(),
                biometricsController = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            TransferCryptoAnnouncement(
                dismissRecorder = get(),
                walletStatus = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            RegisteredForAirdropMiniAnnouncement(
                dismissRecorder = get(),
                queries = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            SimpleBuyFinishSignupAnnouncement(
                dismissRecorder = get(),
                analytics = get(),
                queries = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            FiatFundsNoKycAnnouncement(
                dismissRecorder = get(),
                userIdentity = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            FiatFundsKycAnnouncement(
                dismissRecorder = get(),
                userIdentity = get(),
                bankService = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            SellIntroAnnouncement(
                dismissRecorder = get(),
                identity = get(),
                coincore = get(),
                analytics = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            CloudBackupAnnouncement(
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            InterestAvailableAnnouncement(
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            AaveYfiDotAvailableAnnouncement(
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            SendToDomainAnnouncement(
                dismissRecorder = get(),
                coincore = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            RecurringBuysAnnouncement(
                dismissRecorder = get(),
                announcementQueries = get(),
                currencyPrefs = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            NewAssetAnnouncement(
                dismissRecorder = get(),
                announcementQueries = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            KycRecoveryResubmissionAnnouncement(
                dismissRecorder = get(),
                userIdentity = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            CeloEurAnnouncement(
                announcementQueries = get(),
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)

        factory {
            WalletConnectAnnouncement(
                dismissRecorder = get()
            )
        }.bind(AnnouncementRule::class)
    }

    single {
        DismissRecorder(
            prefs = get(),
            clock = get()
        )
    }

    single {
        object : DismissClock {
            override fun now(): Long = System.currentTimeMillis()
        }
    }.bind(DismissClock::class)
}

fun getAllAnnouncements(): List<AnnouncementRule> {
    return payloadScope.getAll()
}

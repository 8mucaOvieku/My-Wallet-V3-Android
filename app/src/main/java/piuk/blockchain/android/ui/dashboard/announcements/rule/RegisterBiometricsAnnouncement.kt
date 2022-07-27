package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.walletmode.WalletMode
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

class RegisterBiometricsAnnouncement(
    dismissRecorder: DismissRecorder,
    private val biometricsController: BiometricsController
) : AnnouncementRule(dismissRecorder) {

    override val dismissKey = DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return Single.just(
            biometricsController.isHardwareDetected &&
                !biometricsController.isBiometricUnlockEnabled
        )
    }

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                dismissRule = DismissRule.CardPeriodic,
                dismissEntry = dismissEntry,
                titleText = R.string.register_fingerprint_card_title_1,
                bodyText = R.string.register_fingerprint_card_body_1,
                ctaText = R.string.register_fingerprint_card_cta,
                iconImage = R.drawable.ic_announce_fingerprint,
                dismissFunction = {
                    host.dismissAnnouncementCard()
                },
                ctaFunction = {
                    host.dismissAnnouncementCard()
                    host.startEnableFingerprintLogin()
                }
            )
        )
    }

    override val name = "fingerprint"

    override val associatedWalletModes: List<WalletMode>
        get() = WalletMode.values().toList()

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "EnableFingerprintAnnouncement_DISMISSED"
    }
}

package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.preferences.WalletStatusPrefs
import com.blockchain.walletmode.WalletMode
import info.blockchain.wallet.api.data.Settings
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard
import piuk.blockchain.androidcore.data.settings.SettingsDataManager

class TwoFAAnnouncement(
    dismissRecorder: DismissRecorder,
    private val walletStatusPrefs: WalletStatusPrefs,
    private val walletSettings: SettingsDataManager
) : AnnouncementRule(dismissRecorder) {

    override val dismissKey = DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return walletSettings.getSettings()
            .map { !it.isSmsVerified && walletStatusPrefs.isWalletFunded && it.authType == Settings.AUTH_TYPE_OFF }
            .singleOrError()
    }
    override val associatedWalletModes: List<WalletMode>
        get() = WalletMode.values().toList()

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                dismissRule = DismissRule.CardPeriodic,
                dismissEntry = dismissEntry,
                titleText = R.string.two_fa_card_title,
                bodyText = R.string.two_fa_card_body,
                ctaText = R.string.two_fa_card_cta,
                iconImage = R.drawable.ic_announce_two_step,
                dismissFunction = {
                    host.dismissAnnouncementCard()
                },
                ctaFunction = {
                    host.dismissAnnouncementCard()
                    host.startSetup2Fa()
                }
            )
        )
    }

    override val name = "two_fa"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "TwoFactorAuthAnnouncement_DISMISSED"
    }
}

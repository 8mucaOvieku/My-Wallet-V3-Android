package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.nabu.Tier
import com.blockchain.nabu.UserIdentity
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

class FiatFundsNoKycAnnouncement(
    dismissRecorder: DismissRecorder,
    private val userIdentity: UserIdentity
) : AnnouncementRule(dismissRecorder) {

    override val dismissKey = DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return userIdentity.getHighestApprovedKycTier().map {
            it != Tier.GOLD
        }
    }

    override fun show(host: AnnouncementHost) {
        host.showAnnouncementCard(
            card = StandardAnnouncementCard(
                name = name,
                dismissRule = DismissRule.CardOneTime,
                dismissEntry = dismissEntry,
                iconImage = R.drawable.vector_new_badge,
                titleText = R.string.fiat_funds_no_kyc_announcement_title,
                bodyText = R.string.fiat_funds_no_kyc_announcement_description,
                ctaText = R.string.learn_more,
                ctaFunction = {
                    host.dismissAnnouncementCard()
                    host.showFiatFundsKyc()
                },
                dismissFunction = {
                    host.dismissAnnouncementCard()
                }
            )
        )
    }

    override val name = "fiat_funds_no_kyc"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "FiatFundsNoKycAnnouncement_DISMISSED"
    }
}

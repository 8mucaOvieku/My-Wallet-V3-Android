package piuk.blockchain.android.deeplink

import android.content.Intent
import android.net.Uri
import com.blockchain.notifications.links.PendingLink
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.kyc.KycDeepLinkHelper
import piuk.blockchain.android.kyc.KycLinkState
import piuk.blockchain.android.thepit.ThePitDeepLinkParser

internal class DeepLinkProcessor(
    private val linkHandler: PendingLink,
    private val emailVerifiedLinkHelper: EmailVerificationDeepLinkHelper,
    private val kycDeepLinkHelper: KycDeepLinkHelper,
    private val thePitDeepLinkParser: ThePitDeepLinkParser,
    private val openBankingDeepLinkParser: OpenBankingDeepLinkParser,
    private val blockchainDeepLinkParser: BlockchainDeepLinkParser
) {
    fun getLink(intent: Intent): Single<LinkState> =
        linkHandler.getPendingLinks(intent).switchIfEmpty(Single.never()).flatMap {
            urlProcessor(it)
        }

    fun getLink(link: String): Single<LinkState> =
        urlProcessor(Uri.parse(link))

    private fun urlProcessor(uri: Uri): Single<LinkState> =
        Maybe.fromCallable {
            val emailVerifiedUri = emailVerifiedLinkHelper.mapUri(uri)
            if (emailVerifiedUri != EmailVerifiedLinkState.NoUri) {
                return@fromCallable LinkState.EmailVerifiedDeepLink(emailVerifiedUri)
            }
            val kyc = kycDeepLinkHelper.mapUri(uri)
            if (kyc != KycLinkState.NoUri) {
                return@fromCallable LinkState.KycDeepLink(kyc)
            }
            val linkId = thePitDeepLinkParser.mapUri(uri)
            if (linkId != null) {
                return@fromCallable LinkState.ThePitDeepLink(linkId)
            }
            val openBankingDeepLink = openBankingDeepLinkParser.mapUri(uri)
            if (openBankingDeepLink != null && openBankingDeepLink.type != OpenBankingLinkType.UNKNOWN) {
                return@fromCallable openBankingDeepLink
            }
            val blockchainLink = blockchainDeepLinkParser.mapUri(uri)
            if (blockchainLink != BlockchainLinkState.NoUri) {
                return@fromCallable LinkState.BlockchainLink(blockchainLink)
            }
            LinkState.NoUri
        }.switchIfEmpty(Maybe.just(LinkState.NoUri))
            .toSingle()
            .onErrorResumeNext { Single.just(LinkState.NoUri) }
}

sealed class LinkState {
    data class BlockchainLink(val link: BlockchainLinkState) : LinkState()
    data class EmailVerifiedDeepLink(val link: EmailVerifiedLinkState) : LinkState()
    data class KycDeepLink(val link: KycLinkState) : LinkState()
    data class ThePitDeepLink(val linkId: String) : LinkState()
    data class OpenBankingLink(val type: OpenBankingLinkType, val consentToken: String?) : LinkState()

    object NoUri : LinkState()
}

package piuk.blockchain.android.cards

import com.blockchain.commonarch.presentation.mvi.MviState
import com.blockchain.nabu.datamanagers.BillingAddress
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.CardStatus
import com.braintreepayments.cardform.utils.CardType
import info.blockchain.balance.FiatCurrency
import kotlinx.serialization.Serializable
import piuk.blockchain.android.R

@Serializable
data class CardState(
    val fiatCurrency: FiatCurrency,
    val cardId: String? = null,
    val cardStatus: CardStatus? = null,
    val billingAddress: BillingAddress? = null,
    val addCard: Boolean = false,
    @Transient
    @kotlinx.serialization.Transient
    val authoriseCard: CardAcquirerCredentials? = null,
    @Transient
    @kotlinx.serialization.Transient
    val cardRequestStatus: CardRequestStatus? = null
) : MviState

@Serializable
sealed class CardAcquirerCredentials {
    // This used to be EverypayAuthOptions
    data class Everypay(val paymentLink: String, val exitLink: String) : CardAcquirerCredentials()

    data class Stripe(val apiKey: String, val clientSecret: String) : CardAcquirerCredentials()

    data class Checkout(
        val apiKey: String,
        val paymentLink: String,
        val exitLink: String
    ) : CardAcquirerCredentials()
}

@Serializable
sealed class CardRequestStatus {
    class Error(val type: CardError) : CardRequestStatus()
    object Loading : CardRequestStatus()
    class Success(val card: PaymentMethod.Card) : CardRequestStatus()
}

fun CardType.icon() =
    when (this) {
        CardType.VISA -> R.drawable.ic_visa
        CardType.MASTERCARD -> R.drawable.ic_mastercard
        else -> this.frontResource
    }

enum class CardError {
    CREATION_FAILED,
    ACTIVATION_FAIL,
    PENDING_AFTER_POLL,
    LINK_FAILED,
    INSUFFICIENT_CARD_BALANCE,
    CARD_PAYMENT_DECLINED,
    DEBIT_CARD_ONLY
}

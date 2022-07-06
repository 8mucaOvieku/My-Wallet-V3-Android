package piuk.blockchain.android.ui.activity.detail.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.domain.paymentmethods.model.MobilePaymentType
import com.blockchain.domain.paymentmethods.model.PaymentMethod
import com.blockchain.domain.paymentmethods.model.PaymentMethodType
import com.blockchain.utils.toFormattedString
import info.blockchain.wallet.multiaddress.TransactionSummary
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Currency
import java.util.Locale
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ItemListInfoRowBinding
import piuk.blockchain.android.simplebuy.toHumanReadableRecurringBuy
import piuk.blockchain.android.simplebuy.toHumanReadableRecurringDate
import piuk.blockchain.android.ui.activity.detail.Action
import piuk.blockchain.android.ui.activity.detail.ActivityDetailsType
import piuk.blockchain.android.ui.activity.detail.Amount
import piuk.blockchain.android.ui.activity.detail.BuyCryptoWallet
import piuk.blockchain.android.ui.activity.detail.BuyFee
import piuk.blockchain.android.ui.activity.detail.BuyPaymentMethod
import piuk.blockchain.android.ui.activity.detail.BuyPurchaseAmount
import piuk.blockchain.android.ui.activity.detail.Copyable
import piuk.blockchain.android.ui.activity.detail.Created
import piuk.blockchain.android.ui.activity.detail.Description
import piuk.blockchain.android.ui.activity.detail.Fee
import piuk.blockchain.android.ui.activity.detail.FeeAmount
import piuk.blockchain.android.ui.activity.detail.FeeForTransaction
import piuk.blockchain.android.ui.activity.detail.From
import piuk.blockchain.android.ui.activity.detail.HistoricCryptoPrice
import piuk.blockchain.android.ui.activity.detail.HistoricValue
import piuk.blockchain.android.ui.activity.detail.NetworkFee
import piuk.blockchain.android.ui.activity.detail.NextPayment
import piuk.blockchain.android.ui.activity.detail.RecurringBuyFrequency
import piuk.blockchain.android.ui.activity.detail.SellCryptoWallet
import piuk.blockchain.android.ui.activity.detail.SellPurchaseAmount
import piuk.blockchain.android.ui.activity.detail.SwapReceiveAmount
import piuk.blockchain.android.ui.activity.detail.To
import piuk.blockchain.android.ui.activity.detail.TotalCostAmount
import piuk.blockchain.android.ui.activity.detail.TransactionId
import piuk.blockchain.android.ui.activity.detail.XlmMemo
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.context

class ActivityDetailInfoItemDelegate<in T>(
    private val onLongClick: (String) -> Unit
) : AdapterDelegate<T> {
    override fun isForViewType(items: List<T>, position: Int): Boolean {
        val item = items[position] as ActivityDetailsType
        return item !is Action && item !is Description
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        InfoItemViewHolder(
            ItemListInfoRowBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onLongClick
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as InfoItemViewHolder).bind(
        items[position] as ActivityDetailsType
    )
}

private class InfoItemViewHolder(
    private val binding: ItemListInfoRowBinding,
    private val onLongClick: (String) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: ActivityDetailsType) {
        with(binding) {
            itemListInfoRowTitle.text = getHeaderForType(item)
            itemListInfoRowDescription.text = getValueForType(item)
            setupCopyContentForType(item)
        }
    }

    private fun setupCopyContentForType(infoType: ActivityDetailsType) {
        (infoType as? Copyable)?.let { copyable ->
            val tinyPadding = context.resources.getDimension(R.dimen.tiny_margin).toInt()
            itemView.setPadding(itemView.paddingLeft, itemView.paddingTop, tinyPadding, itemView.paddingBottom)
            itemView.setOnLongClickListener {
                onLongClick(copyable.filed)
                true
            }
            binding.copyTapTarget.visible()
            binding.copyTapTarget.setOnClickListener {
                onLongClick(copyable.filed)
            }
        }
    }

    private fun getHeaderForType(infoType: ActivityDetailsType): String =
        when (infoType) {
            is Created -> context.getString(R.string.activity_details_created)
            is NextPayment -> context.getString(R.string.recurring_buy_details_next_payment)
            is Amount -> context.getString(R.string.activity_details_amount)
            is Fee -> context.getString(R.string.activity_details_fee)
            is HistoricValue -> context.getString(R.string.common_total)
            is HistoricCryptoPrice -> context.getString(R.string.activity_details_exchange_rate)
            is To -> context.getString(R.string.activity_details_to)
            is From -> context.getString(R.string.activity_details_from)
            is FeeForTransaction -> context.getString(R.string.activity_details_transaction_fee)
            is BuyFee -> context.getString(R.string.activity_details_buy_fee)
            is BuyPurchaseAmount -> context.getString(R.string.activity_details_buy_purchase_amount_1)
            is TotalCostAmount -> context.getString(R.string.common_total)
            is FeeAmount -> context.getString(R.string.recurring_buy_details_fee)
            is SellPurchaseAmount -> context.getString(R.string.common_total)
            is TransactionId -> context.getString(R.string.activity_details_buy_tx_id)
            is BuyCryptoWallet,
            is SellCryptoWallet -> context.getString(R.string.activity_details_buy_deposited_to)
            is BuyPaymentMethod -> context.getString(R.string.activity_details_buy_payment_method)
            is SwapReceiveAmount -> context.getString(R.string.activity_details_swap_for)
            is NetworkFee -> context.getString(
                R.string.tx_confirmation_network_fee,
                infoType.feeValue.currency.displayTicker
            )
            is XlmMemo -> context.getString(R.string.xlm_memo_text)
            is RecurringBuyFrequency -> context.getString(R.string.recurring_buy_frequency_label_1)
            else -> context.getString(R.string.empty)
        }

    private fun getValueForType(infoType: ActivityDetailsType): String =
        when (infoType) {
            is Created -> infoType.date.toFormattedString()
            is NextPayment -> infoType.date.toFormattedString()
            is RecurringBuyFrequency -> {
                val recurringFrequency = infoType.frequency.toHumanReadableRecurringBuy(context)
                val recurringDate = infoType.frequency.toHumanReadableRecurringDate(
                    context,
                    ZonedDateTime.ofInstant(
                        infoType.nextPayment.toInstant(),
                        ZoneId.systemDefault()
                    )
                )
                context.getString(R.string.common_spaced_strings, recurringFrequency, recurringDate)
            }
            is Amount -> infoType.value.toStringWithSymbol()
            is Fee -> infoType.feeValue?.toStringWithSymbol() ?: context.getString(
                R.string.activity_details_fee_load_fail
            )
            is HistoricValue -> infoType.fiatAtExecution?.toStringWithSymbol()
                ?: context.getString(
                    R.string.activity_details_historic_value_load_fail
                )
            is HistoricCryptoPrice -> context.getString(
                R.string.activity_details_exchange_rate_value,
                infoType.price?.toStringWithSymbol(),
                infoType.cryptoCurrency
            )
            is To -> infoType.toAddress ?: context.getString(
                R.string.activity_details_to_load_fail
            )
            is From -> infoType.fromAddress ?: context.getString(
                R.string.activity_details_from_load_fail
            )
            is FeeForTransaction -> {
                when (infoType.transactionType) {
                    TransactionSummary.TransactionType.SENT -> context.getString(
                        R.string.activity_details_transaction_fee_send,
                        infoType.cryptoValue.toStringWithSymbol()
                    )
                    else -> context.getString(
                        R.string.activity_details_transaction_fee_unknown
                    )
                }
            }
            is BuyFee -> infoType.feeValue.toStringWithSymbol()
            is BuyPurchaseAmount -> infoType.fundedFiat.toStringWithSymbol()
            is TotalCostAmount -> infoType.fundedFiat.toStringWithSymbol()
            is FeeAmount -> infoType.fundedFiat.toStringWithSymbol()
            is TransactionId -> infoType.txId
            is BuyCryptoWallet -> context.getString(
                R.string.custodial_wallet_default_label_2, infoType.crypto.displayTicker
            )
            is SellCryptoWallet -> infoType.currency.name
            is SellPurchaseAmount -> infoType.value.toStringWithSymbol()
            is BuyPaymentMethod -> {
                with(infoType.paymentDetails) {
                    when {
                        !endDigits.isNullOrEmpty() &&
                            !label.isNullOrEmpty() -> {
                            with(context) {
                                accountType?.let {
                                    val accType = getString(
                                        R.string.payment_method_type_account_info,
                                        accountType,
                                        endDigits
                                    )

                                    getString(
                                        R.string.common_spaced_strings,
                                        label,
                                        accType
                                    )
                                } ?: getString(
                                    R.string.common_hyphenated_strings,
                                    label,
                                    endDigits
                                )
                            }
                        }
                        paymentMethodType == PaymentMethodType.PAYMENT_CARD &&
                            endDigits.isNullOrEmpty() && label.isNullOrEmpty() -> {
                            context.getString(R.string.credit_or_debit_card)
                        }
                        paymentMethodId == PaymentMethod.FUNDS_PAYMENT_ID -> {
                            label?.let {
                                Currency.getInstance(label).getDisplayName(Locale.getDefault())
                            } ?: ""
                        }
                        mobilePaymentType == MobilePaymentType.GOOGLE_PAY -> {
                            context.getString(R.string.google_pay)
                        }
                        mobilePaymentType == MobilePaymentType.APPLE_PAY -> {
                            context.getString(R.string.apple_pay)
                        }
                        else -> {
                            context.getString(R.string.activity_details_payment_load_fail)
                        }
                    }
                }
            }
            is SwapReceiveAmount -> infoType.receivedAmount.toStringWithSymbol()
            is NetworkFee -> infoType.feeValue.toStringWithSymbol()
            is XlmMemo -> infoType.memo
            else -> ""
        }
}

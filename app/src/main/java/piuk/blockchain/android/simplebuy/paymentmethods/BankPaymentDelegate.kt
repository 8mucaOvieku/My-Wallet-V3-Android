package piuk.blockchain.android.simplebuy.paymentmethods

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.domain.paymentmethods.model.PaymentMethod
import com.bumptech.glide.Glide
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.BankPaymentMethodLayoutBinding
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.context

class BankPaymentDelegate : AdapterDelegate<PaymentMethodItem> {

    override fun isForViewType(items: List<PaymentMethodItem>, position: Int): Boolean =
        items[position].paymentMethod is PaymentMethod.Bank

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        BankPaymentViewHolder(
            BankPaymentMethodLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(items: List<PaymentMethodItem>, position: Int, holder: RecyclerView.ViewHolder) {
        (holder as BankPaymentViewHolder).bind(items[position])
    }

    private class BankPaymentViewHolder(private val binding: BankPaymentMethodLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(paymentMethodItem: PaymentMethodItem) {
            with(binding) {
                (paymentMethodItem.paymentMethod as? PaymentMethod.Bank)?.let {
                    paymentMethodTitle.text = it.bankName

                    val accountType = if (it.uiAccountType.isNotEmpty()) it.uiAccountType + " " else ""
                    val accountEnding = if (it.accountEnding.isNotEmpty()) " " + it.accountEnding else ""

                    paymentMethodDetails.text = context.getString(
                        R.string.payment_method_type_account_info, accountType, accountEnding
                    )

                    if (it.iconUrl.isNotEmpty()) {
                        Glide.with(context).load(it.iconUrl).into(paymentMethodIcon)
                    }
                }
                paymentMethodRoot.setOnClickListener { paymentMethodItem.clickAction() }
            }
        }
    }
}

package piuk.blockchain.android.ui.dashboard.assetdetails.delegates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.nabu.models.data.RecurringBuy
import com.blockchain.nabu.models.data.RecurringBuyState
import com.blockchain.utils.toFormattedDateWithoutYear
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ViewAccountRecurringBuyOverviewBinding
import piuk.blockchain.android.simplebuy.toHumanReadableRecurringBuy
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsItem
import piuk.blockchain.android.util.context
import piuk.blockchain.android.util.setAssetIconColoursWithTint

class RecurringBuyItemDelegate(
    private val onRecurringBuyClicked: (RecurringBuy) -> Unit
) : AdapterDelegate<AssetDetailsItem> {
    override fun isForViewType(items: List<AssetDetailsItem>, position: Int): Boolean =
        items[position] is AssetDetailsItem.RecurringBuyInfo

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        RecurringBuyViewHolder(
            ViewAccountRecurringBuyOverviewBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onRecurringBuyClicked
        )

    override fun onBindViewHolder(
        items: List<AssetDetailsItem>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as RecurringBuyViewHolder).bind(
        items[position] as AssetDetailsItem.RecurringBuyInfo,
        items.indexOfFirst { it is AssetDetailsItem.RecurringBuyInfo } == position
    )
}

private class RecurringBuyViewHolder(
    private val binding: ViewAccountRecurringBuyOverviewBinding,
    private val onRecurringBuyClicked: (RecurringBuy) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: AssetDetailsItem.RecurringBuyInfo, isFirstItemOfCategory: Boolean) {
        with(binding) {
            rbHeaderGroup.visibleIf { isFirstItemOfCategory }

            item.recurringBuy.asset?.let {
                rbIcon.setAssetIconColoursWithTint(it)
            }

            rbTitle.text = context.getString(
                R.string.dashboard_recurring_buy_item_title_1,
                item.recurringBuy.amount.toStringWithSymbol(),
                item.recurringBuy.recurringBuyFrequency.toHumanReadableRecurringBuy(context)
            )

            rbLabel.text = if (item.recurringBuy.state == RecurringBuyState.ACTIVE) {
                context.getString(
                    R.string.dashboard_recurring_buy_item_label,
                    item.recurringBuy.nextPaymentDate.toFormattedDateWithoutYear()
                )
            } else {
                context.getString(R.string.dashboard_recurring_buy_item_label_error)
            }
        }

        binding.root.setOnClickListener { onRecurringBuyClicked(item.recurringBuy) }
    }
}

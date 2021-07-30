package piuk.blockchain.android.ui.recurringbuy.domain

import com.blockchain.nabu.models.data.RecurringBuyFrequency
import io.reactivex.rxjava3.core.Single
import java.time.ZonedDateTime

interface TradeDataManager {

    fun isFirstTimeBuyer(): Single<Boolean>

    fun getNextPaymentDate(): Single<Map<RecurringBuyFrequency, ZonedDateTime>>
}
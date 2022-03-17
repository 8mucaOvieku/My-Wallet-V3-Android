package piuk.blockchain.android.ui.dashboard.assetdetails

import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAsset
import com.blockchain.coincore.StateAwareAction
import com.blockchain.commonarch.presentation.mvi.MviIntent
import com.blockchain.core.price.HistoricalRateList
import com.blockchain.core.price.HistoricalTimeSpan
import com.blockchain.core.price.Prices24HrWithDelta
import com.blockchain.nabu.FeatureAccess
import com.blockchain.nabu.models.data.RecurringBuy
import com.blockchain.nabu.models.data.RecurringBuyPaymentDetails
import com.blockchain.nabu.models.data.RecurringBuyState
import info.blockchain.balance.AssetInfo

sealed class AssetDetailsIntent : MviIntent<AssetDetailsState>

class ShowAssetActionsIntent(
    val account: BlockchainAccount
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

class AccountActionsLoaded(
    private val account: BlockchainAccount,
    private val actions: Set<StateAwareAction>
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            selectedAccount = account,
            errorState = AssetDetailsError.NONE,
            actions = actions,
            assetDetailsCurrentStep = AssetDetailsStep.ASSET_ACTIONS
        ).updateBackstack(oldState)
}

class LoadAsset(
    val asset: CryptoAsset
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            asset = asset,
            assetDisplayMap = mapOf()
        )
}

object CheckUserBuyStatus : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState
}

class UserBuyAccessUpdated(private val userBuyAccess: FeatureAccess) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(userBuyAccess = userBuyAccess)
}

class UpdateTimeSpan(
    val updatedTimeSpan: HistoricalTimeSpan
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(timeSpan = updatedTimeSpan)
}

class HandleActionIntent(
    private val action: AssetAction
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(hostAction = action)
}

object HandleActionIntentLockedForTier : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.KYC_UPGRADE_NOW)
}

object NavigateToKyc : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(navigateToKyc = true)
}

object SelectAccount : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            assetDetailsCurrentStep = AssetDetailsStep.SELECT_ACCOUNT
        ).updateBackstack(oldState)
}

object ChartLoading : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(chartLoading = true)
}

class AssetDisplayDetailsLoaded(
    private val assetDisplayMap: AssetDisplayMap
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDisplayMap = assetDisplayMap)
}

class ChartDataLoaded(
    private val chartData: HistoricalRateList
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            chartData = chartData,
            chartLoading = false
        )
}

object ChartDataLoadFailed : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            chartData = emptyList(),
            chartLoading = false,
            errorState = AssetDetailsError.NO_CHART_DATA
        )
}

object AssetDisplayDetailsFailed : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.NO_ASSET_DETAILS
        )
}

class RecurringBuyDataLoaded(private val items: Map<String, RecurringBuy>) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            recurringBuys = items
        )
}

object RecurringBuyDataFailed : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.NO_RECURRING_BUYS
        )
}

object ShowCustodyIntroSheetIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.CUSTODY_INTRO_SHEET)
}

object ShowAssetDetailsIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.ASSET_DETAILS).updateBackstack(oldState)
}

object CustodialSheetFinished : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(assetDetailsCurrentStep = AssetDetailsStep.ASSET_DETAILS)
}

object ClearSheetDataIntent : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = AssetDetailsState()
}

object TransactionInFlight : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(errorState = AssetDetailsError.TX_IN_FLIGHT)
}

object ClearActionStates : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.NONE,
            hostAction = null
        )
}

object ClearSelectedRecurringBuy : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.NONE,
            selectedRecurringBuy = null
        )
}

object DeleteRecurringBuy : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

object GetPaymentDetails : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState
}

class UpdatePaymentDetails(
    private val recurringBuyPaymentDetails: RecurringBuyPaymentDetails
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            selectedRecurringBuy = oldState.selectedRecurringBuy?.copy(paymentDetails = recurringBuyPaymentDetails)
        )
}

class UpdatePriceDeltaDetails(
    private val prices24HrWithDelta: Prices24HrWithDelta
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            prices24HrWithDelta = prices24HrWithDelta
        )
}

object UpdatePriceDeltaFailed : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(errorState = AssetDetailsError.NO_PRICE_DATA)
}

object ShowInterestDashboard : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(navigateToInterestDashboard = true)
}

class ShowRelevantAssetDetailsSheet(
    val asset: AssetInfo
) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState = oldState.updateBackstack(oldState)
}

class ShowRecurringBuySheet(private val recurringBuy: RecurringBuy) : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            assetDetailsCurrentStep = AssetDetailsStep.RECURRING_BUY_DETAILS,
            selectedRecurringBuy = recurringBuy
        ).updateBackstack(oldState)
}

object UpdateRecurringBuy : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            selectedRecurringBuy = oldState.selectedRecurringBuy?.copy(state = RecurringBuyState.INACTIVE)
        )
}

object UpdateRecurringBuyError : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState =
        oldState.copy(
            errorState = AssetDetailsError.RECURRING_BUY_DELETE
        )
}

object ReturnToPreviousStep : AssetDetailsIntent() {
    override fun reduce(oldState: AssetDetailsState): AssetDetailsState {
        val stack = oldState.stepsBackStack
        require(stack.isNotEmpty())
        val previousStep = stack.pop()
        return oldState.copy(
            stepsBackStack = stack,
            assetDetailsCurrentStep = previousStep,
            hostAction = null,
            errorState = AssetDetailsError.NONE
        )
    }
}

fun AssetDetailsState.updateBackstack(oldState: AssetDetailsState) =
    if (oldState.assetDetailsCurrentStep != this.assetDetailsCurrentStep &&
        oldState.assetDetailsCurrentStep.addToBackStack
    ) {
        val updatedStack = oldState.stepsBackStack
        updatedStack.push(oldState.assetDetailsCurrentStep)

        this.copy(stepsBackStack = updatedStack)
    } else {
        this
    }

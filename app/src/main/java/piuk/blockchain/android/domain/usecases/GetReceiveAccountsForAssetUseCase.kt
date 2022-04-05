package piuk.blockchain.android.domain.usecases

import com.blockchain.coincore.ActionState
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.AssetFilter
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.SingleAccountList
import com.blockchain.coincore.filterByAction
import com.blockchain.coincore.filterByActionAndState
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.usecases.UseCase
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Single

class GetReceiveAccountsForAssetUseCase(
    private val coincore: Coincore,
    private val entitySwitchSilverEligibilityFeatureFlag: FeatureFlag
) : UseCase<AssetInfo, Single<SingleAccountList>>() {

    override fun execute(parameter: AssetInfo): Single<SingleAccountList> =
        coincore[parameter].accountGroup(AssetFilter.All).flatMapSingle { accountGroup ->
            entitySwitchSilverEligibilityFeatureFlag.enabled
                .flatMap { enabled ->
                    if (enabled) {
                        accountGroup.accounts.filterByActionAndState(
                            AssetAction.Receive,
                            listOf(ActionState.Available, ActionState.LockedForTier)
                        )
                    } else {
                        accountGroup.accounts.filterByAction(AssetAction.Receive)
                    }
                }
        }.toSingle()
}

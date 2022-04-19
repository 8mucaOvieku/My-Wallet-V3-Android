package com.blockchain.nabu.service

import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.api.nabu.Nabu
import com.blockchain.nabu.common.extensions.wrapErrorMessage
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.Limits
import com.blockchain.nabu.models.responses.nabu.LimitsJson
import com.blockchain.nabu.models.responses.nabu.Tier
import com.blockchain.nabu.models.responses.nabu.TierResponse
import com.blockchain.nabu.models.responses.nabu.TierUpdateJson
import com.blockchain.nabu.models.responses.nabu.Tiers
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.Currency
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

internal class NabuTierService(
    private val endpoint: Nabu,
    private val assetCatalogue: AssetCatalogue,
    private val authenticator: Authenticator
) : TierService, TierUpdater {

    override fun tiers(): Single<KycTiers> =
        authenticator.authenticate {
            endpoint.getTiers(it.authHeader).wrapErrorMessage()
        }.map { tiersResponse ->
            KycTiers(
                constructTierMap(tiersResponse.tiers)
            )
        }.subscribeOn(Schedulers.io())

    private fun constructTierMap(tiersResponse: List<TierResponse>): Tiers =
        KycTierLevel.values().map { level ->
            val tierResponse = tiersResponse[level.ordinal]
            val limitsCurrency = tierResponse.limits?.currency?.let {
                assetCatalogue.fromNetworkTicker(it)
            }
            level to Tier(
                tierResponse.state,
                Limits(
                    dailyLimit = tierResponse.limits?.dailyLimit(limitsCurrency),
                    annualLimit = tierResponse.limits?.annualLimit(limitsCurrency)
                )
            )
        }.toTiersMap()

    override fun setUserTier(tier: Int): Completable =
        authenticator.authenticate {
            endpoint.setTier(
                TierUpdateJson(tier),
                it.authHeader
            ).toSingleDefault(tier)
        }.ignoreElement()

    private fun List<Pair<KycTierLevel, Tier>>.toTiersMap(): Tiers = Tiers(this.toMap())
}

private fun LimitsJson.annualLimit(limitsCurrency: Currency?): Money? {
    val currency = limitsCurrency ?: return null
    val amount = annual ?: return null
    return Money.fromMajor(currency, amount)
}

private fun LimitsJson.dailyLimit(limitsCurrency: Currency?): Money? {
    val currency = limitsCurrency ?: return null
    val amount = daily ?: return null
    return Money.fromMajor(currency, amount)
}

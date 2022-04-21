package com.blockchain.core.interest

import com.blockchain.api.services.InterestBalanceDetails
import com.blockchain.api.services.InterestService
import com.blockchain.auth.AuthHeaderProvider
import com.blockchain.core.common.caching.TimedCacheRequest
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import io.reactivex.rxjava3.core.Single

typealias AssetInterestBalanceMap = Map<AssetInfo, InterestAccountBalance>

internal class InterestBalanceCallCache(
    private val balanceService: InterestService,
    private val assetCatalogue: AssetCatalogue,
    private val authHeaderProvider: AuthHeaderProvider
) {
    private val refresh: () -> Single<AssetInterestBalanceMap> = {
        authHeaderProvider.getAuthHeader()
            .flatMap { auth ->
                balanceService.getAllInterestAccountBalances(auth)
            }.map { details ->
                details.mapNotNull { entry ->
                    (assetCatalogue.fromNetworkTicker(entry.assetTicker) as? AssetInfo)?.let { assetInfo ->
                        assetInfo to entry.toInterestBalance(assetInfo)
                    }
                }.toMap()
            }
    }

    private val cache = TimedCacheRequest(
        cacheLifetimeSeconds = CACHE_LIFETIME,
        refreshFn = refresh
    )

    fun getBalances(): Single<AssetInterestBalanceMap> =
        cache.getCachedSingle().onErrorReturn {
            emptyMap()
        }

    fun invalidate() {
        cache.invalidate()
    }

    companion object {
        private const val CACHE_LIFETIME = 240L
    }
}

private fun InterestBalanceDetails.toInterestBalance(asset: AssetInfo) =
    InterestAccountBalance(
        totalBalance = CryptoValue.fromMinor(asset, totalBalance),
        pendingInterest = CryptoValue.fromMinor(asset, pendingInterest),
        pendingDeposit = CryptoValue.fromMinor(asset, pendingDeposit),
        totalInterest = CryptoValue.fromMinor(asset, totalInterest),
        lockedBalance = CryptoValue.fromMinor(asset, lockedBalance),
        hasTransactions = true
    )

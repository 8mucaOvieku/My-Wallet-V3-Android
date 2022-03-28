package info.blockchain.wallet.api

import info.blockchain.wallet.api.FeeApi.Companion.cacheTime
import info.blockchain.wallet.api.FeeApi.Companion.feeCache
import info.blockchain.wallet.api.data.FeeOptions
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.TimeUnit

data class FeeApi(private val feeEndpoints: FeeEndpoints) {
    /**
     * Returns a [FeeOptions] object for BTC which contains both a "regular" and a "priority"
     * fee option, both listed in Satoshis per byte.
     */
    val btcFeeOptions: Observable<FeeOptions>
        get() = byCache("BTC") { feeEndpoints.getBtcFeeOptions() }

    /**
     * Returns a [FeeOptions] object for BCH which contains both a "regular" and a "priority"
     * fee option, both listed in Satoshis per byte.
     */
    val bchFeeOptions: Observable<FeeOptions>
        get() = byCache("BCH") { feeEndpoints.getBchFeeOptions() }

    /**
     * Returns a [FeeOptions] object for ETH which contains both a "regular" and a "priority"
     * fee option.
     */
    val ethFeeOptions: Observable<FeeOptions>
        get() = byCache("ETH") { feeEndpoints.getEthFeeOptions() }

    /**
     * Returns a [FeeOptions] object for XLM which contains both a "regular" and a "priority"
     * fee option.
     */
    val xlmFeeOptions: Observable<FeeOptions>
        get() = byCache("XLM") {
            feeEndpoints.getFeeOptions("xlm")
        }

    /**
     * Returns a [FeeOptions] object for ERC20 tokens which contains both a "regular" and a "priority"
     * fee option.
     * @param contractAddress the contract address for ERC20
     */
    fun getErc20FeeOptions(contractAddress: String = ""): Observable<FeeOptions> {
        return byCache("ETH") { feeEndpoints.getErc20FeeOptions(contractAddress) }
    }

    companion object {
        internal val feeCache = mutableMapOf<String, FeeOptionsCacheEntry>()
        internal val cacheTime = TimeUnit.MINUTES.toMillis(2)
    }
}

internal data class FeeOptionsCacheEntry(val fee: Observable<FeeOptions>, val timestamp: Long)

private fun byCache(currency: String, loader: () -> Observable<FeeOptions>): Observable<FeeOptions> {
    val entry = feeCache[currency]

    val timestamp = System.currentTimeMillis()
    return if (entry == null || (timestamp - entry.timestamp) > cacheTime) {
        val newEntry = loader().cache()
        feeCache[currency] = FeeOptionsCacheEntry(newEntry, timestamp)
        newEntry
    } else {
        entry.fee
    }
}

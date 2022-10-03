package com.blockchain.core.chains.erc20.domain.model

import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Single
import java.math.BigInteger

data class Erc20HistoryEvent(
    val transactionHash: String,
    val value: CryptoValue,
    val from: String,
    val to: String,
    val blockNumber: BigInteger,
    val timestamp: Long,
    val fee: Single<Money>
) {
    fun isFromAccount(accountHash: String): Boolean =
        accountHash.compareTo(from, ignoreCase = true) == 0

    fun isToAccount(accountHash: String): Boolean =
        accountHash.compareTo(to, ignoreCase = true) == 0
}

typealias Erc20HistoryList = List<Erc20HistoryEvent>

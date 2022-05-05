package com.blockchain.api.services

import com.blockchain.api.txlimits.TxLimitsApi

class TxLimitsService(
    private val api: TxLimitsApi
) {

    fun getCrossborderLimits(
        authHeader: String,
        outputCurrency: String,
        sourceCurrency: String,
        targetCurrency: String,
        sourceAccountType: String,
        targetAccountType: String
    ) = api.getCrossborderLimits(
        authorization = authHeader,
        outputCurrency = outputCurrency,
        sourceCurrency = sourceCurrency,
        targetCurrency = targetCurrency,
        sourceAccountType = sourceAccountType,
        targetAccountType = targetAccountType
    )

    fun getFeatureLimits(authHeader: String) = api.getFeatureLimits(authHeader)
}

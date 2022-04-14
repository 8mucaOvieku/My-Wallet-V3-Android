package com.blockchain.analytics

interface ProviderSpecificAnalytics {
    fun logSingUp(success: Boolean)
    fun logLogin(success: Boolean)
    fun logContentView(screen: String)
    fun logShare(share: String)
}

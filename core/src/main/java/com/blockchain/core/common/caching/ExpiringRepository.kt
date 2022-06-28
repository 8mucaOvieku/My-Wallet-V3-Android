package com.blockchain.core.common.caching

import io.reactivex.rxjava3.core.Maybe

private const val CACHE_LIFETIME = 60 * 1000

abstract class ExpiringRepository<T, U> {
    var lastUpdatedTimestamp = -1L

    fun isCacheExpired() =
        System.currentTimeMillis() - lastUpdatedTimestamp >= CACHE_LIFETIME

    abstract fun getFromNetwork(param: U): Maybe<T>
    abstract fun getFromCache(param: U): Maybe<T>
}

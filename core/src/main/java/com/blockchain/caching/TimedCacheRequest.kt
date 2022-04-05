package com.blockchain.caching

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TimedCacheRequest<T>(
    private val cacheLifetimeSeconds: Long,
    private val refreshFn: () -> Single<T>
) {
    private val expired = AtomicBoolean(true)
    private lateinit var current: Single<T>

    fun getCachedSingle(): Single<T> =
        Single.defer {
            if (expired.compareAndSet(true, false)) {
                current = refreshFn.invoke().cache().doOnError {
                    expired.set(true)
                }

                Single.timer(cacheLifetimeSeconds, TimeUnit.SECONDS)
                    .subscribeBy(onSuccess = { expired.set(true) })
            }
            current
        }

    fun invalidate() {
        expired.set(true)
    }
}

class ParameteredSingleTimedCacheRequest<INPUT, OUTPUT>(
    private val cacheLifetimeSeconds: Long,
    private val refreshFn: (INPUT) -> Single<OUTPUT>
) {
    private val expired = hashMapOf<INPUT, Boolean>()
    private lateinit var current: Single<OUTPUT>

    fun getCachedSingle(input: INPUT): Single<OUTPUT> =
        Single.defer {
            if (expired[input] != false) {
                current = refreshFn.invoke(input).cache().doOnSuccess {
                    expired[input] = false
                }.doOnError {
                    expired[input] = true
                }

                Single.timer(cacheLifetimeSeconds, TimeUnit.SECONDS)
                    .subscribeBy(onSuccess = { expired[input] = true })
            }
            current
        }

    fun invalidate(input: INPUT) {
        expired[input] = true
    }

    fun invalidateAll() {
        expired.keys.forEach { expired[it] = true }
    }
}

class ParameteredMappedSinglesTimedRequests<INPUT, OUTPUT>(
    private val cacheLifetimeSeconds: Long,
    private val refreshFn: (INPUT) -> Single<OUTPUT>
) {
    private val expired = hashMapOf<INPUT, Boolean>()
    private val values = hashMapOf<INPUT, Single<OUTPUT>>()

    fun getCachedSingle(input: INPUT): Single<OUTPUT> =
        Single.defer {
            if (expired[input] != false) {
                values[input] = refreshFn.invoke(input).cache().doOnSuccess {
                    expired[input] = false
                }.doOnError {
                    expired[input] = true
                }

                Single.timer(cacheLifetimeSeconds, TimeUnit.SECONDS)
                    .subscribeBy(onSuccess = { expired[input] = true })
            }
            values[input]
        }

    fun invalidate(input: INPUT) {
        expired[input] = true
    }

    fun invalidateAll() {
        expired.keys.forEach { expired[it] = true }
    }
}

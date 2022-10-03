package com.blockchain.network.websocket

import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit

fun WebSocketConnection.autoRetry(): WebSocketConnection = RetryingConnection(this)

fun <OUTGOING, INCOMING> WebSocket<OUTGOING, INCOMING>.autoRetry(): WebSocket<OUTGOING, INCOMING> =
    this + (this as WebSocketConnection).autoRetry()

private class RetryingConnection(
    private val inner: WebSocketConnection
) : WebSocketConnection by inner {

    private val connections = CompositeDisposable()

    private val timeoutTimes = listOf(1000L, 2000L, 4000L)

    override fun open() {
        resetEvents(0)
        inner.open()
    }

    private fun resetEvents(timeoutIndex: Int) {
        connections.clear()
        connections += watchEvents(timeoutIndex)
    }

    private fun watchEvents(timeoutIndex: Int): Disposable =
        connectionEvents
            .throttleWithTimeout(timeoutTimes[timeoutIndex], TimeUnit.MILLISECONDS)
            .subscribe {
                when (it) {
                    is ConnectionEvent.Failure -> {
                        resetEvents(Math.min(timeoutIndex + 1, timeoutTimes.size - 1))
                        inner.open()
                    }
                    ConnectionEvent.ClientDisconnect -> inner.open()
                    ConnectionEvent.Connected -> resetEvents(0)
                    ConnectionEvent.Authenticated -> {}
                }
            }

    override fun close() {
        connections.clear()
        inner.close()
    }
}

package com.blockchain.network.websocket

import com.blockchain.logging.ILogger
import com.blockchain.logging.NullLogger
import io.reactivex.rxjava3.core.Observable
import org.koin.java.KoinJavaComponent.getKoin

fun <OUTGOING, INCOMING> WebSocket<OUTGOING, INCOMING>.debugLog(label: String): WebSocket<OUTGOING, INCOMING> {
    val logger: ILogger = getKoin().get()
    return if (logger == NullLogger) {
        this
    } else {
        DebugLogWebSocket(label, this, logger)
    }
}

private class DebugLogWebSocket<OUTGOING, INCOMING>(
    private val label: String,
    private val inner: WebSocket<OUTGOING, INCOMING>,
    private val logger: ILogger
) : WebSocket<OUTGOING, INCOMING> {

    override fun open() {
        logger.d("WebSocket $label Open called")
        inner.open()
    }

    override fun close() {
        logger.d("WebSocket $label Close called")
        inner.close()
    }

    override val connectionEvents: Observable<ConnectionEvent>
        get() = inner.connectionEvents
            .doOnNext {
                when (it) {
                    is ConnectionEvent.Connected -> logger.d("WebSocket $label Connected")
                    is ConnectionEvent.Failure -> logger.e("WebSocket $label Failed with ${it.throwable}")
                    is ConnectionEvent.ClientDisconnect -> logger.e("WebSocket $label Client Disconnected")
                    ConnectionEvent.Authenticated -> logger.d("WebSocket $label Authenticated")
                }
            }

    override fun send(message: OUTGOING) {
        logger.v("WebSocket $label send $message")
        inner.send(message)
    }

    override val responses: Observable<INCOMING>
        get() = inner.responses
            .doOnNext {
                logger.v("WebSocket $label receive $it")
            }
}

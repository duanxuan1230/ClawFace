package com.openclaw.clawface.network

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for connecting to the ClawFace daemon.
 * Replaces UdpClient — same Flow-based interface but over WebSocket.
 */
class WsClient {

    companion object {
        private const val TAG = "WsClient"
    }

    /** Events emitted by the WebSocket connection. */
    sealed class Event {
        /** WebSocket handshake completed, connection is open. */
        object Connected : Event()
        /** A text message was received from the server. */
        data class Message(val text: String) : Event()
        /** Connection was closed (cleanly or not). */
        data class Closed(val reason: String) : Event()
        /** Connection failed with an error. */
        data class Failed(val error: Throwable) : Event()
    }

    private var webSocket: WebSocket? = null

    /**
     * Connect to the ClawFace daemon and emit events as a Flow.
     * The flow completes when the connection is closed or fails.
     *
     * @param url WebSocket URL, e.g. "ws://192.168.1.100:9527/ws"
     */
    fun connect(url: String): Flow<Event> = callbackFlow {
        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)    // no read timeout for long-lived connection
            .build()

        val request = Request.Builder().url(url).build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $url")
                trySend(Event.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(Event.Message(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closed: $code $reason")
                trySend(Event.Closed(reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failed: ${t.message}")
                trySend(Event.Failed(t))
                close()
            }
        })
        webSocket = ws

        awaitClose {
            Log.i(TAG, "Flow closing, shutting down WebSocket")
            ws.close(1000, "bye")
            client.dispatcher.executorService.shutdown()
        }
    }

    /** Send a text message to the server. */
    fun send(data: String): Boolean {
        return webSocket?.send(data) ?: false
    }

    fun close() {
        webSocket?.close(1000, "bye")
        webSocket = null
    }
}

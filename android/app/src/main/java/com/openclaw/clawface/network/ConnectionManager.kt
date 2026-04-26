package com.openclaw.clawface.network

import android.util.Log
import com.openclaw.clawface.config.AppConfig
import com.openclaw.clawface.protocol.Frame
import com.openclaw.clawface.protocol.FrameParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages WebSocket connection lifecycle: receiving frames,
 * disconnect detection, auto-reconnect, and standby detection.
 *
 * Replaces the old UDP-based manager. State transitions are now driven
 * by WebSocket events (onOpen/onClosed/onFailure) instead of heartbeat counting.
 */
class ConnectionManager {

    companion object {
        private const val TAG = "ConnectionManager"
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, OFFLINE
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Callback invoked on the main thread for each received data frame. */
    var onFrame: ((Frame) -> Unit)? = null

    /** Callback invoked when connection state changes. */
    var onStateChange: ((ConnectionState) -> Unit)? = null

    private var scope: CoroutineScope? = null
    private var wsClient: WsClient? = null
    private var wsUrl: String = ""

    // Standby tracking
    private var standbyJob: Job? = null
    private var lastFrameTime = 0L

    // Auto-reconnect
    private var reconnectJob: Job? = null
    private var reconnectDelay = AppConfig.RECONNECT_BASE_DELAY_MS

    fun connect(host: String, port: Int) {
        wsUrl = "ws://$host:$port/ws"
        reconnectDelay = AppConfig.RECONNECT_BASE_DELAY_MS

        disconnect()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startConnection()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        standbyJob?.cancel()
        scope?.cancel()
        scope = null
        wsClient?.close()
        wsClient = null
        updateState(ConnectionState.DISCONNECTED)
    }

    private fun startConnection() {
        updateState(ConnectionState.CONNECTING)

        val client = WsClient()
        wsClient = client

        scope?.launch {
            try {
                lastFrameTime = System.currentTimeMillis()
                startStandbyDetection()

                client.connect(wsUrl)
                    .collect { event ->
                        handleEvent(event)
                    }
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
                updateState(ConnectionState.OFFLINE)
                scheduleReconnect()
            }
        }
    }

    private fun handleEvent(event: WsClient.Event) {
        when (event) {
            is WsClient.Event.Connected -> {
                reconnectDelay = AppConfig.RECONNECT_BASE_DELAY_MS
                updateState(ConnectionState.CONNECTED)
            }
            is WsClient.Event.Message -> {
                handleMessage(event.text)
            }
            is WsClient.Event.Closed -> {
                Log.i(TAG, "Connection closed: ${event.reason}")
                updateState(ConnectionState.OFFLINE)
                scheduleReconnect()
            }
            is WsClient.Event.Failed -> {
                Log.e(TAG, "Connection failed: ${event.error.message}")
                updateState(ConnectionState.OFFLINE)
                scheduleReconnect()
            }
        }
    }

    private fun handleMessage(message: String) {
        val frame = FrameParser.parse(message)
        val now = System.currentTimeMillis()

        when (frame) {
            is Frame.HeartbeatAck, is Frame.Heartbeat, is Frame.Unknown -> {
                // Protocol frames — no action needed (WebSocket ping/pong handles keepalive)
            }
            else -> {
                // Data frame — update timestamp and dispatch
                lastFrameTime = now

                // Ensure CONNECTED state on data frame
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    updateState(ConnectionState.CONNECTED)
                }

                // Dispatch on main thread
                scope?.launch(Dispatchers.Main) {
                    onFrame?.invoke(frame)
                }
            }
        }
    }

    private fun startStandbyDetection() {
        standbyJob?.cancel()
        standbyJob = scope?.launch {
            while (isActive) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - lastFrameTime
                if (elapsed >= AppConfig.STANDBY_TIMEOUT_MS &&
                    _connectionState.value == ConnectionState.CONNECTED
                ) {
                    withContext(Dispatchers.Main) {
                        onStateChange?.invoke(ConnectionState.CONNECTED)
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope?.launch {
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2)
                .coerceAtMost(AppConfig.RECONNECT_MAX_DELAY_MS)
            Log.i(TAG, "Reconnecting (next delay: ${reconnectDelay}ms)")
            wsClient?.close()
            startConnection()
        }
    }

    private fun updateState(state: ConnectionState) {
        _connectionState.value = state
        scope?.launch(Dispatchers.Main) {
            onStateChange?.invoke(state)
        }
    }

    /** Returns milliseconds since last data frame was received. */
    fun getTimeSinceLastFrame(): Long {
        return System.currentTimeMillis() - lastFrameTime
    }
}

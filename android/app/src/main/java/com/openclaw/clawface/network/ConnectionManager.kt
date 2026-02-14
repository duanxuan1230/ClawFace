package com.openclaw.clawface.network

import android.util.Log
import com.openclaw.clawface.config.AppConfig
import com.openclaw.clawface.protocol.Frame
import com.openclaw.clawface.protocol.FrameParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages UDP connection lifecycle: receiving frames, heartbeat,
 * disconnect detection, auto-reconnect, and standby detection.
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
    private var udpClient: UdpClient? = null
    private var host: String = ""
    private var port: Int = AppConfig.DEFAULT_PORT

    // Heartbeat tracking
    private var heartbeatJob: Job? = null
    private var missedHeartbeats = 0
    private var lastDataTime = 0L

    // Standby tracking
    private var standbyJob: Job? = null
    private var lastFrameTime = 0L

    // Auto-reconnect
    private var reconnectJob: Job? = null
    private var reconnectDelay = AppConfig.RECONNECT_BASE_DELAY_MS

    fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
        reconnectDelay = AppConfig.RECONNECT_BASE_DELAY_MS

        disconnect()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startConnection()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        standbyJob?.cancel()
        scope?.cancel()
        scope = null
        udpClient?.close()
        udpClient = null
        updateState(ConnectionState.DISCONNECTED)
    }

    private fun startConnection() {
        updateState(ConnectionState.CONNECTING)

        val client = UdpClient()
        udpClient = client

        scope?.launch {
            try {
                lastFrameTime = System.currentTimeMillis()
                lastDataTime = System.currentTimeMillis()
                missedHeartbeats = 0

                updateState(ConnectionState.CONNECTED)
                startHeartbeat()
                startStandbyDetection()

                client.receiveFlow(port)
                    .flowOn(Dispatchers.IO)
                    .collect { message ->
                        handleMessage(message)
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

    private fun handleMessage(message: String) {
        val frame = FrameParser.parse(message)
        val now = System.currentTimeMillis()

        when (frame) {
            is Frame.HeartbeatAck -> {
                missedHeartbeats = 0
                lastDataTime = now
            }
            is Frame.Heartbeat -> {
                missedHeartbeats = 0
                lastDataTime = now
                // Respond with ack
                scope?.launch {
                    udpClient?.send(host, port, """{"type":"heartbeat_ack"}""")
                }
            }
            is Frame.Unknown -> {
                // Ignore unknown frames
            }
            else -> {
                // Data frame — update timestamps and dispatch
                lastDataTime = now
                lastFrameTime = now

                // Ensure ACTIVE state on data frame
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

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope?.launch {
            while (isActive) {
                delay(AppConfig.HEARTBEAT_INTERVAL_MS)
                try {
                    udpClient?.send(host, port, """{"type":"heartbeat"}""")
                    missedHeartbeats++

                    if (missedHeartbeats >= AppConfig.HEARTBEAT_TIMEOUT_COUNT) {
                        Log.w(TAG, "Heartbeat timeout ($missedHeartbeats missed)")
                        updateState(ConnectionState.OFFLINE)
                        // Don't reconnect — socket is still open, server may come back
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat send error: ${e.message}")
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
                    // Notify via onStateChange but don't change connectionState
                    // The service will handle STANDBY mode separately
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
            udpClient?.close()
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

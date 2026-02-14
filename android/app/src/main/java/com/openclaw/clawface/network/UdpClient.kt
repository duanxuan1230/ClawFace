package com.openclaw.clawface.network

import android.util.Log
import com.openclaw.clawface.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP socket wrapper with Coroutines Flow for receiving and suspend for sending.
 */
class UdpClient {

    private var socket: DatagramSocket? = null

    companion object {
        private const val TAG = "UdpClient"
    }

    /**
     * Opens a UDP socket on [port] and emits received strings as a Flow.
     * The flow completes when [close] is called or an error occurs.
     */
    fun receiveFlow(port: Int): Flow<String> = callbackFlow {
        val sock = DatagramSocket(port)
        socket = sock
        Log.i(TAG, "Listening on UDP port $port")

        val buffer = ByteArray(AppConfig.UDP_BUFFER_SIZE)

        try {
            while (!sock.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                sock.receive(packet) // blocks on IO thread
                val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                trySend(message)
            }
        } catch (e: Exception) {
            if (!sock.isClosed) {
                Log.e(TAG, "Receive error: ${e.message}")
            }
        } finally {
            sock.close()
        }

        awaitClose {
            Log.i(TAG, "Closing UDP socket")
            sock.close()
        }
    }

    /**
     * Send a string to the specified host:port via UDP.
     */
    suspend fun send(host: String, port: Int, data: String) = withContext(Dispatchers.IO) {
        try {
            val bytes = data.toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(bytes, bytes.size, address, port)
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }
}

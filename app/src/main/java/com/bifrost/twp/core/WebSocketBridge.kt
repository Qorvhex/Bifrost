package com.bifrost.twp.core

import android.net.Uri
import com.bifrost.twp.model.ProxyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bidirectional bridge between a Telegram SOCKS5 client TCP socket and
 * the Cloudflare Worker WebSocket endpoint (TWP protocol).
 *
 * MTProto packets received on the TCP socket are packed into binary WebSocket frames.
 * Binary frames returned from the Worker are unpacked and written back to the TCP socket.
 */
class WebSocketBridge(
    private val clientSocket: Socket,
    private val targetIp: String,
    private val targetPort: Int,
    private val proxyConfig: ProxyConfig,
    private val scope: CoroutineScope,
    private val onCloseCallback: () -> Unit
) {
    private val isClosed = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var clientReaderJob: Job? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    fun start() {
        val customDns = CleanIpDns(proxyConfig.workerHost, proxyConfig.cleanIp)

        val okHttpClient = OkHttpClient.Builder()
            .dns(customDns)
            .protocols(listOf(Protocol.HTTP_1_1))
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for WebSocket
            .build()

        // Build WebSocket URL: wss://<workerHost>[:port]/?ip=<targetIp>&port=<targetPort>[&secret=<secret>]
        val urlBuilder = StringBuilder("wss://").append(proxyConfig.workerHost.trim())
        if (proxyConfig.port != 443) {
            urlBuilder.append(":").append(proxyConfig.port)
        }
        urlBuilder.append("/?ip=").append(Uri.encode(targetIp))
        urlBuilder.append("&port=").append(targetPort)

        if (!proxyConfig.secret.isNullOrBlank()) {
            urlBuilder.append("&secret=").append(Uri.encode(proxyConfig.secret.trim()))
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.toString())
            .header("Host", proxyConfig.workerHost.trim())

        if (!proxyConfig.secret.isNullOrBlank()) {
            requestBuilder.header("X-Worker-Secret", proxyConfig.secret.trim())
        }

        val request = requestBuilder.build()

        try {
            inputStream = clientSocket.getInputStream()
            outputStream = clientSocket.getOutputStream()
        } catch (e: Exception) {
            close()
            return
        }

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // WebSocket connection established. Start streaming client TCP -> WebSocket
                startClientToWsPiping(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Received MTProto packet frame from Cloudflare Worker -> write to Telegram TCP
                try {
                    synchronized(this@WebSocketBridge) {
                        if (!isClosed.get()) {
                            outputStream?.write(bytes.toByteArray())
                            outputStream?.flush()
                        }
                    }
                } catch (e: Exception) {
                    close()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close()
            }
        })
    }

    private fun startClientToWsPiping(ws: WebSocket) {
        clientReaderJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(16384) // 16 KB read buffer
            try {
                val input = inputStream ?: return@launch
                while (isActive && !isClosed.get()) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) {
                        // Client closed stream
                        break
                    }
                    if (bytesRead > 0) {
                        val byteString = buffer.toByteString(0, bytesRead)
                        val sent = ws.send(byteString)
                        if (!sent) {
                            // Queue overflow or socket closed
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Expected when socket closes
            } finally {
                close()
            }
        }
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            clientReaderJob?.cancel()
            try {
                webSocket?.close(1000, "Bridge closed")
            } catch (_: Exception) {}
            try {
                inputStream?.close()
            } catch (_: Exception) {}
            try {
                outputStream?.close()
            } catch (_: Exception) {}
            try {
                clientSocket.close()
            } catch (_: Exception) {}

            onCloseCallback()
        }
    }
}

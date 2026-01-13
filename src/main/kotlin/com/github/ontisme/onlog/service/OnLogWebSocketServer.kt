package com.github.ontisme.onlog.service

import com.github.ontisme.onlog.model.LogEntry
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArraySet

/**
 * WebSocket server that external applications connect to for sending logs.
 * Listens on port 19199 by default.
 */
class OnLogWebSocketServer(
    port: Int = DEFAULT_PORT,
    private val listener: OnLogServerListener
) : WebSocketServer(InetSocketAddress("127.0.0.1", port)), Disposable {

    private val gson = Gson()
    private val connectedClients = CopyOnWriteArraySet<WebSocket>()

    interface OnLogServerListener {
        fun onClientConnected(clientId: String)
        fun onClientDisconnected(clientId: String)
        fun onLogsReceived(entries: List<LogEntry>)
        fun onMetadataReceived(sources: Set<String>, categories: Set<String>)
    }

    override fun onStart() {
        LOG.info("OnLog WebSocket server started on port $port")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val clientId = conn.remoteSocketAddress?.toString() ?: "unknown"
        LOG.info("Client connected: $clientId, path=${handshake.resourceDescriptor}")
        connectedClients.add(conn)
        listener.onClientConnected(clientId)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val clientId = conn.remoteSocketAddress?.toString() ?: "unknown"
        LOG.info("Client disconnected: $clientId, reason=$reason")
        connectedClients.remove(conn)
        listener.onClientDisconnected(clientId)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return

            when (type) {
                "app_init" -> handleAppInit(json)
                "logs" -> handleLogs(json)
                else -> LOG.debug("Unknown message type: $type")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse message: $message", e)
        }
    }

    private fun handleAppInit(json: JsonObject) {
        val data = json.getAsJsonObject("data") ?: return

        val sources = mutableSetOf<String>()
        val categories = mutableSetOf<String>()

        data.getAsJsonArray("sources")?.forEach { element ->
            sources.add(element.asString)
        }
        data.getAsJsonArray("categories")?.forEach { element ->
            categories.add(element.asString)
        }

        if (sources.isNotEmpty() || categories.isNotEmpty()) {
            listener.onMetadataReceived(sources, categories)
        }
    }

    private fun handleLogs(json: JsonObject) {
        val data = json.getAsJsonObject("data") ?: return
        val entriesArray = data.getAsJsonArray("entries") ?: return

        val entries = mutableListOf<LogEntry>()
        for (element in entriesArray) {
            try {
                val entry = LogEntry.fromJson(element.asJsonObject)
                entries.add(entry)
            } catch (e: Exception) {
                LOG.debug("Failed to parse log entry: $element", e)
            }
        }

        if (entries.isNotEmpty()) {
            listener.onLogsReceived(entries)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        val clientId = conn?.remoteSocketAddress?.toString() ?: "server"
        LOG.warn("WebSocket error from $clientId", ex)
    }

    fun getConnectedClientCount(): Int = connectedClients.size

    override fun dispose() {
        try {
            stop(1000)
            LOG.info("OnLog WebSocket server stopped")
        } catch (e: Exception) {
            LOG.warn("Error stopping WebSocket server", e)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(OnLogWebSocketServer::class.java)
        const val DEFAULT_PORT = 19199
    }
}

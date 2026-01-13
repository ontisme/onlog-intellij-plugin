package com.github.ontisme.onlog.model

import com.google.gson.JsonElement

/**
 * WebSocket message types.
 */
object MsgType {
    const val INIT = "init"
    const val SUBSCRIBE = "subscribe"
    const val HISTORY = "history"
    const val LOGS = "logs"
    const val META = "meta"
    const val ERROR = "error"
}

/**
 * Base WebSocket message.
 */
data class WsMessage(
    val type: String,
    val data: JsonElement? = null
)

/**
 * Init message from server.
 */
data class InitMessage(
    val port: Int,
    val sources: List<String>,
    val categories: List<String>,
    val bufferSize: Int,
    val bufferUsed: Int
)

/**
 * Subscribe message to server.
 */
data class SubscribeMessage(
    val filter: SubscribeFilter
)

data class SubscribeFilter(
    val sources: List<String>? = null,
    val categories: List<String>? = null,
    val minLevel: String? = null,
    val tags: List<String>? = null
)

/**
 * History request to server.
 */
data class HistoryMessage(
    val filter: SubscribeFilter? = null,
    val limit: Int = 1000
)

/**
 * Logs message from server.
 */
data class LogsMessage(
    val entries: List<Map<String, Any>>
)

/**
 * Connection state.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

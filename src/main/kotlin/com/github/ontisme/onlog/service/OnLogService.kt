package com.github.ontisme.onlog.service

import com.github.ontisme.onlog.model.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Main service for managing onlog connections and log entries.
 * Supports both:
 * 1. WebSocket server mode (external apps connect to IDE)
 * 2. Console stdout parsing (IDE-internal execution)
 */
@Service(Service.Level.PROJECT)
class OnLogService(private val project: Project) : Disposable, OnLogWebSocketServer.OnLogServerListener {

    private var wsServer: OnLogWebSocketServer? = null
    private val entries = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<OnLogServiceListener>()

    // Metadata
    private val sources = CopyOnWriteArraySet<String>()
    private val categories = CopyOnWriteArraySet<String>()
    private val allTags = CopyOnWriteArraySet<String>()

    // Source-specific metadata (source -> categories/tags)
    private val sourceMetadata = ConcurrentHashMap<String, SourceMeta>()

    /**
     * Metadata for a specific source.
     */
    data class SourceMeta(
        val categories: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val tags: MutableSet<String> = ConcurrentHashMap.newKeySet()
    )

    // State
    var connectionState = ConnectionState.DISCONNECTED
        private set
    var currentPort: Int? = null
        private set
    var filter = LogFilter()
        private set
    var connectedClients = 0
        private set

    // Buffer limit
    private val maxEntries = 50000

    interface OnLogServiceListener {
        fun onConnectionStateChanged(state: ConnectionState)
        fun onLogsReceived(entries: List<LogEntry>)
        fun onLogsCleared()
        fun onMetadataUpdated(
            sources: Set<String>,
            categories: Set<String>,
            tags: Set<String>,
            sourceMetadata: Map<String, SourceMeta>
        )
        fun onFilterChanged(filter: LogFilter)
    }

    init {
        // Auto-start WebSocket server
        startServer()
    }

    /**
     * Start WebSocket server on default port.
     */
    fun startServer(port: Int = OnLogWebSocketServer.DEFAULT_PORT) {
        if (wsServer != null) {
            stopServer()
        }

        LOG.info("Starting OnLog WebSocket server on port $port")
        currentPort = port

        try {
            wsServer = OnLogWebSocketServer(port, this)
            wsServer?.start()
            connectionState = ConnectionState.CONNECTED
            notifyConnectionStateChanged()
        } catch (e: Exception) {
            LOG.warn("Failed to start WebSocket server", e)
            connectionState = ConnectionState.ERROR
            notifyConnectionStateChanged()
        }
    }

    /**
     * Stop WebSocket server.
     */
    fun stopServer() {
        LOG.info("Stopping OnLog WebSocket server")
        wsServer?.dispose()
        wsServer = null
        connectionState = ConnectionState.DISCONNECTED
        connectedClients = 0
        notifyConnectionStateChanged()
    }

    /**
     * Clear all log entries.
     */
    fun clearLogs() {
        entries.clear()
        sources.clear()
        categories.clear()
        allTags.clear()
        sourceMetadata.clear()
        notifyLogsCleared()
        notifyMetadataUpdated()
    }

    /**
     * Update filter and notify listeners.
     */
    fun updateFilter(newFilter: LogFilter) {
        filter = newFilter
        listeners.forEach { it.onFilterChanged(filter) }
    }

    /**
     * Add log entries from stdout parsing.
     * Called by ConsoleFilterProvider when JSON log lines are detected.
     */
    fun addStdoutLogs(newEntries: List<LogEntry>) {
        if (newEntries.isEmpty()) return

        ApplicationManager.getApplication().invokeLater {
            processNewEntries(newEntries)
        }
    }

    /**
     * Get all entries (optionally filtered).
     */
    fun getEntries(filtered: Boolean = true): List<LogEntry> {
        return if (filtered && !filter.isEmpty) {
            entries.filter { filter.matches(it) }
        } else {
            entries.toList()
        }
    }

    /**
     * Get current metadata.
     */
    fun getSources(): Set<String> = sources.toSet()
    fun getCategories(): Set<String> = categories.toSet()
    fun getTags(): Set<String> = allTags.toSet()
    fun getSourceMetadata(): Map<String, SourceMeta> = sourceMetadata.toMap()

    fun addListener(listener: OnLogServiceListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnLogServiceListener) {
        listeners.remove(listener)
    }

    // OnLogServerListener implementation (WebSocket server callbacks)

    override fun onClientConnected(clientId: String) {
        LOG.info("Client connected: $clientId")
        ApplicationManager.getApplication().invokeLater {
            connectedClients = wsServer?.getConnectedClientCount() ?: 0
            notifyConnectionStateChanged()
        }
    }

    override fun onClientDisconnected(clientId: String) {
        LOG.info("Client disconnected: $clientId")
        ApplicationManager.getApplication().invokeLater {
            connectedClients = wsServer?.getConnectedClientCount() ?: 0
            notifyConnectionStateChanged()
        }
    }

    override fun onLogsReceived(newEntries: List<LogEntry>) {
        ApplicationManager.getApplication().invokeLater {
            processNewEntries(newEntries)
        }
    }

    override fun onMetadataReceived(sources: Set<String>, categories: Set<String>) {
        ApplicationManager.getApplication().invokeLater {
            var changed = false
            sources.forEach { if (this.sources.add(it)) changed = true }
            categories.forEach { if (this.categories.add(it)) changed = true }
            if (changed) {
                notifyMetadataUpdated()
            }
        }
    }

    private fun processNewEntries(newEntries: List<LogEntry>) {
        // Add new entries
        entries.addAll(newEntries)

        // Trim if exceeds max
        while (entries.size > maxEntries) {
            entries.removeAt(0)
        }

        // Update metadata
        var metadataChanged = false
        newEntries.forEach { entry ->
            if (sources.add(entry.src)) metadataChanged = true
            entry.cat?.let { if (categories.add(it)) metadataChanged = true }
            entry.tags.forEach { if (allTags.add(it)) metadataChanged = true }

            // Track source-specific metadata
            val sourceMeta = sourceMetadata.getOrPut(entry.src) { SourceMeta() }
            entry.cat?.let { if (sourceMeta.categories.add(it)) metadataChanged = true }
            entry.tags.forEach { if (sourceMeta.tags.add(it)) metadataChanged = true }
        }

        // Notify listeners
        notifyLogsReceived(newEntries)
        if (metadataChanged) {
            notifyMetadataUpdated()
        }
    }

    private fun notifyConnectionStateChanged() {
        listeners.forEach { it.onConnectionStateChanged(connectionState) }
    }

    private fun notifyLogsReceived(newEntries: List<LogEntry>) {
        listeners.forEach { it.onLogsReceived(newEntries) }
    }

    private fun notifyLogsCleared() {
        listeners.forEach { it.onLogsCleared() }
    }

    private fun notifyMetadataUpdated() {
        listeners.forEach {
            it.onMetadataUpdated(
                sources.toSet(),
                categories.toSet(),
                allTags.toSet(),
                sourceMetadata.toMap()
            )
        }
    }

    override fun dispose() {
        stopServer()
        listeners.clear()
    }

    companion object {
        private val LOG = Logger.getInstance(OnLogService::class.java)

        fun getInstance(project: Project): OnLogService {
            return project.getService(OnLogService::class.java)
        }
    }
}

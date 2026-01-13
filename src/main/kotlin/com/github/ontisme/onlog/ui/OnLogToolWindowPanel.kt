package com.github.ontisme.onlog.ui

import com.github.ontisme.onlog.model.*
import com.github.ontisme.onlog.service.OnLogService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * Main panel for the OnLog tool window.
 * Simple layout: Source tree on left, filter + console on right.
 *
 * For multiple views, use IntelliJ's native tool window features
 * (drag to new location, right-click to duplicate).
 */
class OnLogToolWindowPanel(
    private val project: Project,
    private val service: OnLogService
) : SimpleToolWindowPanel(true, true), Disposable, OnLogService.OnLogServiceListener {

    private val filterPanel: FilterPanel
    private val sourceTree: SourceTree
    private val logConsole: LogConsole
    private val statusLabel: JBLabel
    private val followButton: JToggleButton
    private lateinit var logScrollPane: JBScrollPane

    // Local filter
    private var localFilter = LogFilter()

    init {
        // Create components
        filterPanel = FilterPanel { filter ->
            localFilter = filter
            applyFilter()
        }

        sourceTree = SourceTree { selection ->
            filterPanel.updateSourceSelection(selection)
        }

        logConsole = LogConsole(project)
        statusLabel = JBLabel("Ready")

        // Follow button
        followButton = JToggleButton("Follow", true).apply {
            icon = AllIcons.RunConfigurations.Scroll_down
            isSelected = true
            addActionListener {
                logConsole.setAutoScroll(isSelected)
            }
        }

        // Setup layout
        setupLayout()

        // Setup toolbar
        setupToolbar()

        // Register listener
        service.addListener(this)

        // Setup auto-scroll listener
        logConsole.setAutoScrollListener { enabled ->
            SwingUtilities.invokeLater {
                followButton.isSelected = enabled
            }
        }

        // Initial state
        updateStatus()

        // Initial data load
        loadInitialData()
    }

    private fun setupLayout() {
        // === Left Panel: Source Tree ===
        val sourceHeader = JPanel(BorderLayout()).apply {
            add(JBLabel("Sources").apply {
                border = JBUI.Borders.empty(4, 8)
            }, BorderLayout.WEST)

            // Select All / Deselect All buttons
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                add(JButton(AllIcons.Actions.Selectall).apply {
                    toolTipText = "Select All"
                    preferredSize = JBUI.size(24, 24)
                    isFocusable = false
                    addActionListener { sourceTree.selectAll() }
                })
                add(JButton(AllIcons.Actions.Unselectall).apply {
                    toolTipText = "Deselect All"
                    preferredSize = JBUI.size(24, 24)
                    isFocusable = false
                    addActionListener { sourceTree.deselectAll() }
                })
            }
            add(buttonPanel, BorderLayout.EAST)
        }

        val leftPanel = JPanel(BorderLayout()).apply {
            add(sourceHeader, BorderLayout.NORTH)
            add(JBScrollPane(sourceTree), BorderLayout.CENTER)
            preferredSize = JBUI.size(200, -1)
            minimumSize = JBUI.size(150, -1)
        }

        // === Right Panel: Filter + Console ===
        logScrollPane = JBScrollPane(logConsole)
        logConsole.setupScrollListener(logScrollPane)

        // Bottom bar with Follow button and status
        val bottomBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)

            // Left: Follow button
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(followButton)
            }, BorderLayout.WEST)

            // Right: Status
            add(statusLabel, BorderLayout.EAST)
        }

        val rightPanel = JPanel(BorderLayout()).apply {
            add(filterPanel, BorderLayout.NORTH)
            add(logScrollPane, BorderLayout.CENTER)
            add(bottomBar, BorderLayout.SOUTH)
        }

        // === Main Splitter ===
        val splitter = JBSplitter(false, 0.18f).apply {
            firstComponent = leftPanel
            secondComponent = rightPanel
            dividerWidth = 3
        }

        setContent(splitter)
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup()

        // Add existing actions from plugin.xml (Clear, etc.)
        val existingGroup = ActionManager.getInstance().getAction("OnLog.ToolbarActions") as? DefaultActionGroup
        existingGroup?.let {
            it.childActionsOrStubs.forEach { action ->
                actionGroup.add(action)
            }
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "OnLogToolbar",
            actionGroup,
            true
        )
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
    }

    private fun loadInitialData() {
        val entries = service.getEntries(false)
        logConsole.setEntries(entries.filter { localFilter.matches(it) })
        sourceTree.updateMetadata(service.getSourceMetadata())
        filterPanel.updateTags(service.getTags())
    }

    private fun applyFilter() {
        val entries = service.getEntries(false)
        logConsole.setEntries(entries.filter { localFilter.matches(it) })
    }

    private fun updateStatus() {
        val logCount = logConsole.getEntryCount()
        statusLabel.text = "$logCount logs"
        statusLabel.icon = AllIcons.General.InspectionsOK
    }

    // === OnLogServiceListener ===

    override fun onConnectionStateChanged(state: ConnectionState) {
        SwingUtilities.invokeLater { updateStatus() }
    }

    override fun onLogsReceived(entries: List<LogEntry>) {
        SwingUtilities.invokeLater {
            val filtered = entries.filter { localFilter.matches(it) }
            if (filtered.isNotEmpty()) {
                logConsole.addEntries(filtered, localFilter)
            }
            updateStatus()
        }
    }

    override fun onLogsCleared() {
        SwingUtilities.invokeLater {
            logConsole.clear()
            sourceTree.clearTree()
            filterPanel.updateTags(emptySet())
            updateStatus()
        }
    }

    override fun onMetadataUpdated(
        sources: Set<String>,
        categories: Set<String>,
        tags: Set<String>,
        sourceMetadata: Map<String, OnLogService.SourceMeta>
    ) {
        SwingUtilities.invokeLater {
            sourceTree.updateMetadata(sourceMetadata)
            filterPanel.updateTags(tags)
        }
    }

    override fun onFilterChanged(filter: LogFilter) {
        // Using local filter, no-op
    }

    override fun dispose() {
        service.removeListener(this)
    }
}

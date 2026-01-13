package com.github.ontisme.onlog.ui

import com.github.ontisme.onlog.model.LogEntry
import com.github.ontisme.onlog.model.LogFilter
import com.github.ontisme.onlog.model.LogLevel
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Log console table with colored output, caller navigation, and smart auto-scroll.
 */
class LogConsole(private val project: Project) : JBTable() {

    private val tableModel = LogTableModel()
    private var autoScroll = true
    private var autoScrollListener: ((Boolean) -> Unit)? = null

    // Column indices
    companion object {
        const val COL_TIME = 0
        const val COL_LEVEL = 1
        const val COL_SOURCE = 2
        const val COL_CATEGORY = 3
        const val COL_CALLER = 4
        const val COL_MESSAGE = 5
        const val COL_FIELDS = 6
        const val COL_TAGS = 7
    }

    init {
        model = tableModel
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setShowGrid(false)
        rowHeight = JBUI.scale(20)
        tableHeader.reorderingAllowed = false

        // Set column widths
        columnModel.getColumn(COL_TIME).preferredWidth = 90
        columnModel.getColumn(COL_TIME).maxWidth = 100
        columnModel.getColumn(COL_LEVEL).preferredWidth = 40
        columnModel.getColumn(COL_LEVEL).maxWidth = 50
        columnModel.getColumn(COL_SOURCE).preferredWidth = 80
        columnModel.getColumn(COL_SOURCE).maxWidth = 120
        columnModel.getColumn(COL_CATEGORY).preferredWidth = 60
        columnModel.getColumn(COL_CATEGORY).maxWidth = 100
        columnModel.getColumn(COL_CALLER).preferredWidth = 100
        columnModel.getColumn(COL_CALLER).maxWidth = 150
        columnModel.getColumn(COL_MESSAGE).preferredWidth = 400
        columnModel.getColumn(COL_FIELDS).preferredWidth = 200
        columnModel.getColumn(COL_TAGS).preferredWidth = 100

        // Custom renderer
        val renderer = LogCellRenderer()
        for (i in 0 until columnCount) {
            columnModel.getColumn(i).cellRenderer = renderer
        }

        // Add mouse listener for caller navigation
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = rowAtPoint(e.point)
                val col = columnAtPoint(e.point)
                if (row >= 0 && col == COL_CALLER) {
                    val entry = tableModel.getEntry(row)
                    entry?.caller?.let { navigateToCaller(it) }
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val col = columnAtPoint(e.point)
                cursor = if (col == COL_CALLER) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }
        })

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val col = columnAtPoint(e.point)
                cursor = if (col == COL_CALLER) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }
        })
    }

    /**
     * Navigate to caller location (file:line).
     */
    private fun navigateToCaller(caller: String) {
        val parts = caller.split(":")
        if (parts.size != 2) return

        val fileName = parts[0]
        val lineNumber = parts[1].toIntOrNull() ?: return

        // Search for the file in project
        val files = FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))

        val file = files.firstOrNull()
        if (file != null) {
            // Navigate to file:line (line is 0-indexed in OpenFileDescriptor)
            OpenFileDescriptor(project, file, lineNumber - 1, 0).navigate(true)
        }
    }

    /**
     * Setup scroll listener after being added to a JScrollPane.
     */
    fun setupScrollListener(scrollPane: JScrollPane) {
        val verticalBar = scrollPane.verticalScrollBar

        verticalBar.addAdjustmentListener(object : AdjustmentListener {
            private var previousValue = 0
            private var previousMax = 0

            override fun adjustmentValueChanged(e: AdjustmentEvent) {
                val scrollBar = e.adjustable
                val currentValue = scrollBar.value
                val currentMax = scrollBar.maximum - scrollBar.visibleAmount
                val isAtBottom = currentValue >= currentMax - 10

                // Detect user scrolling up (not programmatic scroll)
                if (!e.valueIsAdjusting && currentMax == previousMax) {
                    if (currentValue < previousValue && autoScroll) {
                        autoScroll = false
                        autoScrollListener?.invoke(false)
                    }
                }

                // Re-enable auto-scroll when user scrolls to bottom
                if (isAtBottom && !autoScroll) {
                    autoScroll = true
                    autoScrollListener?.invoke(true)
                }

                previousValue = currentValue
                previousMax = currentMax
            }
        })
    }

    fun setAutoScrollListener(listener: (Boolean) -> Unit) {
        autoScrollListener = listener
    }

    fun addEntries(entries: List<LogEntry>, filter: LogFilter) {
        val filtered = if (filter.isEmpty) entries else entries.filter { filter.matches(it) }
        if (filtered.isEmpty()) return

        tableModel.addEntries(filtered)

        if (autoScroll) {
            SwingUtilities.invokeLater { scrollToBottom() }
        }
    }

    fun setEntries(entries: List<LogEntry>) {
        tableModel.setEntries(entries)

        if (autoScroll) {
            SwingUtilities.invokeLater { scrollToBottom() }
        }
    }

    fun clear() {
        tableModel.clear()
    }

    fun isAutoScroll(): Boolean = autoScroll

    fun setAutoScroll(enabled: Boolean) {
        autoScroll = enabled
        if (enabled) {
            SwingUtilities.invokeLater { scrollToBottom() }
        }
    }

    fun getEntryCount(): Int = tableModel.rowCount

    private fun scrollToBottom() {
        val lastRow = rowCount - 1
        if (lastRow >= 0) {
            scrollRectToVisible(getCellRect(lastRow, 0, true))
        }
    }

    /**
     * Table model for log entries.
     */
    private class LogTableModel : AbstractTableModel() {
        private val entries = mutableListOf<LogEntry>()
        private val columns = arrayOf("Time", "Level", "Source", "Category", "Caller", "Message", "Fields", "Tags")

        override fun getRowCount(): Int = entries.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            if (rowIndex >= entries.size) return null
            val entry = entries[rowIndex]

            return when (columnIndex) {
                COL_TIME -> entry.formattedTime
                COL_LEVEL -> entry.lvl
                COL_SOURCE -> entry.src
                COL_CATEGORY -> entry.cat ?: ""
                COL_CALLER -> entry.caller ?: ""
                COL_MESSAGE -> entry.msg
                COL_FIELDS -> formatFields(entry.fields)
                COL_TAGS -> entry.tags.joinToString(", ")
                else -> null
            }
        }

        fun getEntry(row: Int): LogEntry? = entries.getOrNull(row)

        fun addEntries(newEntries: List<LogEntry>) {
            if (newEntries.isEmpty()) return
            val start = entries.size
            entries.addAll(newEntries)
            fireTableRowsInserted(start, entries.size - 1)
        }

        fun setEntries(newEntries: List<LogEntry>) {
            entries.clear()
            entries.addAll(newEntries)
            fireTableDataChanged()
        }

        fun clear() {
            entries.clear()
            fireTableDataChanged()
        }

        private fun formatFields(fields: Map<String, Any>): String {
            return fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
        }
    }

    /**
     * Cell renderer with level-based coloring and caller link styling.
     */
    private inner class LogCellRenderer : DefaultTableCellRenderer() {
        private val linkColor = JBColor(Color(0, 102, 204), Color(88, 166, 255))

        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            if (!isSelected) {
                val entry = tableModel.getEntry(row)
                val bgColor = when (entry?.level) {
                    LogLevel.ERROR -> JBColor(Color(255, 235, 235), Color(80, 40, 40))
                    LogLevel.WARN -> JBColor(Color(255, 250, 230), Color(80, 70, 40))
                    else -> table?.background ?: background
                }
                background = bgColor

                // Level column color
                when (column) {
                    COL_LEVEL -> {
                        foreground = when (entry?.level) {
                            LogLevel.DEBUG -> JBColor.GRAY
                            LogLevel.INFO -> JBColor(Color(0, 128, 0), Color(100, 200, 100))
                            LogLevel.WARN -> JBColor(Color(200, 150, 0), Color(255, 200, 100))
                            LogLevel.ERROR -> JBColor.RED
                            else -> foreground
                        }
                    }
                    COL_CALLER -> {
                        // Style caller as a link
                        if (entry?.caller != null) {
                            foreground = linkColor
                            text = "<html><u>${entry.caller}</u></html>"
                        } else {
                            foreground = JBColor.GRAY
                        }
                    }
                    else -> {
                        foreground = table?.foreground ?: foreground
                    }
                }
            }

            border = JBUI.Borders.empty(0, 4)

            return component
        }
    }
}

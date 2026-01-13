package com.github.ontisme.onlog.ui

import com.github.ontisme.onlog.model.LogFilter
import com.github.ontisme.onlog.model.LogLevel
import com.github.ontisme.onlog.model.SourceSelection
import com.intellij.icons.AllIcons
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import java.awt.event.ActionListener

/**
 * Filter panel with multi-select level checkboxes, tag input, and search field.
 */
class FilterPanel(
    private val onFilterChanged: (LogFilter) -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)) {

    // Level checkboxes (multi-select)
    private val levelCheckboxes: Map<LogLevel, JCheckBox>
    private val followButton: JToggleButton
    private val tagField: SearchTextField
    private val searchField: SearchTextField
    private var availableTags: Set<String> = emptySet()

    private var currentFilter = LogFilter()
    private var currentSourceSelection: SourceSelection? = null

    init {
        border = JBUI.Borders.empty(4, 8)

        // Level label
        add(JLabel("等級:"))

        // Level checkboxes (all checked by default)
        levelCheckboxes = LogLevel.entries.associateWith { level ->
            JCheckBox(level.label).apply {
                isSelected = true
                foreground = when (level) {
                    LogLevel.DEBUG -> JBColor(0x0066CC, 0x58A6FF)  // 藍色
                    LogLevel.INFO -> JBColor(0x3B7A3B, 0x6AAF6A)  // Green
                    LogLevel.WARN -> JBColor(0xB58900, 0xD4A017)  // Yellow/Orange
                    LogLevel.ERROR -> JBColor(0xB71C1C, 0xE57373) // Red
                }
                addActionListener { updateFilter() }
            }
        }
        levelCheckboxes.values.forEach { add(it) }

        // Follow button (跟隨) - right after level checkboxes
        followButton = JToggleButton().apply {
            icon = AllIcons.RunConfigurations.Scroll_down
            isSelected = true
            toolTipText = "跟隨"
            preferredSize = JBUI.size(28, 24)
            isFocusable = false
        }
        add(followButton)

        add(Box.createHorizontalStrut(16))

        // Tags filter
        add(JLabel("標籤:"))
        tagField = SearchTextField(false).apply {
            textEditor.emptyText.text = "例如: error,slow"
            textEditor.columns = 15
            addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    updateFilter()
                }
            })
        }
        add(tagField)

        add(Box.createHorizontalStrut(16))

        // Search
        add(JLabel(AllIcons.Actions.Search))
        searchField = SearchTextField(true).apply {
            textEditor.emptyText.text = "搜尋日誌..."
            textEditor.columns = 20
            addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    updateFilter()
                }
            })
        }
        add(searchField)
    }

    private fun updateFilter() {
        // Collect selected levels
        val selectedLevels = levelCheckboxes
            .filterValues { it.isSelected }
            .keys
            .toSet()

        // Use all levels if none selected (edge case)
        val levels = if (selectedLevels.isEmpty()) LogLevel.entries.toSet() else selectedLevels

        val tags = tagField.text
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val search = searchField.text.trim()

        currentFilter = currentFilter.copy(
            levels = levels,
            tags = tags,
            search = search,
            sourceSelection = currentSourceSelection
        )

        onFilterChanged(currentFilter)
    }

    /**
     * Update the source selection from the SourceTree.
     */
    fun updateSourceSelection(selection: SourceSelection) {
        currentSourceSelection = selection
        currentFilter = currentFilter.copy(sourceSelection = selection)
        onFilterChanged(currentFilter)
    }

    fun updateTags(tags: Set<String>) {
        availableTags = tags
        // Could add autocomplete here
    }

    fun getFilter(): LogFilter = currentFilter

    /**
     * Get selected levels.
     */
    fun getSelectedLevels(): Set<LogLevel> = levelCheckboxes
        .filterValues { it.isSelected }
        .keys
        .toSet()

    /**
     * Set selected levels programmatically.
     */
    fun setSelectedLevels(levels: Set<LogLevel>) {
        levelCheckboxes.forEach { (level, checkbox) ->
            checkbox.isSelected = level in levels
        }
    }

    /**
     * Add listener to follow button.
     */
    fun addFollowButtonListener(listener: ActionListener) {
        followButton.addActionListener(listener)
    }

    /**
     * Get follow button selected state.
     */
    fun isFollowEnabled(): Boolean = followButton.isSelected

    /**
     * Set follow button selected state.
     */
    fun setFollowEnabled(enabled: Boolean) {
        followButton.isSelected = enabled
    }
}

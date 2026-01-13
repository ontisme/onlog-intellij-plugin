package com.github.ontisme.onlog.ui

import com.github.ontisme.onlog.model.LogEntry
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * Detail panel showing log entry fields in a tree structure,
 * similar to IntelliJ's debugger Variables panel.
 */
class FieldsDetailPanel : JPanel(BorderLayout()) {

    private val tree: Tree
    private val rootNode = DefaultMutableTreeNode("欄位")
    private val treeModel = DefaultTreeModel(rootNode)
    private val headerLabel = JBLabel("欄位")

    init {
        border = JBUI.Borders.empty()

        // Header with bottom border
        val header = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(4, 8)
            )
            add(headerLabel, BorderLayout.WEST)
        }

        // Tree
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = FieldTreeCellRenderer()
        }

        add(header, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        // Initial state
        showEmpty()
    }

    /**
     * Update the panel to show fields from the selected log entry.
     */
    fun showEntry(entry: LogEntry?) {
        rootNode.removeAllChildren()

        if (entry == null || entry.fields.isEmpty()) {
            showEmpty()
            return
        }

        headerLabel.text = "欄位 (${entry.fields.size})"

        // Build tree from fields
        entry.fields.forEach { (key, value) ->
            val node = buildNode(key, value)
            rootNode.add(node)
        }

        treeModel.reload()
        expandAll()
    }

    private fun showEmpty() {
        headerLabel.text = "欄位"
        rootNode.removeAllChildren()
        rootNode.add(DefaultMutableTreeNode(FieldNode("（無欄位）", null, FieldType.EMPTY)))
        treeModel.reload()
    }

    /**
     * Build a tree node for a key-value pair, handling nested structures.
     */
    private fun buildNode(key: String, value: Any?): DefaultMutableTreeNode {
        return when (value) {
            null -> DefaultMutableTreeNode(FieldNode(key, "null", FieldType.NULL))
            is Map<*, *> -> {
                val node = DefaultMutableTreeNode(FieldNode(key, "{...}", FieldType.OBJECT))
                value.forEach { (k, v) ->
                    if (k != null) {
                        node.add(buildNode(k.toString(), v))
                    }
                }
                node
            }
            is List<*> -> {
                val node = DefaultMutableTreeNode(FieldNode(key, "[${value.size}]", FieldType.ARRAY))
                value.forEachIndexed { index, item ->
                    node.add(buildNode("[$index]", item))
                }
                node
            }
            is String -> DefaultMutableTreeNode(FieldNode(key, "\"$value\"", FieldType.STRING))
            is Number -> DefaultMutableTreeNode(FieldNode(key, value.toString(), FieldType.NUMBER))
            is Boolean -> DefaultMutableTreeNode(FieldNode(key, value.toString(), FieldType.BOOLEAN))
            else -> DefaultMutableTreeNode(FieldNode(key, value.toString(), FieldType.OTHER))
        }
    }

    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    /**
     * Data class representing a field node in the tree.
     */
    data class FieldNode(
        val key: String,
        val value: String?,
        val type: FieldType
    ) {
        override fun toString(): String = if (value != null) "$key = $value" else key
    }

    enum class FieldType {
        STRING, NUMBER, BOOLEAN, NULL, OBJECT, ARRAY, EMPTY, OTHER
    }

    /**
     * Custom cell renderer for field nodes, mimicking IntelliJ's debugger style.
     */
    private class FieldTreeCellRenderer : DefaultTreeCellRenderer() {
        private val keyColor = JBColor(Color(128, 0, 128), Color(200, 150, 255))  // Purple for keys
        private val stringColor = JBColor(Color(0, 128, 0), Color(106, 171, 115))  // Green for strings
        private val numberColor = JBColor(Color(0, 0, 255), Color(104, 151, 187))  // Blue for numbers
        private val boolColor = JBColor(Color(0, 0, 139), Color(204, 120, 50))     // Dark blue/orange for booleans
        private val nullColor = JBColor.GRAY
        private val objectColor = JBColor(Color(128, 128, 0), Color(187, 181, 41)) // Olive for objects

        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

            val node = (value as? DefaultMutableTreeNode)?.userObject as? FieldNode
            if (node != null) {
                icon = getIconForType(node.type, expanded)

                if (!sel) {
                    // Format with colored key and value
                    val keyHtml = "<font color='${colorToHex(keyColor)}'>${escapeHtml(node.key)}</font>"
                    val valueHtml = if (node.value != null) {
                        val valueColor = getColorForType(node.type)
                        " = <font color='${colorToHex(valueColor)}'>${escapeHtml(node.value)}</font>"
                    } else ""
                    text = "<html>$keyHtml$valueHtml</html>"
                }
            }

            return this
        }

        private fun getIconForType(type: FieldType, expanded: Boolean): Icon {
            return when (type) {
                FieldType.OBJECT -> if (expanded) AllIcons.Json.Object else AllIcons.Json.Object
                FieldType.ARRAY -> AllIcons.Json.Array
                FieldType.STRING -> AllIcons.Nodes.Field
                FieldType.NUMBER -> AllIcons.Nodes.Field
                FieldType.BOOLEAN -> AllIcons.Nodes.Field
                FieldType.NULL -> AllIcons.Nodes.Field
                FieldType.EMPTY -> AllIcons.General.Information
                FieldType.OTHER -> AllIcons.Nodes.Field
            }
        }

        private fun getColorForType(type: FieldType): Color {
            return when (type) {
                FieldType.STRING -> stringColor
                FieldType.NUMBER -> numberColor
                FieldType.BOOLEAN -> boolColor
                FieldType.NULL -> nullColor
                FieldType.OBJECT -> objectColor
                FieldType.ARRAY -> objectColor
                else -> foreground
            }
        }

        private fun colorToHex(color: Color): String {
            return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
        }

        private fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
    }
}

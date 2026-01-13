package com.github.ontisme.onlog.ui

import com.github.ontisme.onlog.model.SourceFilter
import com.github.ontisme.onlog.model.SourceSelection
import com.github.ontisme.onlog.service.OnLogService
import com.intellij.icons.AllIcons
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Node types for the source tree.
 */
sealed class TreeNodeData {
    data object Root : TreeNodeData()
    data class Source(val name: String) : TreeNodeData()
    data class CategoryFolder(val sourceName: String) : TreeNodeData()
    data class TagFolder(val sourceName: String) : TreeNodeData()
    data class Category(val sourceName: String, val name: String) : TreeNodeData()
    data class Tag(val sourceName: String, val name: String) : TreeNodeData()
}

/**
 * Hierarchical tree view for filtering logs by source, category, and tag.
 *
 * Structure:
 * All Sources
 * ├── source1 [checkbox]
 * │   ├── Categories
 * │   │   ├── category1 [checkbox]
 * │   │   └── category2 [checkbox]
 * │   └── Tags
 * │       ├── tag1 [checkbox]
 * │       └── tag2 [checkbox]
 * └── source2 [checkbox]
 *     └── ...
 */
class SourceTree(
    private val onSelectionChanged: (SourceSelection) -> Unit
) : CheckboxTree(SourceTreeCellRenderer(), CheckedTreeNode(TreeNodeData.Root)) {

    // Source name -> Source node
    private val sourceNodes = mutableMapOf<String, CheckedTreeNode>()

    // Source name -> Category folder node
    private val categoryFolderNodes = mutableMapOf<String, CheckedTreeNode>()

    // Source name -> Tag folder node
    private val tagFolderNodes = mutableMapOf<String, CheckedTreeNode>()

    // "source:category" -> Category node
    private val categoryNodes = mutableMapOf<String, CheckedTreeNode>()

    // "source:tag" -> Tag node
    private val tagNodes = mutableMapOf<String, CheckedTreeNode>()

    private var isUpdating = false

    init {
        // Expand root initially
        expandRow(0)
    }

    override fun onNodeStateChanged(node: CheckedTreeNode) {
        if (isUpdating) return
        super.onNodeStateChanged(node)

        // Handle parent-child checkbox propagation
        val data = node.userObject as? TreeNodeData ?: return

        when (data) {
            is TreeNodeData.Source -> {
                // Check/uncheck all children when source is toggled
                isUpdating = true
                propagateToChildren(node, node.isChecked)
                isUpdating = false
            }
            is TreeNodeData.CategoryFolder, is TreeNodeData.TagFolder -> {
                // Check/uncheck all children when folder is toggled
                isUpdating = true
                propagateToChildren(node, node.isChecked)
                isUpdating = false
            }
            is TreeNodeData.Category, is TreeNodeData.Tag -> {
                // Update parent state when child changes
                isUpdating = true
                updateParentState(node)
                isUpdating = false
            }
            else -> {}
        }

        notifySelectionChanged()
    }

    private fun propagateToChildren(node: CheckedTreeNode, checked: Boolean) {
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? CheckedTreeNode ?: continue
            child.isChecked = checked
            propagateToChildren(child, checked)
        }
    }

    private fun updateParentState(node: CheckedTreeNode) {
        val parent = node.parent as? CheckedTreeNode ?: return
        val parentData = parent.userObject as? TreeNodeData ?: return

        // Skip root node
        if (parentData is TreeNodeData.Root) return

        // Check if all children are checked
        var allChecked = true
        var anyChecked = false
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) as? CheckedTreeNode ?: continue
            if (child.isChecked) anyChecked = true else allChecked = false
        }

        parent.isChecked = allChecked || anyChecked

        // Recursively update grandparent
        updateParentState(parent)
    }

    /**
     * Update the tree with new metadata.
     */
    fun updateMetadata(sourceMetadata: Map<String, OnLogService.SourceMeta>) {
        isUpdating = true

        val root = model.root as CheckedTreeNode
        val treeModel = model as DefaultTreeModel

        // Track expanded paths
        val expandedPaths = mutableListOf<TreePath>()
        for (i in 0 until rowCount) {
            if (isExpanded(i)) {
                expandedPaths.add(getPathForRow(i))
            }
        }

        // Update sources
        sourceMetadata.forEach { (sourceName, meta) ->
            val sourceNode = sourceNodes.getOrPut(sourceName) {
                CheckedTreeNode(TreeNodeData.Source(sourceName)).apply {
                    isChecked = true
                    root.add(this)
                }
            }

            // Create/update category folder
            if (meta.categories.isNotEmpty()) {
                val categoryFolder = categoryFolderNodes.getOrPut(sourceName) {
                    CheckedTreeNode(TreeNodeData.CategoryFolder(sourceName)).apply {
                        isChecked = true
                        sourceNode.add(this)
                    }
                }

                // Add categories
                meta.categories.forEach { categoryName ->
                    val key = "$sourceName:$categoryName"
                    if (key !in categoryNodes) {
                        val categoryNode = CheckedTreeNode(TreeNodeData.Category(sourceName, categoryName)).apply {
                            isChecked = true
                        }
                        categoryNodes[key] = categoryNode
                        categoryFolder.add(categoryNode)
                    }
                }
            }

            // Create/update tag folder
            if (meta.tags.isNotEmpty()) {
                val tagFolder = tagFolderNodes.getOrPut(sourceName) {
                    CheckedTreeNode(TreeNodeData.TagFolder(sourceName)).apply {
                        isChecked = true
                        sourceNode.add(this)
                    }
                }

                // Add tags
                meta.tags.forEach { tagName ->
                    val key = "$sourceName:$tagName"
                    if (key !in tagNodes) {
                        val tagNode = CheckedTreeNode(TreeNodeData.Tag(sourceName, tagName)).apply {
                            isChecked = true
                        }
                        tagNodes[key] = tagNode
                        tagFolder.add(tagNode)
                    }
                }
            }
        }

        // Refresh tree
        treeModel.reload()

        // Restore expanded state
        expandRow(0) // Always expand root
        expandedPaths.forEach { path ->
            expandPath(path)
        }

        // Expand all source nodes by default
        sourceNodes.values.forEach { node ->
            val path = TreePath(treeModel.getPathToRoot(node))
            expandPath(path)
        }

        isUpdating = false
    }

    /**
     * Clear all nodes from the tree.
     */
    fun clearTree() {
        isUpdating = true

        val root = model.root as CheckedTreeNode
        val treeModel = model as DefaultTreeModel

        // Clear all caches
        sourceNodes.clear()
        categoryFolderNodes.clear()
        tagFolderNodes.clear()
        categoryNodes.clear()
        tagNodes.clear()

        // Remove all children from root
        root.removeAllChildren()

        // Refresh tree
        treeModel.reload()
        expandRow(0)

        isUpdating = false

        // Notify with empty selection
        onSelectionChanged(SourceSelection(emptySet(), emptyMap()))
    }

    /**
     * Select all sources.
     */
    fun selectAll() {
        isUpdating = true

        sourceNodes.values.forEach { node ->
            node.isChecked = true
            propagateToChildren(node, true)
        }

        (model as DefaultTreeModel).reload()
        expandRow(0)

        isUpdating = false
        notifySelectionChanged()
    }

    /**
     * Deselect all sources.
     */
    fun deselectAll() {
        isUpdating = true

        sourceNodes.values.forEach { node ->
            node.isChecked = false
            propagateToChildren(node, false)
        }

        (model as DefaultTreeModel).reload()
        expandRow(0)

        isUpdating = false
        notifySelectionChanged()
    }

    private fun notifySelectionChanged() {
        val selectedSources = mutableSetOf<String>()
        val sourceFilters = mutableMapOf<String, SourceFilter>()

        sourceNodes.forEach { (sourceName, node) ->
            if (node.isChecked) {
                selectedSources.add(sourceName)

                // Collect selected categories for this source
                val selectedCategories = mutableSetOf<String>()
                val allCategories = mutableSetOf<String>()

                categoryNodes.forEach { (key, catNode) ->
                    if (key.startsWith("$sourceName:")) {
                        val categoryName = key.substringAfter(":")
                        allCategories.add(categoryName)
                        if (catNode.isChecked) {
                            selectedCategories.add(categoryName)
                        }
                    }
                }

                // Collect selected tags for this source
                val selectedTags = mutableSetOf<String>()
                val allTags = mutableSetOf<String>()

                tagNodes.forEach { (key, tagNode) ->
                    if (key.startsWith("$sourceName:")) {
                        val tagName = key.substringAfter(":")
                        allTags.add(tagName)
                        if (tagNode.isChecked) {
                            selectedTags.add(tagName)
                        }
                    }
                }

                // Only include in filter if not all are selected
                val categories = if (selectedCategories.size == allCategories.size) emptySet() else selectedCategories
                val tags = if (selectedTags.size == allTags.size) emptySet() else selectedTags

                if (categories.isNotEmpty() || tags.isNotEmpty()) {
                    sourceFilters[sourceName] = SourceFilter(categories, tags)
                }
            }
        }

        // Empty sources means "all sources"
        val sources = if (selectedSources.size == sourceNodes.size) emptySet() else selectedSources

        onSelectionChanged(SourceSelection(sources, sourceFilters))
    }

    class SourceTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? CheckedTreeNode ?: return
            val data = node.userObject as? TreeNodeData ?: return

            when (data) {
                is TreeNodeData.Root -> {
                    textRenderer.append("All Sources", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    textRenderer.icon = AllIcons.Nodes.Folder
                }
                is TreeNodeData.Source -> {
                    textRenderer.append(data.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    textRenderer.icon = AllIcons.Nodes.Module
                }
                is TreeNodeData.CategoryFolder -> {
                    textRenderer.append("Categories", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    textRenderer.icon = AllIcons.Nodes.Tag
                }
                is TreeNodeData.TagFolder -> {
                    textRenderer.append("Tags", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    textRenderer.icon = AllIcons.Nodes.Aspect
                }
                is TreeNodeData.Category -> {
                    textRenderer.append(data.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    textRenderer.icon = AllIcons.Nodes.Property
                }
                is TreeNodeData.Tag -> {
                    textRenderer.append(data.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    textRenderer.icon = AllIcons.Nodes.Annotationtype
                }
            }
        }
    }
}

package com.github.ontisme.onlog.ui.actions

import com.github.ontisme.onlog.service.OnLogService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to clear log entries.
 */
class ClearAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = OnLogService.getInstance(project)
        service.clearLogs()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

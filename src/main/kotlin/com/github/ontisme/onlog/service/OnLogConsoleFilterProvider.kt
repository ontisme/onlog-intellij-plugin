package com.github.ontisme.onlog.service

import com.github.ontisme.onlog.model.LogEntry
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Console filter provider that detects onlog log lines from stdout.
 * Parses both pretty format and JSON format.
 */
class OnLogConsoleFilterProvider : ConsoleFilterProvider {

    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(OnLogFilter(project))
    }
}

/**
 * Filter that detects onlog log lines from stdout.
 * Supports:
 * - Pretty format: "20:46:56.183 DBG app main.go:139 > message key=value"
 * - JSON format: {"level":"info","src":"app","message":"..."}
 */
class OnLogFilter(private val project: Project) : Filter {

    // Strip ANSI codes pattern
    private val ansiPattern = Regex("""\u001b\[[0-9;]*m""")

    // Pretty format pattern: TIME LEVEL SOURCE CALLER > MESSAGE FIELDS...
    private val simplePrettyPattern = Regex(
        """^(\d{2}:\d{2}:\d{2}\.\d{3})\s+(DBG|INF|WRN|ERR)\s+(\S+)\s+(\S+:\d+)\s*>\s*(.+)$"""
    )

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val entry = parseLine(line.trim())
        if (entry != null) {
            try {
                val service = OnLogService.getInstance(project)
                service.addStdoutLogs(listOf(entry))
            } catch (e: Exception) {
                LOG.debug("Failed to send log to OnLogService", e)
            }
        }

        return null
    }

    private fun parseLine(line: String): LogEntry? {
        // Try JSON format first
        if (line.startsWith("{") && (line.contains("\"src\"") || line.contains("\"__onlog__\""))) {
            return LogEntry.fromStdoutJson(line)
        }

        // Try pretty format (strip ANSI codes first)
        val cleanLine = ansiPattern.replace(line, "")
        val match = simplePrettyPattern.find(cleanLine) ?: return null

        val (time, level, src, caller, rest) = match.destructured

        // Parse message and fields from rest
        val (msg, fields) = parseMessageAndFields(rest)

        return LogEntry(
            ts = parseTimeToMillis(time),
            lvl = level,
            src = src,
            cat = null,
            msg = msg,
            caller = caller,
            tags = emptyList(),
            fields = fields
        )
    }

    private fun parseTimeToMillis(time: String): Long {
        // Parse HH:mm:ss.SSS to today's timestamp
        return try {
            val parts = time.split(":", ".")
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            val seconds = parts[2].toInt()
            val millis = parts[3].toInt()

            val now = java.time.LocalDate.now()
            val localTime = java.time.LocalTime.of(hours, minutes, seconds, millis * 1_000_000)
            val dateTime = java.time.LocalDateTime.of(now, localTime)
            dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseMessageAndFields(rest: String): Pair<String, Map<String, Any>> {
        // Find where fields start (key=value pattern)
        val fieldPattern = Regex("""\s+(\w+)=""")
        val firstFieldMatch = fieldPattern.find(rest)

        val msg: String
        val fieldsStr: String

        if (firstFieldMatch != null) {
            msg = rest.substring(0, firstFieldMatch.range.first).trim()
            fieldsStr = rest.substring(firstFieldMatch.range.first).trim()
        } else {
            msg = rest.trim()
            fieldsStr = ""
        }

        // Parse fields
        val fields = mutableMapOf<String, Any>()
        if (fieldsStr.isNotEmpty()) {
            val kvPattern = Regex("""(\w+)=(\S+)""")
            kvPattern.findAll(fieldsStr).forEach { match ->
                val (key, value) = match.destructured
                fields[key] = parseFieldValue(value)
            }
        }

        return Pair(msg, fields)
    }

    private fun parseFieldValue(value: String): Any {
        // Try to parse as number
        value.toIntOrNull()?.let { return it }
        value.toDoubleOrNull()?.let { return it }
        // Try boolean
        if (value == "true") return true
        if (value == "false") return false
        // Return as string (remove surrounding brackets if array-like)
        return value.removeSurrounding("[", "]")
    }

    companion object {
        private val LOG = Logger.getInstance(OnLogFilter::class.java)
    }
}

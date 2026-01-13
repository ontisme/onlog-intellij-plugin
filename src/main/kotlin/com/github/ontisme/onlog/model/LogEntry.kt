package com.github.ontisme.onlog.model

import com.google.gson.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Represents a single log entry from onlog.
 */
data class LogEntry(
    val ts: Long,           // Unix milliseconds
    val lvl: String,        // DBG, INF, WRN, ERR
    val src: String,        // Source name
    val cat: String? = null, // Category (optional)
    val msg: String,        // Message
    val caller: String? = null, // Caller location (file:line)
    val tags: List<String> = emptyList(),
    val fields: Map<String, Any> = emptyMap()
) {
    val level: LogLevel
        get() = LogLevel.fromString(lvl)

    val timestamp: Instant
        get() = Instant.ofEpochMilli(ts)

    val formattedTime: String
        get() = FORMATTER.format(timestamp)

    companion object {
        private val FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())

        /**
         * Parse RFC3339 timestamp to unix milliseconds.
         */
        private fun parseRFC3339(timeStr: String): Long {
            return try {
                Instant.parse(timeStr).toEpochMilli()
            } catch (e: Exception) {
                try {
                    // Try with DateTimeFormatter for more flexible parsing
                    val parsed = java.time.OffsetDateTime.parse(timeStr)
                    parsed.toInstant().toEpochMilli()
                } catch (e2: Exception) {
                    System.currentTimeMillis()
                }
            }
        }

        /**
         * Parse a JSON element to a native value.
         */
        private fun parseFieldValue(value: com.google.gson.JsonElement): Any {
            return when {
                value.isJsonPrimitive -> {
                    val prim = value.asJsonPrimitive
                    when {
                        prim.isBoolean -> prim.asBoolean
                        prim.isNumber -> prim.asNumber
                        else -> prim.asString
                    }
                }
                value.isJsonArray -> value.asJsonArray.map { parseFieldValue(it) }
                else -> value.toString()
            }
        }

        /**
         * Parse a LogEntry from JSON object.
         * Supports both WS message format and stdout JSON format (zerolog).
         */
        fun fromJson(json: JsonObject): LogEntry {
            // Parse timestamp - support both unix millis (ts) and RFC3339 string (time)
            val ts = when {
                json.has("ts") -> json.get("ts").asLong
                json.has("time") -> parseRFC3339(json.get("time").asString)
                else -> System.currentTimeMillis()
            }
            val lvl = json.get("lvl")?.asString ?: json.get("level")?.asString ?: "INF"
            val src = json.get("src")?.asString ?: "unknown"
            val cat = json.get("cat")?.asString
            val msg = json.get("msg")?.asString ?: json.get("message")?.asString ?: ""
            val caller = json.get("caller")?.asString

            val tags = mutableListOf<String>()
            json.getAsJsonArray("tags")?.forEach { element ->
                tags.add(element.asString)
            }

            // Standard fields to exclude from custom fields
            val standardFields = setOf(
                "ts", "time", "level", "lvl", "src", "cat", "msg", "message",
                "caller", "tags", "fields", "__onlog__"
            )

            val fields = mutableMapOf<String, Any>()

            // First, check for nested "fields" object (WS format)
            json.getAsJsonObject("fields")?.entrySet()?.forEach { (key, value) ->
                fields[key] = parseFieldValue(value)
            }

            // Then, collect top-level fields (zerolog format) - excluding standard fields
            json.entrySet().forEach { (key, value) ->
                if (key !in standardFields && !value.isJsonNull) {
                    fields[key] = parseFieldValue(value)
                }
            }

            return LogEntry(
                ts = ts,
                lvl = lvl,
                src = src,
                cat = cat,
                msg = msg,
                caller = caller,
                tags = tags,
                fields = fields
            )
        }

        /**
         * Parse a LogEntry from stdout JSON line.
         * Format: {"__onlog__":"log","ts":...,"lvl":"INF",...}
         * Also supports zerolog format with "level" and "message" fields.
         */
        fun fromStdoutJson(line: String): LogEntry? {
            return try {
                val json = com.google.gson.Gson().fromJson(line, JsonObject::class.java)
                // Accept both __onlog__ marker and raw zerolog JSON with src field
                if (json.get("__onlog__")?.asString == "log" || json.has("src")) {
                    fromJson(json)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Log severity levels.
 */
enum class LogLevel(val label: String, val priority: Int) {
    DEBUG("DBG", 0),
    INFO("INF", 1),
    WARN("WRN", 2),
    ERROR("ERR", 3);

    companion object {
        fun fromString(s: String): LogLevel = when (s.uppercase()) {
            "DBG", "DEBUG" -> DEBUG
            "INF", "INFO" -> INFO
            "WRN", "WARN", "WARNING" -> WARN
            "ERR", "ERROR" -> ERROR
            else -> DEBUG
        }
    }
}

/**
 * Per-source filter for categories and tags.
 */
data class SourceFilter(
    val categories: Set<String> = emptySet(),  // empty = all categories
    val tags: Set<String> = emptySet()         // empty = all tags
)

/**
 * Hierarchical source selection with per-source filters.
 */
data class SourceSelection(
    val sources: Set<String> = emptySet(),           // Selected sources (empty = all)
    val sourceFilters: Map<String, SourceFilter> = emptyMap()  // Per-source category/tag filters
)

/**
 * Filter criteria for log entries.
 * Supports multi-select levels and hierarchical source filtering.
 */
data class LogFilter(
    val sources: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val levels: Set<LogLevel> = LogLevel.entries.toSet(),  // Multi-select levels (default: all)
    val tags: Set<String> = emptySet(),
    val search: String = "",
    val sourceSelection: SourceSelection? = null  // Hierarchical source selection (optional)
) {
    // Backward compatibility - deprecated minLevel
    @Deprecated("Use levels instead", ReplaceWith("levels.minByOrNull { it.priority } ?: LogLevel.DEBUG"))
    val minLevel: LogLevel
        get() = levels.minByOrNull { it.priority } ?: LogLevel.DEBUG

    fun matches(entry: LogEntry): Boolean {
        // Check hierarchical source selection if present
        if (sourceSelection != null) {
            if (sourceSelection.sources.isNotEmpty() && entry.src !in sourceSelection.sources) {
                return false
            }
            // Check per-source category/tag filters
            val sourceFilter = sourceSelection.sourceFilters[entry.src]
            if (sourceFilter != null) {
                if (sourceFilter.categories.isNotEmpty() && entry.cat !in sourceFilter.categories) {
                    return false
                }
                if (sourceFilter.tags.isNotEmpty() && !entry.tags.any { it in sourceFilter.tags }) {
                    return false
                }
            }
        } else {
            // Fallback to simple source/category filtering
            if (sources.isNotEmpty() && entry.src !in sources) return false
            if (categories.isNotEmpty() && entry.cat !in categories) return false
        }

        // Check level (multi-select)
        if (entry.level !in levels) return false

        // Check tags (must contain ANY of the specified tags, or all if empty)
        if (tags.isNotEmpty() && !entry.tags.any { it in tags }) return false

        // Check search
        if (search.isNotBlank()) {
            val searchLower = search.lowercase()
            if (!entry.msg.lowercase().contains(searchLower) &&
                !entry.src.lowercase().contains(searchLower) &&
                entry.cat?.lowercase()?.contains(searchLower) != true
            ) return false
        }

        return true
    }

    val isEmpty: Boolean
        get() = sources.isEmpty() &&
                categories.isEmpty() &&
                levels.size == LogLevel.entries.size &&
                tags.isEmpty() &&
                search.isBlank() &&
                sourceSelection == null
}

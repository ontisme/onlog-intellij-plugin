# OnLog IntelliJ Plugin

> AI-friendly guide for developing and maintaining this JetBrains IDE plugin.

## Related Repository

| Repository | Description |
|------------|-------------|
| [onlog](https://github.com/ontisme/onlog) | Go structured logging library |
| **This repo** | JetBrains IDE plugin for real-time log viewing |

This plugin connects to the Go onlog library via WebSocket to display logs with filtering and search capabilities.

## What is This?

A JetBrains IDE plugin that provides real-time structured log viewing with:
- WebSocket connection to Go onlog library
- Auto-detection from Run console output
- Source/Category/Level/Tag filtering
- Searchable log history

---

## Project Structure

```
intellij-plugin/
├── build.gradle.kts              # Build configuration
├── src/main/kotlin/.../onlog/
│   ├── model/
│   │   ├── LogEntry.kt           # Log entry data class
│   │   └── Messages.kt           # WebSocket message types
│   ├── service/
│   │   ├── OnLogService.kt       # Core service (singleton)
│   │   ├── OnLogWebSocketServer.kt
│   │   └── OnLogConsoleFilterProvider.kt
│   └── ui/
│       ├── OnLogToolWindowFactory.kt
│       ├── OnLogToolWindowPanel.kt
│       ├── FilterPanel.kt
│       ├── LogConsole.kt
│       ├── SourceTree.kt
│       └── actions/OnLogActions.kt
└── src/main/resources/
    └── META-INF/plugin.xml       # Plugin descriptor
```

---

## File Responsibilities

| File | Purpose | Key Classes |
|------|---------|-------------|
| `LogEntry.kt` | Log data model | `LogEntry`, `LogLevel`, `LogFilter` |
| `Messages.kt` | WebSocket protocol | `WsMessage`, `InitMessage`, `LogsMessage` |
| `OnLogService.kt` | State management, filtering | `OnLogService` (application service) |
| `OnLogWebSocketServer.kt` | WebSocket server | Handles app connections |
| `OnLogConsoleFilterProvider.kt` | Console auto-detect | Detects `{"__onlog__":"init"}` |
| `OnLogToolWindowPanel.kt` | Main UI container | Toolbar + FilterPanel + LogConsole |
| `FilterPanel.kt` | Filter controls | Level dropdown, tag input, search |
| `LogConsole.kt` | Log display | Scrollable colored log list |
| `SourceTree.kt` | Source/category tree | Checkbox tree for filtering |
| `OnLogActions.kt` | Toolbar actions | Connect, Disconnect, Clear |

---

## Key Data Classes

### LogEntry

```kotlin
data class LogEntry(
    val timestamp: Long,      // Unix ms
    val level: LogLevel,      // DEBUG, INFO, WARN, ERROR
    val source: String,       // Logger name
    val category: String?,    // Optional category
    val message: String,
    val tags: List<String>,
    val fields: Map<String, Any>,
    val caller: String?
)
```

### LogLevel

```kotlin
enum class LogLevel(val priority: Int, val display: String) {
    DEBUG(0, "DBG"),
    INFO(1, "INF"),
    WARN(2, "WRN"),
    ERROR(3, "ERR")
}
```

### LogFilter

```kotlin
data class LogFilter(
    val sources: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val minLevel: LogLevel = LogLevel.DEBUG,
    val tags: Set<String> = emptySet(),
    val searchText: String = ""
)
```

---

## WebSocket Protocol

### Message Format

```kotlin
data class WsMessage(
    val type: String,      // "init", "logs", "subscribe", "history"
    val data: JsonElement
)
```

### Server → Client

```jsonc
// On connect
{"type": "init", "data": {"port": 19999, "sources": ["api"], "categories": ["request"]}}

// Log stream
{"type": "logs", "data": {"entries": [...]}}
```

### Client → Server

```jsonc
// Subscribe with filter
{"type": "subscribe", "data": {"filter": {"sources": ["api"], "minLevel": "INF"}}}

// Request history
{"type": "history", "data": {"limit": 1000}}
```

---

## Service Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    OnLogService (Singleton)                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ entries     │  │ filter      │  │ listeners           │  │
│  │ (buffer)    │  │ (current)   │  │ (UI callbacks)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
   │ WebSocket   │ │ Console     │ │ ToolWindow  │
   │ Server      │ │ Filter      │ │ Panel       │
   └─────────────┘ └─────────────┘ └─────────────┘
```

---

## Common Development Tasks

### Add New Filter Criteria

1. **Update LogFilter** (`model/LogEntry.kt`):
```kotlin
data class LogFilter(
    // ... existing fields
    val newCriteria: String = ""
)
```

2. **Update match logic** (`model/LogEntry.kt`):
```kotlin
fun matches(entry: LogEntry): Boolean {
    // ... existing checks
    if (newCriteria.isNotEmpty() && !entry.matchesNew(newCriteria)) return false
    return true
}
```

3. **Add UI control** (`ui/FilterPanel.kt`):
```kotlin
private val newCriteriaField = JTextField()
// Add to panel layout
// Add change listener to update filter
```

### Add New WebSocket Message Type

1. **Define message class** (`model/Messages.kt`):
```kotlin
data class NewMessage(
    val field: String
)
```

2. **Handle in service** (`service/OnLogService.kt`):
```kotlin
when (message.type) {
    "new_type" -> {
        val data = gson.fromJson(message.data, NewMessage::class.java)
        // Handle message
    }
}
```

### Add Toolbar Action

1. **Create action class** (`ui/actions/OnLogActions.kt`):
```kotlin
class NewAction : AnAction("New Action", "Description", IconLoader.getIcon("/icons/new.svg")) {
    override fun actionPerformed(e: AnActionEvent) {
        // Action logic
    }
}
```

2. **Register in plugin.xml**:
```xml
<action id="OnLog.NewAction" class="...NewAction">
    <add-to-group group-id="OnLog.Toolbar"/>
</action>
```

3. **Add to toolbar** (`ui/OnLogToolWindowPanel.kt`):
```kotlin
val toolbar = ActionManager.getInstance()
    .createActionToolbar("OnLog", actionGroup, true)
```

### Add New Log Entry Field

1. **Update LogEntry** (`model/LogEntry.kt`):
```kotlin
data class LogEntry(
    // ... existing
    val newField: String?
)
```

2. **Update parsing** (`service/OnLogService.kt`):
```kotlin
LogEntry(
    // ... existing
    newField = json["newField"]?.asString
)
```

3. **Update display** (`ui/LogConsole.kt`):
```kotlin
// Add to log line formatting
if (entry.newField != null) {
    append(" [${entry.newField}]")
}
```

---

## IntelliJ Platform Patterns

### Application Service (Singleton)

```kotlin
@Service
class OnLogService {
    companion object {
        fun getInstance(): OnLogService =
            ApplicationManager.getApplication().getService(OnLogService::class.java)
    }
}
```

### Tool Window Factory

```kotlin
class OnLogToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = OnLogToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

### Console Filter Provider

```kotlin
class OnLogConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(OnLogInitFilter(project))
    }
}
```

### Listener Pattern

```kotlin
interface OnLogListener {
    fun onLogReceived(entries: List<LogEntry>)
    fun onFilterChanged(filter: LogFilter)
    fun onConnectionChanged(connected: Boolean)
}
```

---

## Build Commands

```bash
# Build
./gradlew build

# Run in sandbox IDE
./gradlew runIde

# Package plugin (.zip)
./gradlew buildPlugin

# Run tests
./gradlew test

# Verify plugin compatibility
./gradlew verifyPlugin

# Clean
./gradlew clean
```

---

## Release Process

### Automatic Release (GitHub Actions)

Push a version tag to trigger automatic build and release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

This will:
1. Build the plugin
2. Create a GitHub Release
3. Upload the `.zip` file to Release assets

### Manual Release

1. Go to Actions → "Build & Release"
2. Click "Run workflow"
3. Enter version number (e.g., `1.0.0`)
4. Click "Run workflow"

### GitHub Actions Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | Push/PR to main | Build & verify plugin |
| `release.yml` | Tag `v*` or manual | Build & create release |

---

## Plugin Configuration

### plugin.xml

```xml
<idea-plugin>
    <id>com.github.ontisme.onlog</id>
    <name>OnLog</name>

    <!-- Tool Window -->
    <extensions defaultExtensionNs="com.intellij">
        <toolWindow id="OnLog"
                    factoryClass="...OnLogToolWindowFactory"
                    anchor="bottom"/>
    </extensions>

    <!-- Application Service -->
    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceImplementation="...OnLogService"/>
    </extensions>

    <!-- Console Filter -->
    <extensions defaultExtensionNs="com.intellij">
        <consoleFilterProvider implementation="...OnLogConsoleFilterProvider"/>
    </extensions>
</idea-plugin>
```

### build.gradle.kts

```kotlin
plugins {
    id("org.jetbrains.intellij") version "1.x"
    id("org.jetbrains.kotlin.jvm") version "1.9.x"
}

intellij {
    version.set("2024.1")
    type.set("IC")  // IntelliJ Community
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("243.*")
    }
}
```

---

## Troubleshooting

### "Plugin not loading"
1. Check `plugin.xml` syntax
2. Verify service class paths
3. Check IDE version compatibility in `build.gradle.kts`

### "WebSocket not connecting"
1. Verify port is available
2. Check firewall settings
3. Ensure app is sending correct init message

### "Logs not appearing"
1. Check filter settings (level, sources)
2. Verify WebSocket connection status
3. Check `OnLogService.addLog()` is being called

### "UI not updating"
1. Ensure updates on EDT: `ApplicationManager.getApplication().invokeLater { ... }`
2. Check listener registration
3. Verify `fireXxx()` methods are called

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `org.jetbrains.intellij` | IntelliJ Platform Plugin |
| `org.jetbrains.kotlin.jvm` | Kotlin JVM |
| `com.google.code.gson` | JSON parsing |

---

## Quick Reference

```kotlin
// Get service instance
val service = OnLogService.getInstance()

// Add log entry
service.addLog(entry)

// Update filter
service.setFilter(filter)

// Get filtered logs
val logs = service.getFilteredLogs()

// Add listener
service.addListener(object : OnLogListener {
    override fun onLogReceived(entries: List<LogEntry>) { ... }
    override fun onFilterChanged(filter: LogFilter) { ... }
    override fun onConnectionChanged(connected: Boolean) { ... }
})

// Connect/disconnect
service.connect(port)
service.disconnect()
```

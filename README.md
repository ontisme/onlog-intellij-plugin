# OLog IntelliJ Plugin

Multi-channel structured log viewer for JetBrains IDEs with real-time WebSocket streaming.

## Features

- **Real-time Streaming**: Receive logs via WebSocket as they happen
- **Auto-detection**: Automatically connects when detecting `{"__olog__":"init"}` in Run console
- **Source Filtering**: Filter logs by source (component, service, etc.)
- **Category Filtering**: Filter by log category
- **Level Filtering**: Show only logs above a certain severity
- **Tag Filtering**: Filter by custom tags
- **Search**: Full-text search across all logs
- **Color-coded Output**: Easy visual distinction between log levels

## Installation

### From Marketplace (Coming Soon)

1. Open Settings → Plugins → Marketplace
2. Search for "OLog"
3. Click Install

### From Source

1. Clone this repository
2. Run `./gradlew buildPlugin`
3. Install from Settings → Plugins → ⚙️ → Install Plugin from Disk
4. Select the generated `.zip` file in `build/distributions/`

## Usage

### With Go olog library

```go
import "github.com/lyfx/olog"

func main() {
    olog.Init(olog.WithPort(19999))
    defer olog.Close()

    api := olog.L("api")
    api.Cat("request").Str("method", "GET").Info("handled")
}
```

### Auto-connection

When you run your application from the IDE, the plugin automatically detects the init message and connects.

### Manual Connection

1. Open the OLog tool window (View → Tool Windows → OLog)
2. Click the Connect button in the toolbar
3. Enter the server port

## UI Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  [Connect] [Disconnect] [Clear]                                      │
├─────────────────────────────────────────────────────────────────────┤
│  Level: [DBG ▼]  Tags: [________]  🔍 [Search logs...]             │
├───────────────────┬─────────────────────────────────────────────────┤
│  Sources          │  Time      Lvl  Source    Cat     Message       │
│  ☑ api           │  10:30:01  INF  api       request handled       │
│  ☑ database      │  10:30:02  DBG  database  query   executed      │
│  Categories       │  10:30:03  WRN  api       request rejected      │
│  ☑ request       │  10:30:04  ERR  api       request failed        │
│  ☑ query         │                                                  │
└───────────────────┴─────────────────────────────────────────────────┘
│  Connected to port 19999 | 1,234 logs                               │
└─────────────────────────────────────────────────────────────────────┘
```

## Development

### Prerequisites

- JDK 17+
- IntelliJ IDEA (for running/debugging)

### Build

```bash
./gradlew build
```

### Run in sandbox

```bash
./gradlew runIde
```

### Package

```bash
./gradlew buildPlugin
```

## License

MIT

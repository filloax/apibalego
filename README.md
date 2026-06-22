# Apibalego

> **WIP.** NeoForge support is currently untested. WebSocket live updates are not yet compatible with NeoForge and will fall back gracefully with a warning.

Minecraft mod library providing a generic gamemaster/website connection layer. Extracted from [Ruins of Growsseth](https://modrinth.com/mod/ruins-of-growsseth).

Supports two update modes:
- **HTTP polling** — periodic GET requests to a gamemaster server
- **WebSocket live updates** — Socket.IO 2.x connection for real-time events

For both Fabric and Neoforge (not really currently, see above).

## Requirements

- [FilloaxLib](https://modrinth.com/mod/filloaxlib)
- [ResourcefulConfig](https://modrinth.com/mod/resourceful-config) 

## Usage

### HTTP Polling

Register a handler for a polled endpoint:

```kotlin
DataRemoteSync.subscribe("my-endpoint", MyType.serializer()) { data ->
    // called periodically with fresh data from the gamemaster server
}

ApiEventRegistry.registerHandler("myprefix/") { event, data ->
    // called for any event whose name starts with "myprefix/"
}
```

### WebSocket Live Updates

Register a handler for a specific Socket.IO event:

```kotlin
LiveUpdatesEventRegistry.register("my-event") { data ->
    // called when "my-event" fires over the websocket
}
```

### Command Hooks

```kotlin
GamemasterCommand.onReloadHooks.add { server ->
    // called when the /gmaster reload command runs
}
```

## Configuration

`ApiBalegoConfig` (via ResourcefulConfig UI or config file) exposes:

- Gamemaster server URL and port
- Sync interval for HTTP polling
- API key
- Enable/disable HTTP polling (`webDataSync`) or WebSocket (`liveUpdateService`)
- Remote command execution toggle

Configuration changes require a world reload to take effect.

## Module Structure

| Module | Contents |
|--------|----------|
| `base/` | All platform-agnostic logic (HTTP, WebSocket, commands, config, data, UI) |
| `fabric/` | Fabric entry point + unit tests |
| `neoforge/` | NeoForge entry point |
| `buildSrc/` | Shared Gradle plugins and dependency lists |
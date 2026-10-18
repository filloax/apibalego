# Apibalego

Minecraft mod library providing a gamemaster API to remotely interact with a singleplayer / multiplayer world. Mainly
intended for streamers or challenge runners with a particular goal that requires remote activation / management, or for
roleplay purposes (with a Gamemaster placing specific structures, sending notifications, etc.)

> [!WARNING]
> This mod **requires** connecting to a custom server (NOT a Minecraft server) running the Gamemaster API to work.
> **Only connect to trusted servers!** Normally, this would be someone you know or that works with you, that
> will be the "Game Master".
> 
> An example server you can download is [Egobalego At Home](https://github.com/costentz/egobalego-at-home). 
> For more information, see the [wiki](https://github.com/filloax/apibalego/wiki).

Other than simply allowing remote commands, includes other features such as
- Datapack/resourcepack download (opt-in clientside)
- Structure placement (by exact block rather than per-chunk as vanilla does)
- Toast notification sending

The big one mainly being datapack/resourcepacks, which allow you to remotely modify game behavior.

For both Fabric and Neoforge.

Extracted from [Ruins of Growsseth](https://modrinth.com/mod/ruins-of-growsseth), originally made for [Cydonia's run](https://www.youtube.com/playlist?list=PLL6o5grLhqytZTDOc9kwZb8mVftuRw0lp).

## Requirements

- [FilloaxLib](https://modrinth.com/mod/filloaxlib) (Bundled, not needed to download)
- [ResourcefulConfig](https://modrinth.com/mod/resourceful-config) 

## Configuration

`ApiBalegoConfig` (via ingame config or jsonc file) has:

- Gamemaster server URL and port
- Sync interval for HTTP polling
- API key (optional)
- Enable/disable HTTP polling (`webDataSync`) or WebSocket (`liveUpdateService`)
- Enable/disable remote command execution
- Enable/disable datapack/resourcepack download

See [wiki page](https://github.com/filloax/apibalego/wiki/Config) for more information.

## Mod developers

See the [wiki](https://github.com/filloax/apibalego/wiki/For%E2%80%90Mod%E2%80%90Developers) for more information, including
Java examples.

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

---

> #### AI usage disclaimer
>
> AI was not used for the actual API development in Ruins of Growsseth. It was used here to
> - assist in porting the mod to newer versions
> - creating unit tests and gametests to be extra sure (the mod was also normally playtested as usual, 
>   testing was not left solely to AI-ran tests, dw)
> - initial setup of readme (you may notice em dashes in previous commits of the readme lol)
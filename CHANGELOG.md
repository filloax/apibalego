# Changelog

## 1.1.0

- Add Trigger system
  - Api entries that can expand into more api entries if conditions are met
  - Builtin trigger types are on advancement (once per advancement, customize which),
    and on respawn (repetable)
  - Triggers are per-player as they depend on player stuff, so things like commands will run
    for the specific player, etc.
  - Triggers can contain any api entry, global ones such as structures won't be repeated for multiple
    players. Only exception is other triggers, cannot nest those
- Fix resource packs downloaded from API not being activated
- Mute command output in chat (still logs to console)

## 1.0.0

- Fix neoforge, prepare for first release

## 0.4.1

- Fix Resource pack download

## 0.4.0

- Update to 26.3

## 0.3.0

- Add Java-facing APIs to avoid Kotlin serialization

## 0.2.1

- Simple API docs autogeneration from registered entries

## 0.2.0

- Datapacks / resourcepacks are preloaded before first vanilla pack load
- Expose proper public API

## 0.1.0

- Initial release.

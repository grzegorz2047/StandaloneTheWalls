# Standalone The Walls

A standalone multiplayer 3D game inspired by the rules of the original
`grzegorz2047/TheWalls` Minecraft plugin. The new project does not copy Bukkit,
Minecraft code, assets, UI, or pay-to-win systems.

## Status

Foundation work is in progress. The repository does **not** yet contain a
playable game, production network stack, or finished map.

## Technology baseline

- Java 21 LTS
- Gradle 9.6.1 Wrapper with a pinned distribution checksum
- Kotlin DSL
- jMonkeyEngine 3.9.0-stable in presentation modules only
- JUnit 5 and AssertJ

## Modules

- `shared` - small renderer-independent shared values
- `game-domain` - deterministic game rules
- `protocol` - versioned messages and transport boundaries
- `map-format` - safe `.twmap` contracts and validation
- `client` - jMonkeyEngine game client
- `server` - headless authoritative dedicated server
- `map-studio` - map authoring application
- `bot-client` - headless integration and load-test client

Read [ARCHITECTURE.md](ARCHITECTURE.md) before changing module boundaries.

## Build

```bash
./gradlew check
```

On Windows:

```powershell
.\gradlew.bat check
```

The first run downloads the pinned Gradle distribution and project dependencies.

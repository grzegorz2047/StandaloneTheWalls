# Sunderfront

**Sunderfront** is the working product name for a standalone multiplayer 3D game
inspired by the rules of the original `grzegorz2047/TheWalls` Minecraft plugin.
The repository keeps its historical `StandaloneTheWalls` name for now, but the
product does not copy Bukkit, Minecraft code, assets, UI, branding, or
pay-to-win systems.

The name is provisional until a formal trademark and release-name review is
completed.

## Status

Foundation work is in progress. The repository does **not** yet contain a
playable game, production network stack, finished map, or proven 40-player
capacity.

## Product principles

- authoritative dedicated servers and deterministic renderer-independent rules;
- LAN play without Internet access and direct Internet connections;
- user-created, data-only maps that cannot execute code;
- local-first player identity with optional globally reserved handles;
- no mandatory central account, paid backend, subscription, or pay-to-win system;
- measurable support for integrated graphics instead of vague hardware claims;
- large asset packs kept outside normal Git history and pinned by cryptographic
  hashes.

Read [IDENTITY.md](IDENTITY.md), [PERFORMANCE.md](PERFORMANCE.md), and
[ASSET_PIPELINE.md](ASSET_PIPELINE.md) for the accepted product decisions.

## Technology baseline

- Java 21 LTS
- Gradle 9.6.1 with Kotlin DSL
- jMonkeyEngine 3.9.0-stable in presentation modules only
- JUnit 5 and AssertJ

The official Gradle Wrapper with a pinned distribution checksum remains part of
issue #20 and must be present before the foundation PR leaves draft status.

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

## Build during the foundation draft

Install Gradle 9.6.1 and run:

```bash
gradle check
```

Once issue #20 is complete, the documented command will be `./gradlew check`
(or `.\gradlew.bat check` on Windows) using the repository-owned wrapper.

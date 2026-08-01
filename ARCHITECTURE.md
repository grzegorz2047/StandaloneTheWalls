# Architecture

## Architectural goals

Standalone The Walls is built around a renderer-independent, deterministic game
core and an authoritative dedicated server. Client rendering, network transport,
map files, and administration are adapters around explicit contracts.

The architecture must support Windows and Linux clients, a Linux/Windows headless
server, LAN play without Internet access, direct Internet connections, user maps,
and repeatable tests without opening a graphics device.

## Modules and allowed dependencies

| Module | Responsibility | May depend on |
|---|---|---|
| `shared` | Small immutable cross-cutting values | JDK only |
| `game-domain` | Match, teams, inventory, combat, building, resource and victory rules | `shared` |
| `protocol` | Versioned message schemas and transport interfaces | `shared` |
| `map-format` | `.twmap` schemas, validation and safe package rules | `shared` |
| `server` | Authoritative simulation, adapters, administration and persistence | core modules, SLF4J |
| `client` | jMonkeyEngine rendering, input, prediction, interpolation and UI | core modules, jMonkeyEngine |
| `map-studio` | jMonkeyEngine-based authoring UI | `shared`, `map-format`, jMonkeyEngine |
| `bot-client` | Headless integration and load-test behavior | core modules, SLF4J |

Core modules must never import `com.jme3`, LWJGL, desktop UI toolkits, concrete
socket libraries, or server persistence adapters. The root `verifyArchitecture`
task enforces the renderer part of this rule and will be expanded as adapters are
introduced.

## Dependency direction

```text
shared
  ^
  +-- game-domain
  +-- protocol
  +-- map-format
          ^
          |
client / server / map-studio / bot-client
```

There is no dependency from the domain to the client, server runtime, transport,
SQLite, filesystem, clock, random generator, or jMonkeyEngine. Time and randomness
must enter through explicit interfaces so tests can be deterministic.

## Server authority

The client sends input and intentions. The server validates and resolves movement,
hits, damage, inventory mutations, resource collection, building, crafting, death,
team membership, phase changes, and match results. Presentation prediction may hide
latency but cannot commit authoritative state.

## Fixed-tick simulation

The planned default is 20 simulation ticks per second. Network I/O, map loading,
disk writes, compression, and administrative work must not block the simulation
thread. Capacity for 40 players is a target that must be proven by the bot and soak
tests defined in epic #17, not inferred from this architecture.

## Data contracts

Network messages, server configuration, item/class/recipe data, and `.twmap`
packages are explicitly versioned. Unknown or malformed data is rejected with a
bounded, user-readable error. Java native object serialization is forbidden.

## Decisions intentionally deferred

- Concrete TCP/UDP transport implementation: issue #22 defines the boundary first.
- Match state machine: issue #21.
- Fixed-tick server runtime: issue #25.
- First graphical client screen: issue #26.
- `.twmap` v1 manifest: issue #23.

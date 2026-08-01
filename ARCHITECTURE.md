# Architecture

## Architectural goals

Sunderfront is built around a renderer-independent, deterministic game core and
an authoritative dedicated server. Client rendering, network transport, player
identity, map files, asset distribution, global registries, persistence, and
administration are adapters around explicit contracts.

The architecture must support Windows and Linux clients, a Linux/Windows
headless server, LAN play without Internet access, direct Internet connections,
user maps, optional global handle verification, and repeatable tests without
opening a graphics device.

## Modules and allowed dependencies

| Module | Responsibility | May depend on |
|---|---|---|
| `shared` | Small immutable cross-cutting values | JDK only |
| `game-domain` | Match, teams, inventory, combat, building, resource and victory rules | `shared` |
| `protocol` | Versioned message schemas and transport interfaces | `shared` |
| `map-format` | `.twmap` schemas, validation and safe package rules | `shared` |
| `server` | Authoritative simulation, identity policies, adapters, administration and persistence | core modules, SLF4J |
| `client` | jMonkeyEngine rendering, input, prediction, interpolation, identity profile and UI | core modules, jMonkeyEngine |
| `map-studio` | jMonkeyEngine-based authoring UI | `shared`, `map-format`, jMonkeyEngine |
| `bot-client` | Headless integration and load-test behavior | core modules, SLF4J |

Core modules must never import `com.jme3`, LWJGL, desktop UI toolkits, concrete
socket libraries, SQLite, GitHub SDKs, HTTP clients, or server persistence
adapters. The root `verifyArchitecture` task enforces the renderer part of this
rule and will be expanded as adapters are introduced.

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
SQLite, filesystem, clock, random generator, jMonkeyEngine, GitHub, or an asset
host. Time, randomness, persistence, registries, and distribution enter through
explicit interfaces so tests can remain deterministic.

## Server authority

The client sends input and intentions. The server validates and resolves
movement, hits, damage, inventory mutations, resource collection, building,
crafting, death, team membership, phase changes, and match results. Presentation
prediction may hide latency but cannot commit authoritative state.

## Player identity and names

A Sunderfront client generates its own Ed25519 key pair. The project must never
reuse or import the user's SSH private key. A stable `playerId` is derived from
the public key; a nickname is a separate claim governed by the selected server
policy.

The server authenticates possession of the private key with a versioned,
length-prefixed challenge-response transcript containing a fresh nonce and the
server/session context. The private key never leaves the client. This proves
client identity but does not replace transport encryption or server
authentication.

Identity policy is an adapter with three planned modes:

- `LOCAL_TOFU` binds a nickname to a `playerId` on first successful use;
- `GLOBAL_ONLY` accepts only active names from a verified global snapshot;
- `HYBRID` reserves global names while allowing clearly marked local guests.

The global registry is not a runtime dependency on GitHub. GitHub pull requests
may initially author claims, but servers consume a deterministic, signed and
cached snapshot through a provider interface. A future HTTPS endpoint or local
mirror can replace GitHub without changing the claim or handshake formats. See
[IDENTITY.md](IDENTITY.md) and epic #28.

## Fixed-tick simulation

The planned default is 20 simulation ticks per second. Network I/O, identity
lookups, registry refreshes, map loading, disk writes, compression, and
administrative work must not block the simulation thread. Capacity for 40
players is a target that must be proven by the bot and soak tests defined in
epic #17, not inferred from this architecture.

## Integrated graphics target

Rendering quality is selected by a measured first-run benchmark and can be
overridden by the user. The primary integrated-GPU target is 1080p at 60 FPS on
an Intel Iris Xe 80 EU / AMD Radeon 660M or 680M class device; a compatibility
target aims for 720p at 30 FPS on Intel UHD 620 / AMD Vega 8 class hardware.
These are provisional acceptance targets, not current performance claims. See
[PERFORMANCE.md](PERFORMANCE.md) and issue #32.

## Data contracts

Network messages, identity claims, registry snapshots, server configuration,
item/class/recipe data, asset lockfiles, and `.twmap` packages are explicitly
versioned. Unknown or malformed data is rejected with a bounded, user-readable
error. Java native object serialization is forbidden.

## Asset distribution boundary

Normal Git history contains code, manifests, licenses, small UI assets, and
minimal test fixtures. Large runtime and source asset packs are immutable,
versioned release artifacts referenced by exact URL, size and SHA-256 in a
lockfile. Downloaded data lives in an ignored cache and is activated only after
verification. The provider can move from GitHub Releases to another static HTTPS
host without changing pack formats. See [ASSET_PIPELINE.md](ASSET_PIPELINE.md)
and issue #33.

## Decisions intentionally deferred

- Concrete TCP/UDP transport implementation: issue #22 defines the boundary first.
- Match state machine: issue #21.
- Fixed-tick server runtime: issue #25.
- First graphical client screen: issue #26.
- `.twmap` v1 manifest: issue #23.
- Cryptographic identity implementation: issue #29.
- Global registry repository and signed snapshot pipeline: issue #30.

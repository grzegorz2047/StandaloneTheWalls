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
| `transport-bctls` | TLS 1.3, server pinning, RFC 9266 binding, bounded listener admission, session bootstrap, strict framing and async reliable I/O | `protocol`, Bouncy Castle TLS |

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
client / server / map-studio / bot-client / transport adapters
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

## Secure transport adapters

Concrete networking libraries live outside the core modules. The first reliable
adapter is `transport-bctls`. It uses the public, low-level Bouncy Castle TLS API
and does not depend on JSSE socket callbacks.

The adapter enforces TLS 1.3, ALPN `sunderfront/1`, a bounded TLS 1.3 AEAD cipher
allowlist, an Ed25519 leaf certificate, explicit server pinning/TOFU, and the RFC
9266 `tls-exporter` binding required by player identity. Socket reads must have a
finite timeout before the handshake begins. Exporter bytes are copied
synchronously inside the low-level peer's `notifyHandshakeComplete()` callback,
before Bouncy Castle clears the exporter secret.

`Tls13ServerListener` owns one bound endpoint, a dedicated accept thread and
named virtual handshake threads. Independent hard limits bound concurrent TLS
handshakes and authenticated active leases. The active permit is reserved before
TLS so successful handshakes cannot oversubscribe admission between completion
and registration. Stalled handshakes have a finite timeout. Shutdown closes the
listener socket, in-flight handshake sockets and every tracked active lease
before awaiting owned threads. The handler receives an `AcceptedTlsConnection`
lease outside the accept and simulation threads. See ADR 0008 and issue #51.

`TlsSessionBootstrap` converts an authenticated client connection or server lease
into one shared logical reliable session before the first envelope. The server
generates a non-zero RFC 4122 UUIDv4 and sends a fixed 28-byte `SFSB`
`SESSION_OFFER`. The client validates protocol/schema/type/UUID and echoes the
exact value in `SESSION_ACCEPT`. Both sides reject malformed records or a changed
UUID and then construct the same `TlsEnvelopeStream` and
`AsyncTlsReliableChannel`. The short bootstrap read timeout is reset after
success. Server-side stream closure owns the whole accepted lease, so every
terminal channel path returns listener admission. See ADR 0009 and issue #53.

The session UUID is not a secret or channel authenticator. Identity Proof V2
binds it together with the pinned `ServerId` and the exact RFC 9266 channel
binding. The bootstrap adds no custom MAC or signature because its records are
already inside authenticated TLS.

`TlsEnvelopeStream` adds strict framing for the fixed 40-byte protocol header and
bounded payload after the session UUID is known. It validates the header before
allocating payload memory, binds all envelopes to one logical session UUID,
assigns outbound sequence numbers, requires gap-free inbound sequences,
serializes writers independently from readers, and closes TLS after malformed or
cross-session input. It remains a blocking primitive and must never execute on
the fixed-tick simulation thread.

`AsyncTlsReliableChannel` implements the renderer-independent `ReliableChannel`
contract above that blocking stream. The channel owns named Java 21 virtual
threads, applies hard pending-send count and byte limits, permits exactly one
active receive, moves to a terminal state before publishing EOF or failures, and
returns an asynchronous close stage only after TLS and the owned executor have
terminated. It never uses a common pool. See ADR 0005, ADR 0006, ADR 0007 and
issue #34.

Identity challenge/proof orchestration, integration with the server command
queue, client dialing, certificate/key provisioning, public-PKI validation,
reconnect, realtime DTLS/UDP, and realtime session tokens remain separate
adapters and work items.

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

- Identity challenge/proof payload codecs and orchestration over the bootstrapped channel.
- Integration of authenticated commands with the fixed-tick command queue.
- Client connection ownership, DNS resolution and reconnect policy.
- Public-PKI certificate validation as a separate trust adapter.
- Realtime DTLS/UDP transport and replay-resistant realtime session tokens.
- Global registry repository and signed snapshot pipeline: issue #30.
- Persistent player/server trust stores and production key provisioning.

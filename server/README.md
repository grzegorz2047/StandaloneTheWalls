# Sunderfront dedicated server runtime

This module contains the first headless process lifecycle and fixed-step scheduler.
It does not yet open sockets, load a map, accept players, or claim production
capacity. This implementation is the bounded runtime slice tracked by issue #25;
network and gameplay integration remain separate work.

## Configuration

Copy `server.properties.example` and pass the resulting path explicitly:

```bash
./gradlew :server:run --args="--config /path/to/server.properties --validate-config"
```

Supported properties:

- `server.name`
- `server.tick-rate` — 10 through 60, default 20
- `server.reliable-port` — default 27420
- `server.realtime-port` — default 27421 and must differ from the reliable port
- `server.maximum-players` — 1 through 40

Unknown properties, malformed numbers, invalid ports, unsafe tick rates, and
capacities above the current product target fail closed.

## Identity admission boundary

Issue #65 adds `SessionIdentityAdmissionService` as the mandatory semantic gate
between a successful cryptographic player handshake and future lobby admission.
It accepts only the already-derived stable `playerId`, canonical handle, selected
handle policy, and the explicit registry availability state.

The ordering is intentional and must not be reversed:

1. reject a banned `playerId`;
2. only then evaluate `LOCAL_TOFU`, `GLOBAL_ONLY`, or `HYBRID` handle policy;
3. admit the session to a lobby only when the returned decision is accepted.

A banned first-use attempt therefore cannot reserve a local handle. Rejected
results never carry a verification level. Accepted local identities are marked
`LOCAL_UNVERIFIED`; accepted global identities are marked `GLOBAL_VERIFIED`.
The gate does not own sockets, TLS, private keys, IP addresses, SQLite lifecycle,
or lobby membership.

## Local identity runtime composition

Issue #68 adds `LocalIdentityRuntime` as the single local composition used by
session admission and identity administration. Its configuration contains one
SQLite database path, one different registry bundle path, and one explicit handle
authorization mode.

Opening the runtime constructs:

- handle binding and player-ban SQLite stores on the same database file;
- one atomic registry snapshot store;
- one local `SFRB` bundle provider;
- registry verification and administration services;
- the ban-before-handle session admission gate;
- the typed identity administration command service.

The runtime attempts one local registry reload during construction and retains the
typed result. A missing bundle remains `PROVIDER_FAILURE`; a rejected artifact
remains `SNAPSHOT_REJECTED`; neither creates a default snapshot. `LOCAL_TOFU` can
operate without registry data only when explicitly selected. `GLOBAL_ONLY` and
`HYBRID` keep their existing fail-closed decisions.

Registry availability is computed dynamically from the shared store, clock, and
policy. A later authorized `reload-registry` command is therefore immediately
visible to the next admission call. Bindings and player bans survive reopening via
the shared SQLite file. Trust-root parsing and process configuration are still
outside this composition, as are sockets, TLS, refresh scheduling, and lobby
membership.

## Local identity administration commands

Issues #66 and #67 define a strict typed command boundary for future console,
RCON, HTTP, or GUI adapters. `IdentityAdministrationCommandParser` accepts an
already-split list of tokens; it does not evaluate a shell or tokenize raw text.

Supported shapes are:

```text
identity list handles
identity list bans
identity inspect handle <canonicalHandle>
identity inspect ban <playerId>
identity reserve <handle> <playerId> <reason>
identity unbind <handle> <expectedPlayerId> <reason>
identity rebind <handle> <expectedPlayerId> <replacementPlayerId> <reason>
identity ban-player-id <playerId> <reason>
identity unban-player-id <playerId> <reason>
identity verify-snapshot
identity reload-registry
```

The adapter supplies a quoted multi-word reason as one token. The executor checks
one of four independent capabilities before touching a service: view identity,
manage handle bindings, manage player bans, or manage the registry provider.
Permission denial happens before any mutation or provider I/O. Successful binding
and ban commands delegate to the existing atomic audited policy services.

`verify-snapshot` loads and cryptographically verifies the configured provider
artifact without changing the active snapshot. `reload-registry` verifies first
and then activates only a monotonic higher sequence; an identical artifact returns
`UNCHANGED`. Provider, signature, rollback, and equivocation failures preserve the
last known good active snapshot.

Registry responses contain only sequence, generation time, registry root ID,
SHA-256 digest, entry count, and stable semantic result codes. They never contain
canonical JSON, signature bytes, provider exception text, private keys, IP
addresses, credentials, or sockets.

## Smoke mode

A bounded headless run is available for CI and packaging checks:

```bash
./gradlew :server:run --args="--run-for-ticks 20"
```

The value must be between 1 and 1,000,000. Smoke mode starts the same simulation
thread as normal operation and exits after the requested number of executed ticks.

## Scheduling rules

- The simulation uses monotonic nanosecond time, never wall-clock time.
- Network, file, compression, and administration work must remain outside the tick
  handler.
- Catch-up is bounded to five ticks per scheduler iteration. Additional timing debt
  is reported as skipped scheduling intervals rather than causing an unbounded
  spiral.
- The runtime owns exactly one non-daemon simulation thread and interrupts/joins it
  during shutdown.
- Tests inject a fake clock and sleeper; they do not wait on real tick intervals.

The handler is still a boundary only. Wiring the match lifecycle, map state,
transport queues, and administration belongs to later issues.

# ADR 0021: Local identity runtime composition

- Status: accepted
- Date: 2026-08-02

## Context

The signed registry, local bundle provider, handle policy, audited SQLite stores,
player bans, session admission gate, and administration commands were previously
constructed independently. A server adapter could accidentally create multiple
registry stores or point admission and administration at different databases.
That would make a successful registry reload invisible to login, or allow a ban
command to mutate state that admission never reads.

Startup also needs an explicit result when the local registry bundle is missing or
rejected. It must not invent a snapshot or silently switch authorization mode.

## Decision

`LocalIdentityRuntimeConfiguration` contains exactly:

- one SQLite database path;
- one local registry bundle path;
- one explicit `HandleAuthorizationMode`.

Both paths are normalized absolute file paths and must be different.

`LocalIdentityRuntime.open(...)` receives an already validated trust bundle,
registry policy, and clock, then creates one composition shared by every operation:

- `SqliteLocalHandleAdministrationStore` and
  `SqliteLocalPlayerBanAdministrationStore` use the same SQLite file;
- one `AtomicRegistrySnapshotStore` is used by startup reload, later administration
  reloads, freshness calculation, and session admission;
- one `RegistrySnapshotBundleFile` is the local provider;
- handle and ban administration services use the same persistent stores as the
  admission gate;
- `IdentityAdministrationCommandService` uses those same services and registry
  store.

Opening the runtime attempts one safe local reload and retains its typed
`RegistryAdministrationResult`. A valid bundle is activated. A missing or unreadable
bundle remains `PROVIDER_FAILURE`; a cryptographically rejected artifact remains
`SNAPSHOT_REJECTED`. The runtime is still constructed, but no fallback snapshot is
created.

`registryAvailability()` is calculated on every call from the shared registry store,
clock, and policy. `admit(handle, playerId)` uses the configured mode and current
availability. Therefore an authorized `reload-registry` command is visible to the
next admission without rebuilding the runtime.

## Consequences

- admission and administration cannot diverge onto separate identity state within
  this composition;
- local bindings and player bans survive reopening through the shared SQLite file;
- `LOCAL_TOFU` remains an explicit operator choice that can work without a registry;
- `GLOBAL_ONLY` and `HYBRID` preserve their existing fail-closed behavior when the
  bundle is unavailable or rejected;
- startup state is observable as a bounded semantic result rather than guessed;
- trust-root parsing, process configuration, HTTP/GitHub providers, refresh
  scheduling, sockets, TLS, and lobby membership remain outside this component.

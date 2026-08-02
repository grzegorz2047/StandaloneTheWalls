# Identity policy

`identity-policy` decides whether an already cryptographically authenticated
`playerId` may use one canonical handle. It does not open network sessions,
verify signatures, load registry files, persist SQLite data, or admit players to
a lobby.

## Registry availability

The caller supplies one explicit runtime view of the last verified registry
snapshot:

- `ABSENT` means no verified snapshot has ever been activated;
- `FRESH` means the last verified snapshot is within the configured
  `RegistrySnapshotPolicy.maximumAge()`;
- `STALE` means the snapshot was valid when activated but has since crossed the
  freshness limit.

`AtomicRegistrySnapshotStore` retains the last verified snapshot after it becomes
stale. Exactly the configured maximum age remains fresh; the first later instant
is stale. Provider, parsing, signature, rollback, or refresh failures never erase
that snapshot.

The compatibility overload accepting `Optional<VerifiedRegistrySnapshot>` treats
a present value as fresh and an empty value as absent. Server runtime code should
prefer the explicit availability value so expiry cannot be ignored accidentally.

## Modes

### `LOCAL_TOFU`

The registry snapshot is ignored. One atomic `bindOrVerify(handle, playerId)`
operation creates the first local binding, accepts the same returning identity,
or rejects a different identity. Adapters must implement the operation without a
separate read-then-write race.

### `GLOBAL_ONLY`

A fresh verified snapshot is mandatory. Missing snapshots, stale snapshots,
unknown handles, revoked entries, and player-ID mismatches have distinct
fail-closed decisions. The local binding store is never consulted.

### `HYBRID`

A fresh snapshot provides normal global authorization and delegates only handles
absent from the snapshot to local TOFU.

When the last verified snapshot is stale, it becomes a reservation-only view:

- every handle present in it remains unavailable for local binding, including
  revoked handles;
- such a login returns `REGISTRY_STALE` even when the player key matches;
- a handle absent from the stale snapshot may still use local TOFU;
- an absent snapshot remains fully fail-closed because the server cannot prove
  that a requested handle is not globally reserved.

This keeps private/LAN guests available during a registry outage without making
an outage a path to steal a known global name.

## Decisions

Accepted decisions expose one presentation level:

- `GLOBAL_VERIFIED` for a matching active global entry from a fresh snapshot;
- `LOCAL_UNVERIFIED` for a new or returning local binding.

Rejected decisions expose no verification level and contain no raw snapshot,
public key, private key, IP address, or mutable server state. `REGISTRY_STALE` is
a bounded operational rejection distinct from `REGISTRY_UNAVAILABLE`.

`InMemoryLocalHandleBindingStore` is a thread-safe reference implementation for
tests and ephemeral servers. Persistent SQLite bindings, audited administrative
rebinds, refresh scheduling, TLS/session integration, and lobby admission remain
adapters outside this module.

# Identity policy

`identity-policy` decides whether an already cryptographically authenticated
`playerId` may use one canonical handle. It does not open network sessions,
verify signatures, load registry files, persist SQLite data, or admit players to
a lobby.

## Modes

### `LOCAL_TOFU`

The registry snapshot is ignored. One atomic `bindOrVerify(handle, playerId)`
operation creates the first local binding, accepts the same returning identity,
or rejects a different identity. Adapters must implement the operation without a
separate read-then-write race.

### `GLOBAL_ONLY`

A verified active snapshot is mandatory. Missing snapshots, unknown handles,
revoked entries, and player-ID mismatches have distinct fail-closed decisions.
The local binding store is never consulted.

### `HYBRID`

A verified active snapshot is mandatory because the server otherwise cannot
know whether a handle is globally reserved. Every handle present in the
snapshot remains global, including revoked entries. Only handles absent from the
snapshot are delegated to local TOFU.

## Decisions

Accepted decisions expose one presentation level:

- `GLOBAL_VERIFIED` for a matching active global entry;
- `LOCAL_UNVERIFIED` for a new or returning local binding.

Rejected decisions expose no verification level and contain no raw snapshot,
public key, private key, IP address, or mutable server state.

`InMemoryLocalHandleBindingStore` is a thread-safe reference implementation for
tests and ephemeral servers. Persistent SQLite bindings, audited administrative
rebinds, snapshot freshness evaluation, TLS/session integration, and lobby
admission remain adapters outside this module.

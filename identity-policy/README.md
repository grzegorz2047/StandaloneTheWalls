# Identity policy

`identity-policy` decides whether an already cryptographically authenticated
`playerId` may use one canonical handle and defines atomic local administration
semantics. It also defines an independent local display-name store keyed by
`playerId`. It does not open network sessions, verify signatures, load registry
files, persist SQLite data, render names, or admit players to a lobby.

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
separate read-then-write race. Reaching the configured binding capacity returns a
distinct fail-closed decision instead of pretending that another player owns the
handle.

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

## Local handle administration

`LocalHandleAdministrationStore` extends the same binding port used by TOFU and
must perform each successful administrative mutation and its audit event as one
atomic operation. A persistent adapter must use one database transaction rather
than committing the binding and event separately.

The supported mutations are:

- `reserve(handle, playerId)`, which creates only an absent binding;
- `unbind(handle, expectedPlayerId)`, which removes only the exact value last
  inspected by the administrator;
- `rebind(handle, expectedPlayerId, replacementPlayerId)`, which replaces only
  the exact expected value.

The expected player ID makes `unbind` and `rebind` compare-and-set operations.
A concurrent login or administrator cannot cause a stale command to overwrite a
newer binding. Results distinguish applied, already matched, conflict, not found,
expectation mismatch, same player, and capacity exceeded.

Every applied administrative mutation creates exactly one immutable audit event
with a positive monotonic sequence, explicit UTC instant, bounded administrator
ID, action, canonical handle, previous and new player IDs, and bounded NFC reason.
Failed and idempotent attempts do not create events. If audit capacity is full,
the mutation is rejected so an unaudited state change cannot occur.

## Local display names

A local display name is presentation-only Unicode text assigned directly to a
stable public `playerId`. It is not a handle, login claim, registry reservation,
or security identifier. Two different player IDs may intentionally use the same
display name.

`LocalDisplayName` applies only two visible transforms: NFC normalization and
trimming Unicode whitespace from both ends. Input is capped at 512 UTF-16 code
units before normalization so validation cannot allocate from an unbounded
string. The stored result is non-empty and bounded to 64 Unicode code points and
192 UTF-8 bytes. It rejects malformed UTF-16, NUL, controls, unassigned code
points, surrogate code points, line and paragraph separators, and every Unicode
format character, including bidi overrides, bidi isolates, and zero-width
formatting controls. Validation errors are bounded and never include the rejected value.

`LocalDisplayNameAdministrationStore` exposes deterministic lookup/listing and
atomic `setDisplayName` / `clearDisplayName` operations. Every mutation carries
an explicit expectation: absent, present, or an exact previous display name.
There is no unconditional last-write-wins path. Applied mutations create exactly
one monotonic append-only audit event containing the administrator, timestamp, player ID,
previous value, new value, and bounded reason. No-op and failed attempts create
no event, and exhausted state or audit capacity blocks the mutation.

The stable result codes are `APPLIED`, `UNCHANGED`, `NOT_FOUND`,
`EXPECTATION_MISMATCH`, `INVALID_VALUE`, and `CAPACITY_EXCEEDED`. There is no
binding-not-found result because a local display name may be assigned to any
valid authenticated player ID, including an identity admitted through
`GLOBAL_ONLY`; creating the display name grants no authorization.

`InMemoryLocalDisplayNameStore` is the thread-safe reference implementation for
tests and ephemeral use. It is deliberately not injected into
`HandleAuthorizationService`, registry lookup, local TOFU binding, or player-ban
admission.

## Decisions

Accepted decisions expose one presentation level:

- `GLOBAL_VERIFIED` for a matching active global entry from a fresh snapshot;
- `LOCAL_UNVERIFIED` for a new or returning local binding.

Rejected decisions expose no verification level and contain no raw snapshot,
public key, private key, IP address, or mutable server state. `REGISTRY_STALE` is
a bounded operational rejection distinct from `REGISTRY_UNAVAILABLE`, and
`LOCAL_BINDING_CAPACITY_EXCEEDED` is distinct from an ownership conflict.

Binding views are sorted by canonical handle. Display-name views are sorted by
`playerId`. Audit views are ordered by sequence. Returned collections and events
are immutable and contain no private keys, IP addresses, raw registry snapshots,
or mutable server state.

Persistent SQLite storage, command parsing and permissions, refresh scheduling,
TLS/session integration, rendering, chat, lobby admission, and display-name UI
remain adapters outside this module.

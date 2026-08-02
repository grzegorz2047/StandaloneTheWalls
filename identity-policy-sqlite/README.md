# SQLite identity policy adapter

`identity-policy-sqlite` persists the local binding, player-ban, and local
display-name administration contracts from `identity-policy` in one
operator-owned SQLite file. Production code uses standard JDBC only; the Xerial
driver is a runtime dependency.

## Schema history

### Schema v1

- `local_identity_schema`, with one singleton metadata row;
- `local_handle_bindings`, keyed by canonical handle and containing only the
  public `playerId`;
- `local_handle_audit`, an ordered append-only reserve/unbind/rebind history;
- update and delete triggers protecting handle-audit rows.

### Schema v2

The v1→v2 migration adds:

- `local_player_bans`, keyed by public `playerId`;
- `local_player_ban_audit`, an ordered append-only ban/unban history;
- update and delete triggers protecting ban-audit rows.

### Schema v3

The atomic v2→v3 migration adds:

- `local_player_display_names`, keyed only by public `playerId`, with no unique
  constraint on `display_name`;
- `local_player_display_name_audit`, with monotonic sequence, administrator,
  timestamp, action, player ID, previous value, new value, and bounded reason;
- update and delete triggers protecting display-name audit rows;
- schema metadata version `3`, written only after all new objects exist.

The player-ID primary key provides the lookup/list ordering index. SQLite checks
repeat the 64-code-point and 192-byte storage ceilings, while the Java domain type
performs the full NFC and Unicode-category validation. Display names are
intentionally not indexed as unique because they are presentation values, not
identities or reservations.

## Migration and validation

Opening a new empty database creates v1, then the existing ban store performs its
v1→v2 transaction, and the display-name store performs v2→v3. Opening an existing
v2 database performs only the v2→v3 migration. That migration runs under one
`BEGIN IMMEDIATE` transaction; any table, trigger, validation, or metadata update
failure rolls back the entire migration.

The migration does not rewrite or infer existing data. It preserves all handle
bindings, player bans, both existing audit streams, and their sequence numbers.
Schema versions newer than v3, missing required objects, inconsistent metadata,
failed integrity checks, and configured capacity violations fail closed. Persisted
values are reconstructed through bounded domain types when read, so malformed rows
are rejected instead of silently normalized.

The handle store accepts schema versions v1 through v3 while owning only its v1
objects. The ban store still owns the v1→v2 migration and accepts v2 or a complete
v3. The display-name store alone owns v2→v3.

## Transaction model

Every write opens a short-lived connection and starts `BEGIN IMMEDIATE`. SQLite
therefore serializes writers before an adapter reads current state, expectations,
capacity counters, or the next audit sequence.

- `bindOrVerify` reads and creates the first binding in one transaction;
- handle reserve/unbind/rebind commit with their audit event;
- player ban/unban commit with their audit event;
- display-name set/clear check the explicit absent/present/exact expectation,
  mutate the row, and insert exactly one audit event in one transaction;
- SQL failure, trigger rejection, or commit failure rolls back both state and
  audit;
- full state or audit capacity returns a bounded fail-closed result without a
  partial mutation.

No-op display-name sets and failed expectations do not insert events. Multiple
store instances use SQLite writer serialization, so two concurrent exact updates
can produce only one applied mutation.

The adapter configures foreign-key enforcement and a bounded SQLite busy timeout
on every connection. It uses SQLite durable rollback-journal semantics and does
not emulate atomicity with application-level read-then-write locks.

## Stored data boundary

The file stores public player IDs, canonical handles, local display names,
bounded administrator IDs/reasons, timestamps, and audit actions. It stores no
private key, IP address, credential, raw identity proof, TLS session, registry
snapshot, or global registry root.

Command parsing, permissions, UI, rendering, chat, network protocol changes,
registry refresh scheduling, and lobby integration remain outside this module.

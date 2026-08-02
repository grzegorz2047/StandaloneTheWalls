# SQLite identity policy adapter

`identity-policy-sqlite` persists the local binding and administration contracts
from `identity-policy` in one operator-owned SQLite file. Production code uses
standard JDBC only; the Xerial driver is a runtime dependency.

## Schema v1

The database contains:

- `local_identity_schema`, with one row identifying schema version `1`;
- `local_handle_bindings`, keyed by canonical handle and containing only the
  public `playerId`;
- `local_handle_audit`, an ordered append-only history of successful reserve,
  unbind and rebind operations;
- update and delete triggers that reject mutation of existing audit rows.

Opening a new empty database creates all schema objects in one transaction.
Opening a database with missing objects, inconsistent metadata, a failed SQLite
integrity check, a newer schema version, or data above configured capacities
fails closed. The adapter never guesses how to repair an unknown schema.

## Transaction model

Every write opens a short-lived connection and starts `BEGIN IMMEDIATE`. SQLite
therefore serializes writers before the adapter reads the current binding or
capacity counters.

- `bindOrVerify` reads and creates the first binding in one write transaction;
- `reserve` inserts the binding and audit event in one transaction;
- `unbind` and `rebind` compare the current binding with the expected `playerId`,
  mutate it and insert the event in one transaction;
- any SQL failure rolls back both the binding mutation and audit insert;
- full binding or audit capacity returns the bounded fail-closed result without
  partially changing persistent state.

The adapter configures foreign-key enforcement and a bounded SQLite busy timeout
on every connection. The database uses SQLite's normal durable rollback-journal
semantics; no application-level temporary copy or read-then-write lock is used.

## Runtime behavior

Multiple store instances and process restarts share the same constraints and
transactions. Binding views are ordered by canonical handle. Audit views are
ordered by positive sequence and revalidated through the bounded value types from
`identity-policy` before being returned.

The file stores no private key, IP address, credential, TLS session, registry
snapshot, or global registry root. Command parsing, administrator permissions,
bans, aliases, network refresh and lobby admission remain outside this module.

# ADR 0016: Transactional SQLite local identity store

- Status: Accepted
- Date: 2026-08-02
- Issues: #63, #31
- Depends on: #60, #61, #62

## Context

The local identity policy now defines atomic first-use binding and audited
administrator mutations, but the in-memory reference store loses state on
restart. The persistent adapter must preserve the same compare-and-set behavior
across multiple store instances and must not commit a binding change without its
audit event.

A process-local monitor is insufficient because it does not coordinate separate
store instances or survive restart. Application-level read-then-write logic also
leaves a race between checking the current player ID and applying a mutation.

## Decision

Add renderer-independent `identity-policy-sqlite`, implemented with standard JDBC
and the Xerial SQLite runtime driver.

Schema version 1 contains one metadata row, one canonical-handle binding table
and one append-only audit table. Audit update and delete triggers reject mutation
of committed history. The adapter stores public player IDs only.

Every operation that may write begins `BEGIN IMMEDIATE` on a short-lived
connection. SQLite obtains the writer reservation before the adapter reads the
current binding, capacities or next audit sequence.

- Local first-use reads and inserts inside one transaction.
- Reserve inserts the binding and audit event in one transaction.
- Unbind and rebind validate the explicit expected current player ID, mutate the
  binding and insert the event in one transaction.
- SQL failure, trigger rejection or commit failure rolls the transaction back.
- Binding and audit capacities are checked under the same writer transaction.

Database initialization is also transactional. A new empty file receives schema
v1. Existing databases must contain exactly the supported metadata version, all
required tables and append-only triggers, pass SQLite integrity and foreign-key
checks, and remain within configured capacities. Newer, incomplete or corrupted
schemas fail closed rather than being inferred or rewritten.

Each connection enables foreign keys and a bounded busy timeout. Results are read
in deterministic order and reconstructed through the bounded domain values from
`identity-policy`; malformed persisted values therefore fail rather than being
silently normalized.

## Consequences

- Bindings and audit survive process restart.
- Multiple adapter instances share SQLite's writer serialization and cannot both
  win one first-use or expected-player rebind.
- A failed audit insert cannot leave a changed binding.
- Audit history is append-only under the installed schema.
- The database remains a local single-server authority; synchronization across
  independent servers is not introduced.
- Command parsing, authorization of administrators, bans, aliases, registry
  refresh and TLS/lobby integration remain separate work.

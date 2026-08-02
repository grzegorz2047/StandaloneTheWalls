# ADR 0015: Atomic audited local handle administration

- Status: Accepted
- Date: 2026-08-02
- Issues: #62, #31
- Depends on: #60, #61

## Context

Local TOFU protects a canonical handle after its first authenticated login, but
administrators need explicit recovery and reservation operations. A naive
read-then-write command can overwrite a binding changed after the administrator
inspected it. Separating the state mutation from the audit insert can also leave
a changed identity without an audit event, or an event for a change that never
committed.

The semantics must be fixed before adding SQLite and command parsing so every
adapter exposes the same conflict behavior and crash boundary.

## Decision

Add `LocalHandleAdministrationStore`, extending the same
`LocalHandleBindingStore` used by authorization. It exposes three atomic
administrative operations:

- reserve an absent handle for one player ID;
- unbind only when the current player ID equals an explicit expected value;
- rebind only when the current player ID equals an explicit expected value.

`unbind` and `rebind` are compare-and-set operations. They never overwrite a
binding that changed after an administrator inspected it. Results are stable,
bounded enums distinguishing applied changes, idempotent matches, conflicts,
missing handles, expectation mismatches, same-player rebinds, and capacity
exhaustion.

Every applied administrative mutation and exactly one corresponding audit event
must commit as one store operation. Failed and idempotent attempts create no
event. A store that cannot retain the audit event must reject the mutation rather
than apply an unaudited change.

Audit events contain only:

- a positive monotonic sequence;
- an explicit instant supplied through the administration service's `Clock`;
- a bounded lowercase ASCII administrator ID;
- the reserve, unbind, or rebind action;
- the canonical handle;
- optional previous and new public player IDs with an action-specific shape;
- a trimmed, NFC-normalized, control-free bounded reason.

They never contain private keys, IP addresses, credentials, raw registry
snapshots, or mutable server objects.

The in-memory reference store uses one monitor for TOFU, administration, binding
views, and audit views. It enforces explicit binding and audit capacities. Binding
capacity exhaustion produces a distinct fail-closed authorization result.
Bindings are returned sorted by canonical handle and audit events by sequence.

A future SQLite adapter must implement the same port with one transaction for the
binding mutation and audit insert, uniqueness on canonical handle, and
compare-and-set predicates in SQL. It must not emulate atomicity with separate
application-level reads.

## Consequences

- Recovery commands cannot silently overwrite a newer binding.
- Every successful administrative change has one durable audit boundary in a
  compliant adapter.
- Audit-capacity failure is visible and blocks state mutation.
- TOFU and administration operate on one state abstraction instead of divergent
  maps.
- Command syntax, permissions, SQLite schema and migrations, bans, aliases, and
  server-session integration remain separate work.

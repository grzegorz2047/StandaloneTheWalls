# ADR 0014: Stale registry reservation view

- Status: Accepted
- Date: 2026-08-02
- Issues: #61, #31
- Depends on: #57, #59, #60

## Context

A signed registry snapshot can be cryptographically valid yet become too old for
normal authorization after it has already been activated. Treating that snapshot
as fresh forever would bypass the configured freshness policy. Forgetting it
completely would be worse for `HYBRID`: during a source outage, a known global
handle could be captured as a new local TOFU binding.

The server therefore needs to distinguish three runtime states without mutating
or deleting the last verified snapshot:

- no verified snapshot has ever been activated;
- the active snapshot is still within its maximum age;
- the active snapshot has crossed its maximum age.

## Decision

Add immutable `RegistrySnapshotAvailability` with `ABSENT`, `FRESH`, and `STALE`
states. `FRESH` and `STALE` retain the same immutable
`VerifiedRegistrySnapshot`; `ABSENT` contains none.

`AtomicRegistrySnapshotStore` derives availability from an explicit `Clock` and
`RegistrySnapshotPolicy.maximumAge()`. A snapshot exactly at the maximum-age
boundary remains fresh, matching verifier acceptance. It becomes stale only when
its age is greater than the configured maximum.

The store never removes the last verified snapshot merely because time passes or
a later refresh fails. Provider, parsing, digest, signature, policy, rollback,
and equivocation failures continue to preserve the active snapshot.

Authorization uses the following rules:

- `LOCAL_TOFU` ignores registry availability.
- `GLOBAL_ONLY` accepts global identities only from `FRESH`; `ABSENT` returns
  `REGISTRY_UNAVAILABLE`, while `STALE` returns `REGISTRY_STALE`.
- `HYBRID` with `FRESH` keeps the normal global-first behavior.
- `HYBRID` with `STALE` treats every handle present in the last verified
  snapshot as reserved, including revoked entries, but does not globally accept
  any of them. Handles absent from the stale snapshot may use local TOFU.
- `HYBRID` with `ABSENT` remains fully fail-closed because there is no evidence
  that a requested handle is not globally reserved.

The existing authorization overload accepting an optional snapshot remains as a
compatibility boundary and maps present to fresh and empty to absent. Runtime
integration should pass explicit availability.

## Consequences

- Snapshot freshness remains enforceable after activation rather than only at
  load time.
- Registry outages cannot turn a known global or revoked handle into a local
  guest handle.
- Private and LAN servers in `HYBRID` may continue accepting genuinely unknown
  local handles while operating from a stale last-known-good snapshot.
- A matching global player is rejected once the snapshot is stale until a fresh
  snapshot is activated; this is deliberate fail-closed behavior.
- The last verified snapshot stays useful as bounded reservation evidence across
  provider failures.
- Scheduling, retry/backoff, network providers, SQLite persistence,
  administrative overrides, and user-facing messaging remain separate work.

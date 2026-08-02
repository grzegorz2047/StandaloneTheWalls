# ADR 0013: Canonical handle authorization policy

- Status: Accepted
- Date: 2026-08-02
- Issues: #60, #31
- Depends on: #29, #57, #59

## Context

Identity Proof V2 establishes possession of one player private key and produces
an authenticated `playerId` and canonical handle. Signed registry snapshots
establish an offline-verifiable global view. Neither mechanism decides by itself
whether a server should authorize that handle under its configured policy.

Putting authorization directly in TLS, SQLite, lobby code, or a registry
provider would duplicate fallback rules and make outages capable of changing
name ownership. Local first-use binding also requires one atomic operation;
separate lookup and insert steps allow two concurrent first logins to win.

## Decision

Add renderer-independent `identity-policy`, depending only on
`identity-registry` and its public protocol identity types.

The module exposes three modes:

- `LOCAL_TOFU` ignores the registry and atomically binds or verifies the local
  `handle -> playerId` pair.
- `GLOBAL_ONLY` requires a verified active snapshot and never consults local
  bindings.
- `HYBRID` requires a verified active snapshot, treats every present handle as
  globally reserved, including revoked entries, and delegates only absent
  handles to local TOFU.

`HYBRID` without a snapshot returns `REGISTRY_UNAVAILABLE`. It does not degrade
to local-only authorization because the server cannot prove that a requested
handle is not globally reserved.

The local persistence boundary is one atomic `bindOrVerify` operation with
`BOUND`, `MATCHED`, or `CONFLICT` outcomes. A thread-safe in-memory reference
implementation uses `ConcurrentMap.putIfAbsent`; a future SQLite adapter must
provide equivalent transactional semantics.

Authorization returns one bounded enum decision. Accepted global decisions are
`GLOBAL_VERIFIED`; accepted local decisions are `LOCAL_UNVERIFIED`. Rejections
have no verification level and distinguish unavailable registry, unknown global
handle, revoked global handle, global player mismatch, and local binding
conflict.

## Consequences

- Cryptographic authentication, name authorization, and lobby admission remain
  separate phases.
- Global entries always take precedence over stale local bindings in `HYBRID`.
- A revoked global handle cannot silently become a local guest handle.
- First-start offline play remains available through explicit `LOCAL_TOFU`.
- `GLOBAL_ONLY` and `HYBRID` remain fail-closed when no verified active snapshot
  is supplied.
- Snapshot loading, freshness/expiry evaluation, SQLite migrations, audited
  administrative rebinds, bans, TLS integration, and user-facing messages stay
  outside this module.

# ADR 0020: Authorized registry verification and reload

- Status: accepted
- Date: 2026-08-02

## Context

The signed registry service originally exposed one `refresh` operation that loaded,
verified, and activated an artifact. Administration needs two different actions:
operators must be able to verify a candidate without changing the active registry,
and they must be able to request a monotonic reload explicitly.

A console or remote administration adapter must not receive raw canonical JSON,
signature bytes, provider exception text, or an API that can activate data before
checking the caller's permission.

## Decision

`RegistrySnapshotService` exposes separate `verify(provider)` and
`activate(verifiedSnapshot)` operations. Existing `refresh(provider)` remains a
composition of those two operations.

The server administration command model adds these exact, argument-free shapes:

- `identity verify-snapshot`;
- `identity reload-registry`.

Both require the independent `MANAGE_REGISTRY` capability. The command executor
checks that capability before invoking `RegistryAdministrationOperations`; an
unauthorized command therefore cannot read a file, contact a future remote
provider, or affect the active registry.

`verify-snapshot` loads and cryptographically verifies the provider artifact but
never calls activation. `reload-registry` verifies first and then delegates to the
atomic monotonic store, returning `ACTIVATED` for a higher sequence and
`UNCHANGED` for the same sequence and digest.

Administrative success exposes only a bounded summary:

- sequence;
- generation timestamp;
- registry root ID;
- lowercase SHA-256 digest;
- entry count.

The summary excludes canonical JSON and signature bytes. Provider failures map to
`PROVIDER_FAILURE` without exposing the provider exception message. Verification,
rollback, and equivocation failures map to `SNAPSHOT_REJECTED` plus the stable
`RegistrySnapshotException.Code`.

## Consequences

- an operator can validate a candidate artifact without changing admission policy;
- authorization always precedes provider I/O;
- bad signatures, rollback, equivocation, and provider failure preserve the last
  known good active snapshot;
- local adapters receive stable typed outcomes rather than exception text;
- the global registry remains read-only from the dedicated server;
- HTTP/GitHub providers, retry and backoff, automatic refresh scheduling, raw
  console tokenization, and network administration endpoints remain separate
  concerns.

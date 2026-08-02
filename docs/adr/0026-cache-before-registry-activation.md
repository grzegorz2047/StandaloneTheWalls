# ADR 0026: Verified registry cache commits before in-memory activation

- Status: accepted
- Date: 2026-08-02

## Context

Remote and local providers return untrusted `RegistrySnapshotArtifact` values. The
core verifier and monotonic store already preserve the last-known-good active
snapshot, while `RegistrySnapshotBundleFile` can atomically replace the restart
cache. Combining those operations naively creates inconsistent failure windows:

- activation followed by cache persistence can expose a new in-memory snapshot
  even when the durable write fails;
- cache persistence followed by a separate activation decision can overwrite the
  restart cache with a rollback or same-sequence equivocation;
- a monotonic pre-check outside the activation lock can race another refresher and
  let an older artifact overwrite a newer cache.

## Decision

`AtomicRegistrySnapshotStore` owns one cache-before-activation commit boundary
under the same synchronized lock as rollback, equivocation and idempotency checks.

For a verified candidate it performs these steps:

1. compare the candidate with the current active snapshot;
2. reject rollback or same-sequence equivocation before any commit side effect;
3. return `UNCHANGED` for the same sequence and digest without invoking the commit;
4. invoke one checked commit hook while the active snapshot is still unchanged;
5. publish the candidate as active only after the hook completes successfully.

A commit failure leaves the previous active snapshot unchanged. Activation cannot
re-enter through its own commit hook. Read-only inspection of the old active state
remains possible inside the hook for validation and adapter logic.

`RegistrySnapshotService.refreshAndCommit(...)` loads the provider exactly once,
verifies that exact artifact, and passes the same artifact instance together with
the corresponding `VerifiedRegistrySnapshot` to the commit hook.

`RegistrySnapshotCachingRefreshService` uses
`RegistrySnapshotBundleFile.storeVerified(...)` as that hook. The file adapter
therefore completes its same-directory forced temporary write and atomic replace
before the verified snapshot becomes visible in memory.

## Consequences

- provider, verifier, rollback, equivocation and cache-write failures preserve both
  the prior active snapshot and prior bundle;
- an identical artifact does not rewrite the bundle;
- concurrent refreshes cannot commit an older cache after a newer activation;
- a crash after the atomic file replacement but before in-memory publication can
  leave only a newer fully verified bundle, which normal startup can verify and
  activate after restart;
- the commit hook is intentionally executed while holding the registry activation
  lock, so implementations must remain bounded and must not call activation
  recursively;
- existing `activate(...)` and `refresh(...)` APIs retain their previous behavior;
- retry, backoff, jitter, scheduling and process selection of remote versus local
  providers remain separate concerns.

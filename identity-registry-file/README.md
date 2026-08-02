# Identity registry file adapter

`identity-registry-file` is a renderer-independent filesystem adapter for the
trust-neutral `RegistrySnapshotProvider` contract from `identity-registry`.
It does not establish trust: every loaded artifact still has to pass
`RegistrySnapshotVerifier` and the configured trust bundle and policy before
activation.

## Bundle format v1

A bundle is one regular file, conventionally named `registry-v1.sfrb`. All
integers are unsigned or non-negative and encoded in network byte order
(big-endian).

| Offset | Size | Field |
|---:|---:|---|
| 0 | 4 | ASCII magic `SFRB` |
| 4 | 1 | format version `1` |
| 5 | 3 | reserved zero bytes |
| 8 | 4 | canonical JSON byte length |
| 12 | 32 | detached SHA-256 digest |
| 44 | 64 | detached Ed25519 signature |
| 108 | variable | exact RFC 8785/JCS canonical JSON bytes |

The declared JSON length must be at least one byte, must not exceed the local
configured limit or `RegistrySnapshotPolicy.ABSOLUTE_MAXIMUM_JSON_BYTES`, and
must consume the rest of the file exactly. Unknown versions, non-zero reserved
bytes, truncation, trailing data, directories and symbolic links are rejected.
The provider returns the detached values without trusting them.

## Atomic cache writes

`RegistrySnapshotBundleFile.storeVerified(...)` accepts an artifact only when it
matches a `VerifiedRegistrySnapshot` produced by the core verifier. Matching
covers the detached digest, detached signature and a fresh SHA-256 of the exact
JSON bytes.

The adapter writes a temporary file in the destination directory, flushes the
bytes to stable storage with `FileChannel.force(true)`, and replaces the target
with `ATOMIC_MOVE`. It intentionally does not fall back to a non-atomic move.
A failed validation or write leaves the previous target untouched; a failed
atomic move leaves the temporary file cleanup failure suppressed on the primary
exception.

The bundle contains public registry material only. It must never contain player
private keys, recovery material, local bindings, bans or other mutable server
state.

## Verified cache-before-activation refresh

`RegistrySnapshotCachingRefreshService` combines an arbitrary untrusted
`RegistrySnapshotProvider`, the core verifier and monotonic store, and this atomic
bundle writer. One refresh follows this order:

1. load the provider artifact exactly once;
2. verify that exact artifact under the configured trust bundle and policy;
3. under the registry store's activation lock, reject rollback or equivocation;
4. atomically persist the exact verified artifact with `storeVerified(...)`;
5. publish the verified snapshot as active only after the file commit succeeds.

An identical sequence and digest returns `UNCHANGED` without rewriting the file.
Provider failure, invalid signature, rollback, equivocation, artifact mismatch or
cache-write failure leaves both the previous active snapshot and previous bundle
unchanged.

The cache commit runs under the same lock as the monotonic activation decision.
Consequently, two concurrent refreshes cannot let an older artifact overwrite the
bundle after a newer snapshot has become active. Commit implementations must stay
bounded and must not call registry activation recursively.

A process crash after the atomic move but before the final in-memory assignment can
leave a newer bundle on disk. This is safe because the bundle contains only an
artifact already accepted by the verifier; normal startup verifies it again before
activation.

This service deliberately does not choose a provider, retry, back off, add jitter,
schedule refreshes, or change process configuration. Those are higher-level
runtime concerns.

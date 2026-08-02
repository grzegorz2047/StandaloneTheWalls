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

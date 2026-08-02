# ADR 0012: Local registry bundle and verified cache

- Status: Accepted
- Date: 2026-08-02
- Issues: #59, #30, #31
- Depends on: ADR 0011, #57

## Context

ADR 0011 defines verification and in-memory monotonic activation of signed
registry snapshots, but deliberately leaves filesystem providers and persistent
cache outside the core module. A server implementing `GLOBAL_ONLY` or `HYBRID`
must be able to restart without Internet access and recover its last known
artifact without trusting a partially written set of JSON, digest and signature
files.

The filesystem remains an untrusted transport. Reading a file cannot bypass
canonical JSON, digest, root signature, freshness or rollback verification.
Likewise, a cache API must not make it easy to persist an artifact unrelated to
the snapshot that the verifier actually accepted.

## Decision

Add a separate `identity-registry-file` adapter module depending on
`identity-registry`. The core module remains independent of filesystem APIs.

Use one binary bundle file with:

1. ASCII magic `SFRB`;
2. format version `1`;
3. three reserved zero bytes;
4. a four-byte big-endian JSON length;
5. the exact 32-byte detached SHA-256 digest;
6. the exact 64-byte detached Ed25519 signature;
7. the exact canonical snapshot JSON bytes.

The fixed header is 108 bytes. The declared JSON must consume the remainder of
the file exactly and remain within both the adapter limit and the core absolute
limit. The reader rejects symbolic links, non-regular files, unsupported
versions, non-zero reserved bytes, impossible lengths, truncation and trailing
data. Its output is still an untrusted `RegistrySnapshotArtifact`.

`VerifiedRegistrySnapshot` retains the verified detached signature in addition
to its digest and exposes `matchesArtifact`. Matching requires:

- the same detached digest;
- the same detached signature;
- a fresh SHA-256 of the candidate JSON equal to the verified digest.

The cache writer requires that match before any filesystem mutation. It creates
a temporary file in the destination directory, writes and forces the complete
bundle, then replaces the target using `ATOMIC_MOVE`. There is no non-atomic
fallback. Cleanup errors are attached as suppressed exceptions rather than
hiding the primary failure.

## Consequences

- A local mirror and the last-known-good cache use the same portable file and
  provider implementation.
- Crash consistency does not depend on coordinating three detached files.
- A valid cached artifact is still reverified against the current trust bundle,
  minimum sequence, age and future-skew policy on every process start.
- Updating policy or removing a trusted root can make an old cache unusable; the
  file never overrides local trust configuration.
- Filesystems without atomic replacement support cannot be used for cache writes
  through this adapter. Operators must choose a suitable local filesystem rather
  than accepting torn writes.
- HTTP, GitHub Release download, refresh scheduling, SQLite bindings and server
  authorization modes remain separate work.

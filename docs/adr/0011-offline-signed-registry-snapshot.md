# ADR 0011: Offline verification and monotonic activation of registry snapshot v1

- Status: Accepted for the first global-registry consumption slice
- Date: 2026-08-02
- Issues: #30, #31, #57
- Depends on: ADR 0003, ADR 0004, ADR 0010

## Context

A server cannot implement `GLOBAL_ONLY` or the reserved-name part of `HYBRID`
until it has an independently verifiable view of global handle state. GitHub may
host claim review and release artifacts, but it must not become a live trust or
availability dependency of a running match.

HTTPS alone does not prove that a downloaded document was authored by the
registry root, is canonical, is newer than the active snapshot, or maps each
`playerId` to the public key from which it was derived. A failed refresh must not
discard the last valid offline snapshot.

## Decision

### Module boundary

The renderer-independent `identity-registry` module depends on `protocol` for
canonical handles and player IDs. It has no dependency on GitHub APIs, HTTP,
SQLite, the filesystem, server runtime, client UI or jMonkeyEngine.

A `RegistrySnapshotProvider` returns an untrusted detached artifact. Trust is
established only by `RegistrySnapshotVerifier` and a locally configured
`RegistryTrustBundle`.

### Canonical snapshot payload v1

The signed payload is exact UTF-8 JSON canonicalized according to RFC 8785 JCS.
The verifier re-canonicalizes the received document and requires byte-for-byte
equality before parsing semantic fields.

Top-level fields are:

| Field | Rule |
|---|---|
| `entries` | array of resolved entries, strictly increasing by canonical handle |
| `generatedAt` | canonical `Instant.toString()` UTC timestamp |
| `rootKeyId` | stable ID derived from a canonical Ed25519 root SPKI |
| `schema` | integer `1` |
| `sequence` | non-negative monotonic 64-bit integer |

Each entry contains exactly:

| Field | Rule |
|---|---|
| `handle` | canonical `[a-z0-9_]{3,24}` handle |
| `playerId` | canonical `sf1_...` identifier |
| `publicKey` | canonical Base64 of canonical Ed25519 SubjectPublicKeyInfo |
| `status` | `ACTIVE` or `REVOKED` |

The verifier derives the player ID from `publicKey` and requires exact equality
with `playerId`. Entries must already be strictly sorted; duplicate handles and
out-of-order arrays are rejected rather than normalized.

Jackson Core performs bounded streaming schema parsing with strict duplicate-key
detection. Unknown fields, missing fields, invalid token types, trailing JSON,
non-canonical timestamps, invalid Base64, unsupported status and non-Ed25519 keys
are rejected.

### Detached artifact

`RegistrySnapshotArtifact` contains:

- exact canonical JSON bytes;
- a 32-byte SHA-256 digest of those exact bytes;
- a 64-byte Ed25519 signature over those exact bytes.

The signature is not over the digest and no algorithm identifiers are accepted
from the artifact. Version 1 fixes SHA-256 and Ed25519.

Verification order is bounded size, canonical-byte equality, detached digest,
strict schema, trusted root lookup, root signature and local freshness/sequence
policy.

### Root trust

A root identifier is:

```text
rootKeyId = "sfr1_" + lowercase-base32-no-padding(
    sha256(canonical Ed25519 SubjectPublicKeyInfo)
)
```

`RegistryTrustBundle` accepts only public-key bytes and derives every ID itself.
An artifact cannot introduce a trusted key or supply an arbitrary ID/key pair.
Multiple roots may be configured explicitly for a planned transition, but this
slice does not define an automatic root-rotation ceremony.

### Local acceptance policy

`RegistrySnapshotPolicy` independently bounds:

- minimum acceptable sequence;
- maximum snapshot age;
- maximum future clock skew;
- maximum canonical JSON bytes;
- maximum number of entries.

Absolute implementation ceilings prevent a configuration error from allowing an
unbounded document.

### Atomic activation

`AtomicRegistrySnapshotStore` receives only already verified immutable snapshots:

- a higher sequence is activated;
- the same sequence and digest is idempotent `UNCHANGED`;
- a lower sequence is rejected as `ROLLBACK`;
- the same sequence with another digest is rejected as `EQUIVOCATION`.

The store changes its active reference only after all checks succeed. Provider,
canonicalization, parsing, digest, signature, freshness, rollback and
equivocation failures leave the previous active snapshot unchanged.

### Public vector

The public key is:

```text
MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=
```

It gives:

```text
playerId  = sf1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua
rootKeyId = sfr1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua
```

For sequence `7`, generated time `2026-08-02T00:00:00Z`, handle
`player_one`, and status `ACTIVE`, the canonical JSON is 333 bytes and its
SHA-256 digest is:

```text
f160bf701d0e1291d50f958ac55941cc2fb63a4e9807ef9c847582affd9e3899
```

The standalone vector file contains the exact JSON bytes. It intentionally does
not publish a private registry root or a fixed valid signature. Positive tests
generate an ephemeral Ed25519 root and verify a real detached signature.

## Validation requirements

Tests must prove:

- exact public canonical JSON and digest vector;
- valid offline Ed25519 verification with a configured root;
- whitespace, different property order, duplicate keys, unknown/missing fields,
  trailing data and non-canonical timestamps are rejected;
- wrong digest, signature and unknown root are rejected;
- invalid/non-canonical keys and player-ID mismatch are rejected;
- entries must be strictly sorted and unique;
- byte, entry, age, future-skew and minimum-sequence policies are enforced;
- identical artifacts from different providers produce the same verified digest;
- rollback and same-sequence equivocation preserve the active snapshot;
- provider and verification failures preserve the active snapshot;
- the complete repository quality gate passes with strict locks and dependency
  verification.

## Consequences

- A running server can validate and retain registry state entirely offline.
- GitHub Releases, a static HTTPS mirror and a local file can be interchangeable
  byte providers without changing trust semantics.
- Snapshot representation, trust roots and monotonic activation are explicit and
  testable before authorization modes consume them.
- A malicious or unavailable provider cannot replace the last valid state.

This slice does not yet authorize a player, persist snapshots, fetch from the
network, author claims, resolve confusable handles or define root-transition
signatures.

## Rejected alternatives

- **Trusting HTTPS or GitHub authentication:** authenticates a transport/account,
  not the immutable registry payload or rollback order.
- **Embedding the root key in the artifact:** lets an attacker choose the trust
  anchor.
- **Signing a parsed object or reserialized JSON:** permits incompatible byte
  representations and parser-dependent signatures.
- **Signing only the detached digest:** adds an avoidable second signed format;
  v1 signs the exact canonical bytes directly.
- **Sorting or deduplicating entries during verification:** silently changes the
  signed semantic order and can hide conflicting claims.
- **Replacing active state before verification:** converts a failed refresh into
  an outage or rollback.

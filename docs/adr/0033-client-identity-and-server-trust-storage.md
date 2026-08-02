# ADR 0033: Portable client identity and server trust storage

- Status: Accepted
- Date: 2026-08-02
- Decision owners: Sunderfront maintainers
- Related: #34, #87, #86

## Context

The reliable TLS and Identity Proof V2 layers already define renderer-independent
`PlayerIdentityStore` and `ServerTrustStore` boundaries. Direct Connect cannot use
in-memory test stores: restarting the client must preserve the same Ed25519
identity and previously confirmed server pins.

The first alpha must work without a paid service, mandatory account, or an
operating-system-specific keychain implementation. At the same time, storage
failure must never be converted into a new identity or an empty trust store. A
torn write, malformed file, first-use race, symbolic link, or changed server key
must fail closed while preserving last-known-good state.

## Decision

The client receives an explicit data directory and composes two independent
portable file adapters:

- `FilePlayerIdentityStore` for one application-specific Ed25519 pair;
- `FileServerTrustStore` for public server pinning records.

Neither adapter selects a system directory. Presentation or release composition
must provide the path explicitly.

### Player identity format

`SFKI` schema 1 is a strict bounded binary container containing canonical PKCS#8
DER private-key bytes and canonical SubjectPublicKeyInfo DER public-key bytes.
Every load:

- requires a regular non-symlink file within a hard size bound;
- rejects unknown magic/version, impossible lengths, truncation and trailing
  bytes;
- decodes only Ed25519 and requires byte-for-byte canonical re-encoding;
- proves that private and public keys form one pair with an Ed25519 signature
  probe before returning them.

An invalid existing file is never replaced automatically.

### Trust-store format

`SFTR` schema 1 is a strict bounded binary map sorted by canonical
`ServerReference`. Each record contains the exact `ServerId`, numeric source and
bounded audit reason. Loading rejects malformed UTF-8, unknown source codes,
duplicates, non-strict ordering, invalid domain values, truncation and trailing
bytes.

Updates rewrite the complete small store. `saveIfAbsent` and `replace` retain the
compare-and-set contract required by `ServerTrustService`; a stale operation
leaves the active file untouched.

### Atomicity and concurrency

Both adapters use:

1. a process-local lock keyed by the lock-file path;
2. an operating-system exclusive file lock for cross-process coordination;
3. a same-directory unique temporary file;
4. a forced file channel write;
5. a required atomic move to activate the new bytes.

There is no non-atomic move fallback. If the filesystem cannot provide the
required operation, the update fails and the prior target remains active.

For player identity creation, a loser of the first-use race reloads the persisted
winner. It never continues with an unpersisted second identity.

### Permissions

On POSIX filesystems, directories are restricted to owner `0700` and files to
owner `0600`. On Windows, the adapter relies on the chosen directory's ACLs and
makes no encryption-at-rest claim.

### Failure surface

Identity persistence adds stable semantic failure codes for read, write, invalid
store and first-use conflict. Public errors do not include key bytes or full file
contents. Trust-store failures remain behind the bounded
`ServerTrustStoreException` adapter boundary.

## Consequences

### Positive

- A restart preserves `playerId` and server pins without a central service.
- Concurrent first starts converge on one identity.
- Torn or malformed replacement data cannot silently displace the active file.
- The protocol module remains independent of filesystem APIs.
- The Direct Connect service can use the existing trust and identity contracts.

### Negative

- The portable private-key file is not encrypted at rest.
- File locking and atomic moves depend on filesystem support.
- Rewriting the whole trust store limits it to a deliberately small bounded
  dataset.
- The initial release still needs UI for backup warnings, explicit trust reset
  and changed-key replacement.

## Alternatives rejected

### Java native serialization

Rejected because it is unsafe for untrusted or corrupted data, unstable as a
long-lived contract and forbidden by repository policy.

### JSON containing private key material

Rejected because a text representation increases accidental disclosure risk and
does not improve interoperability for an application-private key. The compact
binary container has a smaller parser and exact bounds.

### Java KeyStore as the only implementation

Rejected for the first alpha because password/key-management semantics would be
invented without a complete user recovery and unlock flow. A later secure
keychain adapter can implement the existing interface without changing
`PlayerIdentity` or the network protocol.

### Generate a new identity after any load error

Rejected because it would silently break local bindings and global identity
continuity, and could hide disk corruption or permission regressions.

### Non-atomic fallback rename

Rejected because a successful method return must mean that one complete file is
active. Unsupported atomic replacement is an explicit storage failure.

## Follow-up

- #88 consumes these stores in the production Direct Connect service.
- #90 exposes first-use confirmation, changed-key warnings and explicit reset
  controls without reading raw store bytes.
- A later issue must design encrypted backup/import and optional OS keychain
  adapters before a stable release.

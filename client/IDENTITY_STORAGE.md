# Client identity and server trust storage

This document describes the portable file-backed persistence used by the first
Direct Connect Alpha. The adapter is intentionally explicit: callers construct
`ClientIdentityStorage` with a concrete data directory. The implementation does
not guess a home directory, roaming profile, cloud folder, or operating-system
credential store.

## Files

The selected data directory contains two independent files:

- `player-identity.sfki` — the one application-specific Ed25519 player identity;
- `server-trust.sftr` — public TOFU/pinning records for servers.

Lock and temporary files may exist briefly beside those files during an update.
They are runtime state and must not be committed, uploaded, packaged into a
release, or treated as portable configuration examples.

## Player identity file

`SFKI` schema 1 is a bounded binary container with this exact field order:

1. four-byte magic `SFKI`;
2. 32-bit schema version `1`;
3. 32-bit private-key byte length;
4. 32-bit public-key byte length;
5. canonical Ed25519 PKCS#8 DER private key;
6. canonical Ed25519 SubjectPublicKeyInfo DER public key.

Each key encoding is limited to 4096 bytes and the complete file is limited to
8256 bytes. Loading rejects an unknown magic or version, impossible lengths,
truncation, trailing bytes, non-canonical DER, a non-Ed25519 key, a public/private
mismatch, symbolic links, non-regular files, and oversized files.

The private key never leaves this file through logs, error text, `toString`,
network messages, server trust records, or release artifacts. The stable
`playerId` is derived from the validated public key after every load.

### First-use race

Creation is serialized by a process lock plus an operating-system file lock. A
new key pair is written to a same-directory temporary file, forced to storage,
and activated with a required atomic move. There is no non-atomic fallback.

If two clients start against the same empty directory, exactly one file wins.
The loser reloads and adopts the already persisted identity rather than
continuing with an unpersisted second `playerId`.

An existing different or invalid identity is never overwritten automatically.
Repair or reset must be an explicit operator action.

## Server trust file

`SFTR` schema 1 is a bounded binary map with this exact high-level layout:

1. four-byte magic `SFTR`;
2. 32-bit schema version `1`;
3. bounded record count;
4. records strictly sorted by canonical `ServerReference`.

Each record contains bounded canonical UTF-8 values for:

- server reference;
- stable `ServerId`;
- numeric trust source (`TOFU`, `EXPECTED_PIN`, or `EXPLICIT_REPLACEMENT`);
- bounded audit reason.

The store accepts at most 2048 records and at most 1 MiB in total. It rejects
unknown versions or source codes, malformed UTF-8, duplicate or unsorted
references, invalid domain values, truncation, trailing bytes, symbolic links,
non-regular files, and oversized files.

`saveIfAbsent` and `replace` are compare-and-set operations executed under the
same locking and atomic-replacement rules as the player identity. A first-use
race has one winner. A stale replacement returns `false` and leaves the
last-known-good bytes unchanged.

A changed server identity is not accepted or persisted by this adapter. The
higher-level `ServerTrustService` returns `CHANGED_IDENTITY`; replacement requires
an explicit operation with the previously observed record and a reason.

## Permissions and platform behavior

On POSIX filesystems the adapter applies owner-only permissions:

- data directory: `0700`;
- identity, trust, lock, and temporary files: `0600`.

On Windows the implementation relies on the directory's NTFS ACLs and does not
claim encryption at rest. The Direct Connect Alpha must document where its data
directory is created and should warn users not to place it in a shared or
world-readable location.

A future operating-system keychain adapter may replace only the
`PlayerIdentityStore` implementation. It must preserve the same identity and
failure semantics; it must not silently generate a new identity when a keychain
read fails.

## Backup and recovery

For the alpha file adapter, a backup means copying `player-identity.sfki` while
the client is stopped and storing that copy somewhere private. The file contains
an unencrypted private key:

- never send it to another person;
- never attach it to an issue or pull request;
- never commit it to Git;
- never include it in logs, screenshots, crash reports, or release archives.

Losing every copy of `player-identity.sfki` permanently loses continuity with
that `playerId`. A replacement key is a new player identity and will not satisfy
existing local nickname bindings or global registry claims.

The alpha does not yet provide encrypted export/import, passphrase recovery, TPM
storage, or cloud synchronization. Those features must be designed separately
and may not weaken the fail-closed load behavior.

`server-trust.sftr` contains only public identifiers and audit reasons, but its
loss removes local pinning history. Restore it from backup or repeat a deliberate
first-use verification. Never accept a changed fingerprint merely because the
file was deleted unexpectedly.

## Explicit reset

Reset is intentionally outside normal startup:

- deleting `player-identity.sfki` creates a new cryptographic identity at the
  next successful first use;
- deleting `server-trust.sftr` removes every local server pin;
- deleting one trust record or replacing a changed server key must be exposed as
  an explicit, audited UI/administrative action in later milestone work.

Startup failures return bounded storage error codes and preserve the files for
inspection. The client must not implement "delete and retry" as an automatic
recovery path.

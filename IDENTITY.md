# Player identity and handle registry

This document records the intended security model and the implementation status
of its foundations. Dedicated Ed25519 player identity, channel-bound Identity
Proof V2, strict identity exchange payloads, and offline signed-snapshot
verification/activation are implemented. Claim authoring, snapshot download and
persistence adapters, confusable policy, and server authorization modes remain
tracked by issues #30 and #31.

## Goals

- A player can choose a nickname and play on a LAN or private server without an
  Internet connection or central account.
- A returning player can prove they are the same cryptographic identity.
- A copied nickname is insufficient to impersonate the original player.
- Servers can choose local-only, global-only, or hybrid name policy.
- The initial global registry can be maintained through GitHub pull requests,
  while the game protocol and servers remain independent of GitHub.
- No service stores passwords, e-mail addresses, IP addresses, or private keys.

## Non-goals

- The design does not recover an identity when the private key and all backups
  are lost.
- It does not make a public global handle private: the handle, public key, and
  claim history are intentionally public.
- Client authentication does not by itself encrypt traffic or authenticate the
  server. The transport security design is a separate concern.
- A GitHub account is not an in-game identity and is not trusted during login.

## Application-specific Ed25519 identity

On first profile creation, the client generates a dedicated Ed25519 key pair
using the Java 21 cryptography APIs. It must not discover, import, request, or
reuse SSH, PGP, cryptocurrency, browser, operating-system login, or other
unrelated private keys.

The public key may be exported and shared. The private key remains on the client
and is used only to sign Sunderfront identity messages and registry operations.

A stable identifier is derived from the canonical encoded public key:

```text
playerId = "sf1_" + lowercase-base32(sha256(subjectPublicKeyInfo))
```

The encoding and vectors are frozen by protocol tests. A shorter grouped
fingerprint is displayed for manual verification, but it is never used as the
authoritative identifier.

## Local key storage and backup

The storage adapter should prefer an operating-system credential/key facility
when a supported implementation is available. A portable file fallback may be
used with restrictive permissions and clear warnings. The renderer-independent
modules only see an interface and never know the filesystem or keychain details.

The client must offer an explicit encrypted backup/export flow and an import
flow. Backups are never automatic uploads. Losing every copy of the private key
means losing that cryptographic identity; UI and documentation must state this
before a user registers a global handle.

Logs may contain only a shortened public fingerprint where operationally useful.
They must not contain private key bytes, seeds, encrypted backup contents,
signature transcripts containing secrets, or recovery passphrases.

## Challenge-response V2

A server proves freshness by generating a cryptographically random 32-byte nonce
for a single connection attempt. The client signs a canonical, length-prefixed
binary transcript. Concatenating ambiguous strings is forbidden.

The implemented V2 transcript includes:

1. the `SUNDERFRONT-CLIENT-AUTH-V2` domain separator;
2. protocol version;
3. pinned server identity;
4. logical session UUID;
5. the server nonce;
6. the exact 32-byte secure-channel binding;
7. canonical handle;
8. `playerId`;
9. canonical encoded public key.

The server verifies that:

- the declared `playerId` matches the supplied public key;
- the signature is valid for the exact transcript;
- the challenge belongs to this session, server and secure channel;
- the nonce is unused, unexpired, and removed atomically after a terminal result;
- field sizes, versions, and encodings satisfy strict limits;
- the selected authorization policy permits the handle/key binding.

A nonce cannot be reused after success or failure. Challenges have a short
expiry and bounded memory usage. The reliable identity exchange has bounded
step, overall and close deadlines and forbids identity-state re-entry after
success. Authorization remains separate from cryptographic proof.

## Canonical handle and display name

Identity uses two separate fields:

- `handle`: stable, lowercase ASCII identifier matching
  `[a-z0-9_]{3,24}`;
- `displayName`: optional localized/presentational Unicode text subject to length,
  normalization, control-character, bidi, and confusable-character policy.

Authorization and uniqueness use the canonical handle, never the display name.
This avoids silent collisions caused by case folding, Unicode normalization, or
visually similar characters. The UI may show both, for example:
`Grzegorz [grzegorz2047]`.

Reserved administrative words, product names, team-system names, and names that
violate the confusable policy are rejected by the registry. Local servers may be
stricter but must not reinterpret a globally registered handle.

## Server authorization modes

These modes are the intended authorization layer above a successfully verified
`AuthenticatedReliableSession`; they are not implemented by the snapshot
verifier itself.

### `LOCAL_TOFU`

Trust on first use is intended for LAN and private communities:

1. the first valid signed login for an unused handle atomically stores
   `handle -> playerId`;
2. later logins must prove the same player key;
3. a different key receives a name-conflict error;
4. only an explicit audited administrator action can unbind or rebind the name.

This prevents casual impersonation after first use but does not protect a name
before its first successful binding. Administrators may pre-reserve bindings.

### `GLOBAL_ONLY`

Only active claims in a verified global registry snapshot are accepted. The
handle, `playerId`, public key, claim status, snapshot version, root signature,
and freshness policy must all validate.

If the registry source is unavailable, the server uses its last accepted signed
snapshot subject to configured age/version policy. It never silently falls back
to an open or local-first mode.

### `HYBRID`

Globally registered handles remain reserved and require the registered key.
Unregistered players may join using local TOFU bindings and are visibly marked
as local/unverified. A registry outage cannot make a known global handle
available to another key.

When a locally bound handle later becomes global, the server follows an explicit
conflict policy. The safe default is to reserve the global claim and require an
administrator-reviewed local rename rather than silently transferring identity.

## Global registry claims

The initial authoring workflow uses a separate public GitHub repository. A
claim file is proposed by pull request, but the PR author is not the authority.
The claim must contain and be signed by the in-game private key corresponding to
its public key and `playerId`.

A claim contains at least:

- schema version;
- canonical handle;
- optional display name;
- `playerId`;
- canonical public key encoding;
- status or operation type;
- creation/sequence metadata needed by the schema;
- owner signature over the canonical claim without the signature field.

JSON claims use a documented canonicalization algorithm before signing. CI
validates schema, canonical form, signature, `playerId`, file path, uniqueness,
case collisions, confusable skeleton, reserved words, and operation rules.
Merging still requires repository review and branch protection. This authoring
workflow and claim-operation schema remain future work in #30.

## Signed registry snapshots

Game servers do not scan pull requests or individual claim files during login.
The implemented `identity-registry` module verifies and atomically activates a
resolved snapshot entirely offline. It has no GitHub, HTTP, filesystem, SQLite,
UI or server-runtime dependency.

Snapshot v1 uses exact RFC 8785/JCS canonical UTF-8 JSON containing:

- schema version `1`;
- non-negative monotonic sequence;
- canonical UTC `generatedAt`;
- a registry-root ID derived from canonical Ed25519 SPKI;
- entries strictly sorted by canonical handle;
- for each entry: handle, `playerId`, canonical public key and `ACTIVE` or
  `REVOKED` status.

The detached artifact contains the exact canonical JSON bytes, a SHA-256 digest,
and an Ed25519 registry-root signature over the exact JSON bytes. The verifier:

- requires byte-for-byte JCS canonical form;
- performs bounded duplicate-detecting streaming schema parsing;
- derives each `playerId` from its public key;
- resolves the root only from an explicitly configured local trust bundle;
- validates digest, signature, sequence, age, future skew, byte and entry limits;
- returns an immutable verified snapshot.

Atomic activation accepts only higher sequences, treats the same sequence and
digest as idempotent, rejects rollback, and rejects same-sequence equivocation.
Every provider, parsing, digest, signature, policy or activation failure leaves
the last valid snapshot active.

The source is represented by a provider interface. Future providers can read a
specific GitHub Release asset, local file, static mirror or HTTPS service without
changing trust semantics. Network/file providers, disk cache and persistence are
not implemented by this slice. Reproducible configurations should pin a specific
version or minimum monotonic sequence rather than accepting a mutable `latest`
response.

## Rotation and recovery

A normal player-key rotation is an operation signed by both the currently active
key and the replacement key. A lost-key recovery cannot prove continuity through
the old key, so it requires a separately documented, conservative registry
review procedure and should be visibly recorded as a recovery, not represented
as an ordinary cryptographic rotation.

Registry-root rotation uses a trust bundle that can accept old and new roots for
a bounded transition window. The current verifier supports multiple explicitly
configured roots but does not define or automatically trust a transition
artifact. The old trusted root should sign transition metadata whenever
possible. Servers support explicit emergency trust-bundle updates but never
download and trust an unknown replacement root automatically.

## Privacy and abuse considerations

A global claim intentionally makes the handle and public key public. A public PR
may also make it easy to infer which GitHub account submitted the claim, even
though that account is not used by the game protocol. The registration UI must
explain this before generating claim material.

Server logs should use `playerId` and event codes for enforcement while limiting
IP retention. Ban rules should bind to the stable player identity, with IP-based
controls treated only as optional abuse mitigation.

## Required failure behavior

- A bad signature, mismatched public key, reused nonce, expired challenge,
  malformed field, unsupported version, revoked claim, rollback snapshot, or
  conflicting binding is rejected before the player enters the lobby.
- Registry/network failure never rewrites or clears valid local bindings.
- An invalid new snapshot never replaces the last valid snapshot.
- `GLOBAL_ONLY` never degrades automatically to `HYBRID` or `LOCAL_TOFU`.
- Error responses are semantic and bounded; they do not disclose private data or
  whether unrelated player identities exist beyond what is necessary to explain
  a handle conflict.

## Standards references

- Ed25519 definition and test vectors: RFC 8032,
  <https://www.rfc-editor.org/rfc/rfc8032>.
- Canonical JSON for signed claims: RFC 8785,
  <https://www.rfc-editor.org/rfc/rfc8785>.
- Java 21 standard cryptographic algorithm names, including Ed25519:
  <https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html>.
- Unicode normalization guidance: Unicode Standard Annex #15,
  <https://www.unicode.org/reports/tr15/>.
- Unicode identifier spoofing and confusable detection: Unicode Technical
  Standard #39, <https://www.unicode.org/reports/tr39/>.

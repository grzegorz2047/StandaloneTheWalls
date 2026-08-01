# ADR 0003: Application-specific Ed25519 player identity

- Status: Accepted
- Date: 2026-08-01
- Issues: #28, #29, #30, #31, #34

## Context

A Sunderfront server must distinguish a returning player from another client
that copied the same nickname. LAN and private servers must continue to work
without a central account, e-mail address, password, OAuth provider, or Internet
connection.

A nickname is not an identity. Reusing an existing SSH, PGP, cryptocurrency, or
operating-system login key would unnecessarily couple the game to unrelated,
high-value credentials and increase the impact of an implementation mistake.

A client signature alone also does not authenticate the server or encrypt the
transport. Those properties remain separate work in #34.

## Decision

Each local player profile owns a dedicated Ed25519 key pair generated through
the Java 21 cryptography APIs. The key is used only for Sunderfront identity
operations.

The renderer-independent protocol module defines an adapter boundary for key
storage. This ADR does not choose a filesystem format, operating-system
keychain, encrypted backup format, or recovery mechanism. Private key material
is never exposed by public accessors, protocol messages, `toString`, or logs.

### Public identity

The canonical public-key representation is its X.509
`SubjectPublicKeyInfo` encoding. Decoding must round-trip to identical bytes;
noncanonical or non-Ed25519 encodings are rejected. A loaded public/private pair
must also pass an internal sign-and-verify consistency check before it becomes an
active identity.

The authoritative public identifier is:

```text
playerId = "sf1_" + lowercase-base32-no-padding(
    sha256(subjectPublicKeyInfo)
)
```

A SHA-256 digest is 32 bytes and therefore produces 52 Base32 characters. The
fingerprint shown for manual comparison is the first ten digest bytes rendered
as five groups of four lowercase hexadecimal characters. The fingerprint is a
convenience only and never replaces the full `playerId` or public key.

The repository includes a public-key-only compatibility vector:

```text
publicKey = MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=
playerId  = sf1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua
fingerprint = 6935-ae6e-c114-b657-5fa4
```

No private key corresponding to this vector is stored or needed.

### Canonical handle

Authentication uses a lowercase ASCII handle matching
`[a-z0-9_]{3,24}`. A Unicode display name is a separate presentation field and
does not participate in uniqueness or authorization.

### Challenge and transcript v1

The server issues a cryptographically random 32-byte nonce bound to one session,
one configured `serverId`, and a short expiry. The client signs one canonical,
length-prefixed binary transcript in this exact order:

1. `SUNDERFRONT-CLIENT-AUTH-V1` domain separator;
2. protocol major and minor as unsigned 16-bit values;
3. ASCII `serverId`;
4. session UUID as two 64-bit values;
5. 32-byte nonce;
6. canonical handle;
7. canonical `playerId`;
8. canonical public-key bytes.

Variable fields use a 32-bit byte length followed by the bytes. Ambiguous string
concatenation and Java native serialization are forbidden.

The challenge expiry is maintained by the server ledger rather than trusted
from client data. A challenge is removed atomically before signature
verification. Success, bad signature, malformed key, or any other terminal
attempt consumes it; a replay receives `MISSING_CHALLENGE`. A challenge at or
after its deadline receives `EXPIRED_CHALLENGE`.

The ledger is thread-safe, has a configured maximum number of outstanding
challenges, removes expired entries, and permits at most a five-minute lifetime.
Network-source rate limiting remains an adapter responsibility because the
protocol core does not know IP addresses or transport connections.

### Verification

The server verifies, in order:

1. exact supported protocol version;
2. canonical Ed25519 public-key decoding;
3. derived `playerId` equals the claimed `playerId`;
4. signature covers the exact transcript for the consumed challenge.

Expected failures return bounded semantic statuses and do not include raw key,
nonce, transcript, or signature data.

Message IDs are reserved for identity challenge, proof, and result on the
reliable channel. Their payload serialization is deliberately deferred until a
schema format is chosen; reserving IDs does not authorize ad-hoc object
serialization.

## Consequences

- A copied nickname is insufficient to impersonate a previously bound player.
- The same public key gives the same `playerId` on Windows and Linux.
- Losing the private key and every backup loses cryptographic continuity; no
  central password reset is implied.
- A public global claim intentionally exposes the handle and public key.
- Local/global name authorization remains #30/#31; this ADR proves possession
  but does not decide whether a handle is allowed on a server.
- The current transcript binds a proof to a configured server identifier but
  does not prove that the client connected to the genuine server. TLS/QUIC,
  server-key pinning, and channel binding remain mandatory work in #34.

## Rejected alternatives

- **Nickname only:** trivial impersonation.
- **Password database:** requires secret handling, reset policy, and usually a
  central service.
- **GitHub account as login:** breaks offline/LAN use and couples gameplay to a
  third party.
- **Reuse an SSH/PGP/cryptocurrency key:** exposes unrelated high-value
  credentials to unnecessary code paths.
- **MAC address or hardware fingerprint:** unstable, privacy-invasive, and easy
  to spoof.
- **Custom cryptographic algorithm:** unnecessary and unsafe compared with a
  standard Ed25519 implementation.

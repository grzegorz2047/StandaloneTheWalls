# ADR 0004: Server identity, pinning, and secure-channel binding

- Status: Accepted
- Date: 2026-08-01
- Issues: #34, #43
- Supersedes: the free-form `serverId` and V1 transcript layout in ADR 0003

## Context

The player Ed25519 challenge-response proves that the client owns its private
key. By itself it does not prove which server received the proof, authenticate
the transport endpoint, encrypt traffic, or prevent a relay from forwarding a
valid proof into a different secure session.

The protocol therefore needs a stable server identity and an opaque binding to
the exact authenticated transport channel before a TLS, QUIC, or DTLS adapter is
selected. This layer must not invent an encryption protocol or pretend that a
certificate hash, address, TLS session ID, or nonce is equivalent to a standard
channel exporter.

## Decision

### Dedicated server identity

A Sunderfront server has a dedicated long-term Ed25519 identity key. It is
separate from player keys and may also be separate from a replaceable X.509/TLS
certificate key. Its canonical public representation is X.509
`SubjectPublicKeyInfo`.

```text
serverId = "sfs1_" + lowercase-base32-no-padding(
    sha256(subjectPublicKeyInfo)
)
```

The distinct `sfs1_` prefix prevents server and player identifiers from being
silently interchanged. A manual fingerprint uses the first ten SHA-256 bytes as
five groups of four lowercase hexadecimal characters, but never replaces the
full `serverId`.

Public compatibility vector:

```text
publicKey = MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=
serverId  = sfs1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua
fingerprint = 6935-ae6e-c114-b657-5fa4
```

Only canonical Ed25519 public-key encodings are accepted in this slice.

### Secure channel binding

`SecureChannelBinding` is exactly 32 opaque bytes supplied by the future secure
transport adapter. Core protocol code copies the bytes defensively and never
logs or formats their value.

For a TLS adapter, the preferred source is the RFC 9266 `tls-exporter` channel
binding or an equally standard exporter exposed by the selected maintained
provider. Java 21 JSSE does not expose a public TLS exporter API, so provider and
adapter selection remain in #34. Missing binding is fail-closed: an identity
challenge cannot be created without it.

This core type does not prove that the adapter generated the binding correctly.
It only makes omission, wrong length, logging, and cross-session proof reuse
observable and testable.

### Client authentication transcript V2

The V1 transcript from ADR 0003 is superseded before protocol release. The new
signature domain is `SUNDERFRONT-CLIENT-AUTH-V2`; changing the layout without a
new domain would create two incompatible meanings of V1.

The canonical length-prefixed transcript contains, in order:

1. V2 domain separator;
2. protocol major and minor;
3. canonical `ServerId`;
4. session UUID;
5. 32-byte server nonce;
6. 32-byte secure-channel binding;
7. canonical player handle;
8. `playerId`;
9. canonical player public-key bytes.

Changing the server identity, session, nonce, channel binding, handle, player ID,
or key invalidates the signature. A proof from one secure channel cannot
authenticate another channel even when every other field is unchanged.

### TOFU and expected pins

`ServerReference` is a bounded canonical reference chosen by an adapter, such as
lowercase host and port. It is a lookup key, not cryptographic identity.

Trust inspection is read-only and produces one of four outcomes:

- `TRUSTED`;
- `FIRST_USE_REQUIRES_CONFIRMATION`;
- `CHANGED_IDENTITY`;
- `EXPECTED_PIN_MISMATCH`.

First use never writes automatically. A caller must explicitly confirm TOFU with
an auditable reason. A changed key never overwrites the current record. Explicit
replacement uses compare-and-replace against the previously inspected record so
a concurrent change fails closed.

An expected pin from a signed registry or public configuration takes precedence
over TOFU. It is compared directly and cannot be persisted through the TOFU
operation. This prevents a local first-use record from replacing a configured
public pin.

The trust-store interface is independent of filesystem, SQLite, operating-system
keychains, and UI. Persistence, timestamps, operator identity, and additional
audit metadata belong to adapters.

## Consequences

- The same canonical Ed25519 key gives the same server ID on Windows and Linux.
- Client proofs are cryptographically tied to one server identity and one
  transport exporter value.
- Server trust changes require an explicit operation and reason.
- The trust model can operate offline after first confirmation.
- This PR does not authenticate a real socket, encrypt bytes, issue a
  certificate, or prove possession of a server private key. Those are mandatory
  responsibilities of #34.

## Rejected alternatives

- **Free-form server name as identity:** not cryptographic and easy to copy.
- **IP address or DNS name as the pin:** routing labels can legitimately change
  and do not prove key possession.
- **Certificate hash as universal channel binding:** binds only a certificate,
  not the exact TLS session, and breaks ordinary certificate rotation.
- **TLS session ID, nonce, or socket address as exporter substitute:** not a
  standard channel binding and may be replayable or attacker-controlled.
- **Automatic TOFU write:** hides the security decision at the moment where a
  first-connection attacker has the greatest advantage.
- **Automatic replacement after a mismatch:** converts detection into silent
  compromise.
- **Custom encryption or MAC:** unnecessary and unsafe compared with a maintained
  standard secure-transport implementation.

## Standards reference

- TLS exporters: RFC 5705.
- TLS exporter channel binding: RFC 9266.
- Ed25519: RFC 8032.

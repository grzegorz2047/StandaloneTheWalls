# ADR 0025: Remote registry artifacts use explicit bounded HTTPS resources

- Status: accepted
- Date: 2026-08-02

## Context

The signed registry core already separates an untrusted `RegistrySnapshotProvider`
from cryptographic verification and monotonic activation. A local `SFRB` provider
supports offline startup, but migration between GitHub Releases and another mirror
also requires a remote source whose transport failure cannot weaken trust or erase
the last-known-good snapshot.

Using a mutable release-discovery API, a `latest` URL, implicit content decoding,
or an unbounded byte-array body handler would add authority and resource-consumption
risks to a component that should only fetch bytes.

## Decision

A separate renderer-independent module, `identity-registry-http`, implements
`RegistrySnapshotProvider` using only the JDK HTTP client and the existing registry
API.

One configuration identifies three distinct immutable HTTPS resources:

- canonical snapshot JSON;
- detached SHA-256 digest;
- detached Ed25519 signature.

Every initial and final response URI must be absolute HTTPS with a host and without
userinfo or a fragment. This permits ordinary HTTPS redirects such as release asset
hosting while rejecting an HTTPS-to-HTTP downgrade.

Digest and signature resources use exact lowercase hexadecimal lines terminated by
a single LF. The JSON body is bounded by an explicit configured limit that cannot
exceed the core absolute snapshot limit.

The transport sends a synchronous GET with bounded connect and request timeouts,
`Accept-Encoding: identity`, no authorization header, and JDK `NORMAL` redirects.
Only status 200 is accepted. Non-identity content encoding is rejected.

`Content-Length`, when present, is a strict single positive decimal value. It must
be within the applicable limit and equal the bytes actually read. Independently of
that header, the body is read through `maximumBytes + 1` and rejected if the extra
byte exists. The response stream is closed on every path.

The provider returns only `RegistrySnapshotArtifact`. It does not verify trust,
activate state, persist cache, select releases, retry, back off, or schedule work.
Provider failures use one stable public message without URI, response content,
status text, or transport exception text. Existing higher layers retain the active
last-known-good snapshot.

## Consequences

- switching from GitHub-hosted immutable assets to another HTTPS mirror does not
  change snapshot trust semantics;
- a missing, compressed, oversized, redirected-to-HTTP, malformed, or inconsistent
  resource remains a provider failure or later verifier rejection;
- false or missing `Content-Length` cannot bypass the memory bound;
- remote response bytes never become trusted merely because TLS succeeded;
- version discovery, refresh cadence, retry/backoff, atomic local `SFRB` caching,
  process configuration wiring, and release publishing remain separate decisions;
- private registry signing keys and GitHub credentials are not part of this module.

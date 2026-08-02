# HTTPS identity-registry snapshot provider

`identity-registry-http` is a JDK-only adapter that loads one detached signed
registry artifact from three explicit HTTPS resources:

1. canonical snapshot JSON;
2. lowercase-hex SHA-256 digest;
3. lowercase-hex Ed25519 signature.

The adapter implements `RegistrySnapshotProvider`. It does not decide trust,
parse or activate a snapshot, modify the active registry store, persist a local
cache, choose a release, retry, or schedule refreshes. Every returned artifact
must still pass the existing `identity-registry` cryptographic and semantic
verification before it can become active.

## Resource contract

Construct `RegistrySnapshotHttpsConfiguration` with three different absolute
HTTPS URIs. Each URI must have a host and must not contain user information or a
fragment. Use immutable versioned URLs. A mutable `latest` URL is outside this
adapter's contract because three independent resources could otherwise change
between requests.

The detached text formats are exact:

```text
<64 lowercase hexadecimal SHA-256 characters>\n
<128 lowercase hexadecimal Ed25519 signature characters>\n
```

Uppercase hex, CRLF, comments, filenames, spaces, missing LF, additional lines,
or trailing bytes are rejected. Canonical JSON must be non-empty and no larger
than the configured maximum, which itself cannot exceed the core absolute limit.

## HTTP policy

The production transport uses `java.net.http.HttpClient` with explicit bounded
connect and request timeouts. It sends synchronous `GET` requests with
`Accept-Encoding: identity`, no authorization header, and the JDK `NORMAL`
redirect policy. The final response URI is validated again and must remain HTTPS.

Only status `200` is accepted. A non-identity content encoding is rejected.
`Content-Length`, when present, must be a single positive decimal value within the
resource limit and must match the bytes read. The body stream is independently
read only through `maximumBytes + 1`, so a missing or false header cannot bypass
the hard limit. Every body stream is closed on success and on every rejection.

The public `RegistrySnapshotProviderException` message does not contain the URI,
response body, status text, or transport exception text. Callers should retain
their last-known-good verified snapshot when this source is unavailable.

## Deliberate omissions

This module does not contain GitHub API logic, tokens, credentials, automatic
release discovery, retry/backoff, jitter, local `SFRB` persistence, process
configuration wiring, or a refresh scheduler. Those concerns require separate
bounded adapters around this provider.

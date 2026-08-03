# ADR 0035: Direct Connect client ownership and explicit TOFU

- Status: Accepted
- Date: 2026-08-03
- Decision owners: Sunderfront maintainers
- Related: #34, #76, #81, #86, #87, #88, #89

## Context

The repository already provides low-level TLS 1.3, server pinning, session
bootstrap, Identity Proof V2, persistent client identity, a persistent server
trust store, policy admission, and a minimal reliable lobby. Those components do
not by themselves define one production client operation or tell presentation
code when a connection is trustworthy and application-ready.

The Direct Connect Alpha needs a bounded vertical slice. It must never perform
persistence, DNS, socket I/O, TLS, signing, or blocking protocol work on the
jMonkeyEngine renderer thread. It must also avoid silently trusting the first
certificate, carrying a rejected TLS channel across user confirmation, or
reporting success immediately after a TCP or TLS handshake.

## Decision

### Endpoint contract

Direct Connect accepts one explicit canonical `host:port` value:

- strict dotted-decimal IPv4;
- lowercase ASCII DNS labels;
- bracketed IPv6;
- a decimal port from 1 through 65535.

It rejects implicit ports, URLs, user information, paths, queries, fragments,
whitespace, zone identifiers, unbracketed IPv6, non-ASCII DNS input, and
non-canonical numeric forms. Parsing does not perform DNS resolution.

The canonical authority is also the `ServerReference` trust-store key. The first
release deliberately does not use redirects, SRV records, aliases, or automatic
endpoint migration because those features need explicit trust-key semantics.

### Explicit two-connection TOFU

An unknown server is not accepted or persisted during the first TLS attempt.
`PinnedServerTrustManager` rejects the certificate with bounded public metadata:
server reference, `ServerId`, and `ServerFingerprint`. The client then closes the
socket and returns `ConfirmationRequired` with an opaque token.

The token is:

- generated from 256 random bits;
- bound to one service instance, canonical endpoint, exact `ServerId`, exact
  fingerprint, canonical player handle, and expiry;
- single use;
- removed before a confirmation retry starts;
- invalidated by a new unconfirmed request or service shutdown;
- never rendered with its raw bytes.

After confirmation the client opens a new TCP and TLS connection with the exact
confirmed `ServerId` supplied as an expected pin. A server replacement between
the two attempts therefore fails before bootstrap. Only after that exact-pin TLS
handshake succeeds may the client persist the TOFU record. A concurrent
`saveIfAbsent` winner is accepted only when a fresh inspection proves that the
same server identity is now trusted. Changed identity is never overwritten
implicitly.

Persisting trust after exact-pin TLS but before Identity Proof is intentional:
the user confirms the server transport identity, not the later player policy
outcome. A ban, handle conflict, full lobby, or other admission rejection does
not make the confirmed TLS identity untrusted.

### Operation ownership

One `DirectConnectService` owns at most:

- one in-progress attempt; and
- one connected lobby session.

A second overlapping attempt returns `ALREADY_CONNECTING`; it does not cancel or
replace the first attempt. A new attempt while a session remains owned returns
`ALREADY_CONNECTED`.

Each attempt owns exactly one current resource closure. Ownership advances in
this order:

1. DNS task;
2. TCP socket;
3. TLS connection;
4. bootstrapped reliable session;
5. authenticated reliable session;
6. connected lobby session.

Replacing a stage replaces its cleanup owner. Cancellation, failure, or service
shutdown closes the current owner. Once ownership transfers to
`ConnectedLobbySession`, operation cleanup must not close it again. Public
attempt results are immutable completion stages and callers cannot complete them.

All orchestration and DNS work runs on service-owned Java 21 virtual threads.
The service does not call network or persistent storage from the caller thread.

### Definition of connected

A successful TLS handshake is not a connected result. The service returns
`Connected` only after all of the following succeed in order:

1. TLS 1.3 with trusted or exactly expected server identity;
2. SFSB session bootstrap;
3. Identity Proof V2;
4. accepted `SESSION_ADMISSION_RESULT`;
5. canonical `LOBBY_JOINED` matching the persistent player identity and requested
   handle;
6. canonical initial `LOBBY_SNAPSHOT` whose revision is not older than the join
   revision and which contains the exact authenticated self member.

Every stage has a bounded public failure code. Presentation code receives no raw
exception text, certificate bytes, key material, proof payload, registry
artifact, stack trace, or transport object before successful ownership transfer.

### Connected lobby receiver

`ConnectedLobbySession` exposes immutable current snapshot state and owns one
receiver virtual thread. The receiver accepts only `LOBBY_SNAPSHOT` messages with
a strictly newer revision and an exact self member. It closes fail-closed on:

- EOF or receive failure;
- malformed snapshot;
- stale or repeated revision;
- unexpected message type;
- missing self member;
- changed self player ID or handle.

The receiver starts only after the service atomically stores the exact session
instance. Its close callback clears that same instance with compare-and-set, so
a late callback cannot erase a later connection. Closing is idempotent and
closes the authenticated reliable channel, which releases the server's retained
admission-capacity slot.

## Consequences

### Positive

- First use always requires a visible, explicit trust decision.
- Confirmation cannot authorize a server swapped between attempts.
- The trust store is not silently replaced after a changed key.
- Renderer code receives non-blocking attempts and immutable outcomes.
- The UI can distinguish transport, proof, admission, lobby, cancellation, and
  trust failures without parsing exception strings.
- “Connected” means the server has transferred the player into application-owned
  lobby state.
- Session capacity has one deterministic client owner and is released on close.

### Negative

- First use requires two TCP/TLS handshakes.
- DNS aliases are distinct trust-store references.
- The service supports one operation and one lobby session at a time.
- There is no automatic reconnect, resume token, heartbeat, relay, or endpoint
  discovery.
- Confirmed server trust remains stored even when later player admission is
  rejected.
- The first release has no OS keychain or encrypted-at-rest trust database.

## Alternatives rejected

### Accept and persist the first certificate in one handshake

Rejected because presentation code would receive an already trusted live channel
before the user could verify the identity.

### Pause the rejected TLS connection while waiting for confirmation

Rejected because a channel established under an unresolved trust decision should
not survive the decision boundary. Reconnecting with an exact pin is simpler to
audit and detects server replacement.

### Report success after TLS or Identity Proof

Rejected because the server may still reject policy admission or fail to
transfer the session into lobby ownership.

### Automatically cancel an existing attempt

Rejected because UI retries or duplicate input could close an operation that is
already completing successfully. Overlap is a stable explicit result.

### Run the connection from a jMonkeyEngine state callback

Rejected because DNS, socket, TLS, cryptography, and persistence can block and
would make rendering and input nondeterministic.

## Follow-up

- #90 maps immutable attempt/session state to localized jMonkeyEngine UI without
  network work on the renderer thread.
- #91 executes the exact path from unpacked release artifacts and publishes
  `v0.1.0-alpha.1` with checksums and first-run instructions.
- Reconnect, discovery, relay, public PKI, realtime transport, ready state, teams,
  and gameplay remain separate versioned work.

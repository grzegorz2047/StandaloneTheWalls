# ADR 0031: Production reliable TLS is explicit, fail-closed, and process-owned

- Status: accepted
- Date: 2026-08-02
- Issue: #81
- Depends on: ADR 0005, ADR 0008, ADR 0009, ADR 0010, ADR 0028

## Context

The repository already contains a strict TLS 1.3 listener, session bootstrap,
channel-bound Identity Proof V2, server-side handle authorization, and a bounded
pre-lobby handoff. Before this decision those pieces were composed only in tests.
`ServerLauncher` could run the fixed-tick process and local identity runtime but
could not provision server credentials or open the authenticated endpoint.

Silently generating a server key, accepting a plaintext fallback, or opening a
port before credential validation would weaken the established trust model. A
configuration validation command must also be safe to run in deployment tooling
without binding a socket or performing registry HTTP I/O.

## Decision

Reliable TLS is enabled only by the explicit `--tls-config <path>` launcher
option. The option requires `--identity-config`; it cannot be combined with the
one-shot identity administration command. Omitting it leaves the process network
disabled rather than opening an unauthenticated endpoint.

The TLS configuration is a separate, strict UTF-8 `key=value` file with schema
version `1`. It rejects unknown and duplicate keys, whitespace ambiguities,
controls, malformed numeric values, symbolic links, non-regular files, and files
above the configured hard bounds. Relative credential paths resolve from the TLS
configuration directory.

The operator supplies exactly one canonical Ed25519 PKCS#8 DER private key and
one canonical X.509 DER leaf certificate. The loader:

1. reads both through bounded regular-file checks;
2. decodes only Ed25519 PKCS#8 and X.509 DER;
3. rejects trailing or non-canonical certificate/key bytes;
4. signs a fixed domain-separated probe with the private key and verifies it with
   the certificate public key;
5. constructs `Tls13ServerCredentials`, which derives the public `serverId` and
   validates certificate validity.

No private key, certificate bytes, paths, proof payloads, signatures, remote
addresses, or exception text enter normal launcher diagnostics.

The configured bind address must be a numeric IPv4 or IPv6 literal. DNS is not
performed during configuration validation. The reliable port remains the single
`server.reliable-port` value. Active TLS capacity cannot exceed
`server.maximum-players`; challenge capacity cannot exceed active TLS capacity.
All handshake, listener, challenge, result-send, and shutdown values remain under
existing hard limits.

A `ReliableTlsAdmissionRuntime` owns exactly one:

- `ChallengeLedger` and `IdentityChallengeService`;
- `TlsIdentityAdmissionGateway` using the process-owned `LocalIdentityRuntime`;
- `AuthorizedPlayerSessionQueue` created by that gateway;
- `Tls13ServerListener` using the validated server credentials.

The listener can deliver accepted connections only to the mandatory gateway.
There is no constructor or configuration path in the process composition that
hands authenticated TLS leases directly to gameplay or bypasses proof and policy
admission.

Startup order is:

1. parse and validate server, identity, and TLS configuration and credentials;
2. open the one local identity runtime;
3. construct and bind the TLS listener with its mandatory gateway;
4. start optional registry refresh;
5. install the shutdown hook;
6. start the listener accept loop;
7. start the fixed-tick runtime.

A failure after any owned resource is created closes previously created resources.
Normal and hook shutdown close listener/gateway first, registry refresh second,
and the simulation runtime last. Listener close rejects new accepts, closes
in-flight handshakes and active leases, and gateway close interrupts admission
work and closes queued sessions. Repeated close calls are deterministic.

A terminal accept-loop or listener-shutdown failure is a process-level failure and
stops the fixed-tick runtime. Individual malformed handshakes remain isolated to
their connection.

`--validate-config` performs full strict parsing, credential decoding, key-pair
matching, certificate validity, and limit validation. It does not construct the
identity runtime, create or migrate SQLite, bind the reliable port, start the
registry scheduler, or perform network I/O.

## Consequences

- Production reliable transport cannot be enabled without explicit identity
  policy and operator-provisioned Ed25519 credentials.
- A copied or mismatched certificate/private key pair fails before socket bind.
- The same process-owned identity state serves administration, registry refresh,
  and every network admission decision.
- There is no plaintext or unauthenticated compatibility fallback.
- Credential rotation is an explicit file/configuration deployment followed by a
  process restart; clients observe the derived `serverId` change through their
  existing pinning policy.
- Public PKI, ACME, reconnect, lobby consumption, realtime DTLS/UDP, and realtime
  session tokens remain separate work.

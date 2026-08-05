# ADR 0042: Fail closed when the pinned provider cannot offer DTLS 1.3

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Sunderfront maintainers
- Related: #34, #161, #163, #165
- Depends on: ADR 0040 and ADR 0041

## Context

ADR 0040 selected DTLS 1.3 with one-time external PSKs for the first realtime
transport, and ADR 0041 defined reliable ticket provisioning. The project pins
Bouncy Castle `bctls-jdk18on` 1.84 for low-level TLS.

A source review of that exact release found that `DTLSServerProtocol` still has
its DTLS 1.3 server-hello path commented out behind `TODO[dtls13]` and continues
through the legacy DTLS path. Building a production listener on that class would
therefore either fail or require DTLS 1.2. The latter would contradict the
accepted DTLS 1.3-only profile and create a silent security downgrade.

The project must not advertise or issue usable realtime credentials before a
maintained provider implements the selected protocol and passes the required
loopback, replay, cookie and shutdown tests.

## Decision

### Provider capability is explicit and reviewed

`RealtimeTransportCapability` records whether one exact provider/version is
approved for the production realtime profile. The current Bouncy Castle review
is pinned to version `1.84` and reports `DTLS_1_3_NOT_IMPLEMENTED`.

Any different provider version reports `PROVIDER_VERSION_REQUIRES_REVIEW` until a
maintainer reviews the implementation and adds reproducible DTLS 1.3 tests. A
dependency upgrade alone cannot enable realtime transport.

### Production provisioning fails closed

`RealtimeTicketProvisioner.createProduction(...)` validates its normal bounds but
does not allocate a ticket store when the transport capability is unavailable.
The admitted reliable request loop returns the existing stable
`TEMPORARILY_UNAVAILABLE` rejection. It does not issue an identity or PSK that no
approved listener can consume.

The direct provisioner constructor remains available for deterministic store,
codec and lifecycle tests and for a future explicitly composed, reviewed
transport. This preserves the one-time credential contract without claiming that
the current production process has a DTLS listener.

### No DTLS 1.2 fallback

The server does not enable DTLS 1.2, a custom record layer, a custom PSK binder or
a partial parser copied from a provider. The configured realtime port remains a
reserved process setting, not evidence that a UDP socket is open. Startup logs
report the capability reason.

## Consequences

### Positive

- The runtime cannot silently downgrade the accepted DTLS 1.3 security decision.
- Clients receive a stable bounded rejection instead of unusable secret material.
- Provider upgrades require an explicit source and test review.
- Existing one-time store and reliable provisioning contracts remain testable.

### Limitations

- There is no production UDP/DTLS listener while the capability is unavailable.
- Issue #165 remains open for a maintained DTLS 1.3 implementation or adapter.
- Realtime gameplay packets remain deferred.

## Validation requirements

Tests must prove:

- the pinned Bouncy Castle provider version is exactly the reviewed version;
- that version reports `DTLS_1_3_NOT_IMPLEMENTED` and remains unavailable;
- a different version cannot become available without an explicit review change;
- production provisioning allocates no usable store and rejects ticket requests;
- injected provisioners still exercise issue, redemption, replay, revocation and
  secret-destruction behavior;
- startup and public text contain capability status but no PSK, identity or cookie.

The complete repository quality gate remains required.

## Follow-up

- Evaluate a maintained Java DTLS 1.3 implementation or a later Bouncy Castle
  release by exact immutable version and source.
- Implement #165 only after real DTLS 1.3 external-PSK and stateless-cookie
  loopback tests pass without downgrade.
- Keep QUIC as the separately reviewed alternative already described by ADR 0040.

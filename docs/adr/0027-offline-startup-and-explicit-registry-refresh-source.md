# ADR 0027: Startup stays offline while registry refresh source is explicit

- Status: accepted
- Date: 2026-08-02

## Context

The server has two bounded registry providers:

- the local `SFRB` bundle used for restart recovery;
- explicit immutable HTTPS resources used to obtain a newer detached artifact.

Treating the selected remote source as a startup dependency would make a correct
local server unable to restart during a GitHub, mirror, DNS, or network outage.
Treating HTTPS fields as optional hints would create ambiguous behavior and could
silently ignore an operator mistake. A remote reload also must retain the
cache-before-activation ordering established by ADR 0026.

## Decision

Identity process configuration requires one explicit administrative refresh source:

```text
identity.registry.refresh-source=LOCAL_BUNDLE|HTTPS
```

Startup behavior is independent of that choice. `LocalIdentityRuntime` always
attempts to verify and activate the configured local `SFRB` bundle first and never
performs an HTTP request during construction. A missing or rejected local bundle
remains the existing typed startup result and is evaluated by the selected handle
authorization policy.

`LOCAL_BUNDLE` keeps both `verify-snapshot` and `reload-registry` pointed at the
local file. Any HTTPS-specific configuration key is rejected rather than ignored.

`HTTPS` requires three distinct explicit immutable resources:

```text
identity.registry.https.json-uri=<https URI>
identity.registry.https.digest-uri=<https URI>
identity.registry.https.signature-uri=<https URI>
```

Connect and request timeout seconds are optional bounded overrides. The existing
registry maximum JSON bytes is also the HTTPS JSON body limit. URI, timeout and
body validation is delegated to the bounded HTTPS provider from ADR 0025.

For an HTTPS source:

- `verify-snapshot` downloads and verifies the artifact but does not activate it or
  modify the local bundle;
- `reload-registry` uses the cache-before-activation workflow from ADR 0026, so the
  exact verified artifact is atomically persisted before the shared active store
  is updated;
- provider, verification, rollback, equivocation or cache failure preserves the
  prior active snapshot and prior local bundle.

One runtime owns one `AtomicRegistrySnapshotStore`, one local bundle and one
`RegistrySnapshotService`. Startup, administration and session admission therefore
cannot observe independent registry states.

Validate-only mode parses the source-specific configuration and trust roots but
does not open SQLite, construct the runtime, load the local bundle or execute HTTP.

## Consequences

- a valid local cache permits offline restart even when administrative refresh is
  configured for HTTPS;
- source configuration errors fail before runtime startup instead of being silently
  ignored;
- remote verification remains side-effect free;
- remote reload preserves durable and in-memory ordering;
- local mode retains its previous behavior through the compatibility constructors;
- immutable versioned resource selection remains an operator or publishing concern;
- retry, exponential backoff, jitter and automatic scheduling remain separate work;
- authenticated HTTP, GitHub API discovery, mutable `latest` URLs and custom TLS
  pinning are not introduced.

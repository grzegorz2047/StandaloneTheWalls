# Sunderfront dedicated server runtime

This module contains the first headless process lifecycle and fixed-step scheduler.
It does not yet open sockets, load a map, accept players, or claim production
capacity. This implementation is the bounded runtime slice tracked by issue #25;
network and gameplay integration remain separate work.

## Configuration

Copy `server.properties.example` and pass the resulting path explicitly:

```bash
./gradlew :server:run --args="--config /path/to/server.properties --validate-config"
```

To validate server and local identity configuration together without creating or
migrating SQLite, pass both files:

```bash
./gradlew :server:run --args="--config /path/to/server.properties --identity-config /path/to/identity.properties --validate-config"
```

A normal process start may enable local identity with the optional single-use
`--identity-config <path>` argument. The launcher loads trust roots and opens one
`LocalIdentityRuntime` before the simulation thread starts. Omitting the argument
preserves the existing identity-disabled process behavior.

Supported server properties:

- `server.name`
- `server.tick-rate` — 10 through 60, default 20
- `server.reliable-port` — default 27420
- `server.realtime-port` — default 27421 and must differ from the reliable port
- `server.maximum-players` — 1 through 40

Unknown properties, malformed numbers, invalid ports, unsafe tick rates, and
capacities above the current product target fail closed.

## Local identity process configuration

Issue #69 adds a separate strict configuration file for the inputs required by
`LocalIdentityRuntime`. Copy `identity.properties.example` and replace every path,
authorization choice, trust root, and refresh source intentionally. Issue #70
connects the validated result to `ServerLauncher` through `--identity-config`.

Required literal `key=value` properties are:

```text
identity.sqlite-path=<file>
identity.registry-bundle-path=<file>
identity.authorization-mode=LOCAL_TOFU|GLOBAL_ONLY|HYBRID
identity.trust-roots-path=<file>
identity.registry.refresh-source=LOCAL_BUNDLE|HTTPS
```

Optional bounded registry policy overrides are:

```text
identity.registry.minimum-sequence=<unsigned integer>
identity.registry.maximum-age-seconds=<unsigned integer>
identity.registry.maximum-future-skew-seconds=<unsigned integer>
identity.registry.maximum-json-bytes=<unsigned integer>
identity.registry.maximum-entries=<unsigned integer>
```

Omitted policy values use `RegistrySnapshotPolicy.DEFAULT` exactly. Relative paths
are resolved from the identity configuration file's directory, not the process
working directory. The SQLite, registry bundle, and trust-root paths must be three
different files.

The parser reads at most 64 KiB of strict UTF-8. It rejects symlinks, non-regular
files, malformed UTF-8, escapes, edge whitespace, controls, unknown properties,
duplicate keys, and malformed numeric values. Missing paths, authorization mode,
or refresh source are errors; there is no implicit process identity policy.

The trust-root file is separate, at most 16 KiB, and contains 1–64 non-empty lines.
Every line must be lowercase hexadecimal X.509 DER for an Ed25519 public key.
Whitespace, comments, uppercase hex, duplicate roots, private-key DER, and other
key algorithms are rejected. Error messages never include the raw key line.

`registry-trust-roots.hex.example` intentionally contains an invalid placeholder,
not a production or test trust root. Replace it with an explicitly provisioned
public registry root before loading the identity configuration. Private keys and
credentials do not belong in either configuration file.

### Registry refresh source

The refresh source controls only the administrative `identity verify-snapshot` and
`identity reload-registry` commands. Runtime startup always verifies the configured
local `SFRB` bundle and never performs an HTTP request. A valid local cache can
therefore restore the server while GitHub, a mirror, DNS, or the network is down.

`LOCAL_BUNDLE` keeps verification and reload pointed at the local file. HTTPS
properties are rejected in this mode rather than ignored.

`HTTPS` requires all three explicit immutable versioned resources:

```text
identity.registry.https.json-uri=https://registry.example/releases/v1/registry-v1.json
identity.registry.https.digest-uri=https://registry.example/releases/v1/registry-v1.sha256
identity.registry.https.signature-uri=https://registry.example/releases/v1/registry-v1.sig
```

Optional bounded transport overrides are:

```text
identity.registry.https.connect-timeout-seconds=<unsigned integer>
identity.registry.https.request-timeout-seconds=<unsigned integer>
```

The defaults are 10 and 30 seconds. Every URI must be absolute HTTPS with a host,
without user information or a fragment, and the three normalized URIs must differ.
Mutable `latest` discovery, GitHub API calls, credentials, and authenticated
requests are not supported. The configured registry maximum JSON bytes is also the
hard HTTPS JSON response limit.

With `HTTPS`, `verify-snapshot` downloads and verifies the three-resource artifact
without changing active state or the local cache. `reload-registry` verifies the
same artifact, atomically persists its exact bytes to the local `SFRB`, and only
then publishes it in the shared active store. Provider, signature, rollback,
equivocation, or cache-write failure preserves the prior active snapshot and prior
bundle.

Validate-only mode parses the source-specific properties and trust roots but does
not construct the runtime, create SQLite, load the local bundle, or execute HTTP.
Retry, backoff, jitter, and automatic refresh scheduling are not part of this
configuration.

In a normal start, invalid trust roots or SQLite open/schema failure return the
configuration exit code before the tick loop. Missing or rejected local bundle data
remains a typed startup state rather than a process failure or generated default
snapshot.

Launcher identity logs contain only enabled/disabled state, authorization mode,
registry startup result code, and registry availability state. They do not contain
identity paths, trust-root material, registry digests, signatures, provider
exception text, handles, player IDs, or addresses.

## Identity admission boundary

Issue #65 adds `SessionIdentityAdmissionService` as the mandatory semantic gate
between a successful cryptographic player handshake and future lobby admission.
It accepts only the already-derived stable `playerId`, canonical handle, selected
handle policy, and the explicit registry availability state.

The ordering is intentional and must not be reversed:

1. reject a banned `playerId`;
2. only then evaluate `LOCAL_TOFU`, `GLOBAL_ONLY`, or `HYBRID` handle policy;
3. admit the session to a lobby only when the returned decision is accepted.

A banned first-use attempt therefore cannot reserve a local handle. Rejected
results never carry a verification level. Accepted local identities are marked
`LOCAL_UNVERIFIED`; accepted global identities are marked `GLOBAL_VERIFIED`.
The gate does not own sockets, TLS, private keys, IP addresses, SQLite lifecycle,
or lobby membership.

## Local identity runtime composition

Issue #68 adds `LocalIdentityRuntime` as the single composition used by session
admission and identity administration. Its configuration contains one SQLite
path, one different local registry bundle path, one explicit handle authorization
mode, and one explicit administrative refresh source.

Opening the runtime constructs:

- handle binding and player-ban SQLite stores on the same database file;
- one atomic registry snapshot store shared by startup, administration, and
  admission;
- one local `SFRB` provider used exclusively for startup recovery;
- the selected local or HTTPS administration provider;
- one registry verifier and one local bundle cache;
- the ban-before-handle session admission gate;
- the typed identity administration command service.

The runtime attempts one local bundle reload during construction and retains the
typed result. A missing bundle remains `PROVIDER_FAILURE`; a rejected artifact
remains `SNAPSHOT_REJECTED`; neither creates a default snapshot. Constructing an
HTTPS-enabled runtime does not execute remote I/O.

`LOCAL_TOFU` can operate without registry data only when explicitly selected.
`GLOBAL_ONLY` and `HYBRID` keep their existing fail-closed decisions. Registry
availability is computed dynamically from the one shared store, clock, and policy.
A successful authorized remote reload is therefore immediately visible to the next
admission call and survives restart through the local bundle.

Bindings and player bans survive reopening via the shared SQLite file.
`ServerLauncher` owns one runtime for the process lifetime when `--identity-config`
is supplied. Sockets, TLS, automatic refresh scheduling, and lobby membership
remain outside this composition.

## Local identity administration commands

Issues #66 and #67 define a strict typed command boundary for future console,
RCON, HTTP, or GUI adapters. `IdentityAdministrationCommandParser` accepts an
already-split list of tokens; it does not evaluate a shell or tokenize raw text.

Supported shapes are:

```text
identity list handles
identity list bans
identity inspect handle <canonicalHandle>
identity inspect ban <playerId>
identity reserve <handle> <playerId> <reason>
identity unbind <handle> <expectedPlayerId> <reason>
identity rebind <handle> <expectedPlayerId> <replacementPlayerId> <reason>
identity ban-player-id <playerId> <reason>
identity unban-player-id <playerId> <reason>
identity verify-snapshot
identity reload-registry
```

The adapter supplies a quoted multi-word reason as one token. The executor checks
one of four independent capabilities before touching a service: view identity,
manage handle bindings, manage player bans, or manage the registry provider.
Permission denial happens before any mutation or provider I/O. Successful binding
and ban commands delegate to the existing atomic audited policy services.

`verify-snapshot` loads and cryptographically verifies the configured administrative
refresh source without changing the active snapshot or cache. `reload-registry`
verifies and applies only a monotonic candidate. With HTTPS it atomically writes the
exact verified artifact to the local bundle before publishing active state. An
identical artifact returns `UNCHANGED` without rewriting the bundle.

Provider, signature, rollback, equivocation, and cache failures preserve the last
known good active snapshot. Registry responses contain only sequence, generation
time, registry root ID, SHA-256 digest, entry count, and stable semantic result
codes. They never contain canonical JSON, signature bytes, provider exception text,
private keys, IP addresses, credentials, or sockets.

## One-shot local identity administration

Issue #71 exposes the typed commands to the local operator without starting the
simulation scheduler. Supply all launcher options first, then the terminal
`--identity-command` marker and exactly one command:

```bash
./gradlew :server:run --args="--identity-config /path/to/identity.properties --identity-command identity list handles"
```

A mutation with a multi-word audit reason must deliver that reason as one token.
Shell and Gradle argument quoting are caller responsibilities:

```bash
./gradlew :server:run --args="--identity-config /path/to/identity.properties --identity-command identity reserve player_one ${PLAYER_ID} \"Manual local review\""
```

Command mode requires `--identity-config` and cannot be combined with
`--validate-config` or `--run-for-ticks`. Every argument after
`--identity-command` is passed to the strict identity parser without further
launcher interpretation. The process opens one local runtime, executes one command,
prints deterministic UTF-8 lines to stdout, and exits without constructing the tick
loop or installing a shutdown hook.

The local adapter uses the administrator ID `local-cli` with all four local
capabilities. This grant relies on operating-system access to the process,
configuration, and SQLite database; it is not suitable for a remote transport.
Future RCON, HTTP, or GUI adapters must authenticate and authorize their own
principals.

The first output line is always `response=<stable-code>`. Additional lines contain
explicit fields rather than domain-object `toString()` output. Public player IDs,
handles, ban metadata, and local audit reasons may be printed. Paths, trust roots,
signatures, canonical registry JSON, provider exception text, credentials,
addresses, and sockets are never printed.

Exit codes are:

- `0` for reads and applied or idempotent successful operations;
- `2` for launcher, configuration, or typed-command parsing errors;
- `3` for permission denial, rejected domain mutations, provider failure, or
  snapshot rejection.

Because each invocation reopens the configured runtime, successful bindings and
player bans are visible to later one-shot commands through the shared SQLite file.
A successful HTTPS reload is also available to later offline starts through the
local registry bundle.

## Smoke mode

A bounded headless run is available for CI and packaging checks:

```bash
./gradlew :server:run --args="--run-for-ticks 20"
```

The same bounded run can open local identity before the scheduler:

```bash
./gradlew :server:run --args="--identity-config /path/to/identity.properties --run-for-ticks 20"
```

The value must be between 1 and 1,000,000. Smoke mode starts the same simulation
thread as normal operation and exits after the requested number of executed ticks.

## Scheduling rules

- The simulation uses monotonic nanosecond time, never wall-clock time.
- Network, file, compression, and administration work must remain outside the tick
  handler.
- Catch-up is bounded to five ticks per scheduler iteration. Additional timing debt
  is reported as skipped scheduling intervals rather than causing an unbounded
  spiral.
- The runtime owns exactly one non-daemon simulation thread and interrupts/joins it
  during shutdown.
- Tests inject a fake clock and sleeper; they do not wait on real tick intervals.

The handler is still a boundary only. Wiring the match lifecycle, map state,
transport queues, automatic registry refresh, and remote administration belongs to
later issues.

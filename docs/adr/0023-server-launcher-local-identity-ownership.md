# ADR 0023: ServerLauncher owns the optional local identity runtime

- Status: accepted
- Date: 2026-08-02

## Context

The strict identity process configuration and `LocalIdentityRuntime` were available,
but the dedicated-server process did not own them. Configuration validation could
therefore report the server settings as valid without checking trust roots, while a
normal process start never opened persistent bindings, bans, or the registry cache.

Existing smoke and development invocations without identity must remain valid until
network admission is implemented. Validation must not create or migrate a database.

## Decision

`ServerLauncher` accepts an optional, single-use command-line option:

```text
--identity-config <path>
```

When absent, launcher behavior remains unchanged and no identity runtime is opened.
When present, the strict identity configuration and trust roots are loaded before
any simulation thread starts.

In `--validate-config` mode, the identity file and trust roots are parsed and
validated, but `LocalIdentityRuntime.open` is not called. Validation therefore does
not create SQLite, migrate schema, or load the registry bundle through a runtime
provider.

During a normal start, launcher opens exactly one `LocalIdentityRuntime` with
`Clock.systemUTC()` before constructing and starting the fixed-tick runtime. The
local reference remains owned for the lifetime of the process. SQLite open or schema
failures are mapped to the existing configuration exit code before the tick loop.

A missing or rejected local registry bundle is not a process-start failure. The
runtime opens with its typed startup result and the configured authorization mode
retains its existing `LOCAL_TOFU` or fail-closed semantics.

Launcher logs only:

- whether local identity is enabled;
- the explicit authorization mode;
- the registry startup result code;
- the registry availability state.

It does not log identity file paths, SQLite paths, trust-root material, digests,
signatures, provider exception text, handles, player IDs, or addresses.

## Consequences

- server configuration validation can include identity and trust-root correctness
  without mutating persistent state;
- normal process startup owns one persistent identity composition before simulation
  starts;
- existing invocations without `--identity-config` remain compatible;
- persistence failures stop startup deterministically with the configuration exit
  code;
- missing registry data is still a domain state rather than an invented snapshot;
- sockets, cryptographic handshake, lobby membership, and raw administration
  transports remain separate work.

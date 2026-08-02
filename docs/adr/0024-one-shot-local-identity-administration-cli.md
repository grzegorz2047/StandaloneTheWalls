# ADR 0024: Local identity administration has a one-shot process mode

- Status: accepted
- Date: 2026-08-02

## Context

The server already had strict typed identity commands, independent capabilities,
transactional SQLite persistence, registry operations, and process-owned runtime
configuration. None of those operations was directly usable by the local operator
without first building a remote administration transport or starting the simulation
scheduler.

A single-owner dedicated server needs a bounded local maintenance path before RCON,
HTTP, or a GUI exists. That path must preserve the typed parser and authorization
boundary rather than duplicating mutation logic in `ServerLauncher`.

## Decision

`ServerLauncher` accepts a terminal option:

```text
--identity-command <identity command tokens...>
```

The option requires `--identity-config` and cannot be combined with
`--validate-config` or `--run-for-ticks`. All remaining arguments are copied as
already-tokenized values and passed to `IdentityAdministrationCommandParser`
without shell evaluation or launcher-level reinterpretation. Launcher options must
therefore appear before `--identity-command`.

Command mode loads the strict process configuration, opens exactly one
`LocalIdentityRuntime`, executes exactly one typed command, renders its typed
response, flushes stdout, and returns. It never constructs the fixed-tick runtime,
starts a simulation thread, or installs a shutdown hook.

The local process principal is the bounded administrator ID `local-cli` with all
four existing capabilities:

- `VIEW_IDENTITY`;
- `MANAGE_HANDLE_BINDINGS`;
- `MANAGE_PLAYER_BANS`;
- `MANAGE_REGISTRY`.

This is not a remote authorization mechanism. Possession of local process execution
and read/write access to the identity configuration and SQLite file is the security
boundary for this adapter. Future remote adapters must authenticate their own
principals and must not reuse this implicit grant.

`IdentityAdministrationCliRenderer` serializes responses into deterministic UTF-8
lines. The first line is always the stable top-level response code. Remaining lines
use explicit fields for bindings, bans, mutation results, and the bounded registry
summary. Domain-object `toString()` output is not a serialization contract.

The renderer may expose the local audit reason and public player IDs needed for
administration. It never exposes configuration paths, trust roots, signature bytes,
canonical registry JSON, provider exception text, credentials, addresses, or
sockets.

Process exit codes are:

- `0` for reads and applied or idempotent successful operations;
- `2` for launcher, configuration, or typed-command parsing errors;
- `3` for permission denial, rejected domain mutations, provider failure, or
  snapshot rejection.

## Consequences

- local identity state can be inspected and repaired without starting gameplay;
- every mutation still passes through the existing audited transactional services;
- repeated one-shot invocations reopen the same SQLite state deterministically;
- shell quoting remains the caller's responsibility, especially for a multi-word
  reason that must arrive as one token;
- stdout is stable enough for local scripts while logs remain separate diagnostics;
- interactive stdin, RCON, HTTP, GUI, persistent administrator roles, and remote
  authentication remain separate work;
- actual player-session and lobby integration is unaffected.

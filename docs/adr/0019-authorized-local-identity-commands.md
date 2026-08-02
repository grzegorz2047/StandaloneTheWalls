# ADR 0019: Authorized local identity commands

- Status: accepted
- Date: 2026-08-02

## Context

The identity policy and SQLite adapters expose atomic audited operations, but a
future console, RCON endpoint, or administration UI must not call stores directly.
Without one command contract, adapters could disagree about argument order,
accept unbounded values, perform authorization after mutation, or return
unstructured text that accidentally includes sensitive transport data.

Registry refresh and snapshot verification require a different provider boundary
and are not local binding or player-ban mutations.

## Decision

The `server` module owns a closed local identity command model and a strict parser
for an already-tokenized argument list. The parser does not tokenize raw text,
interpret shell quoting, expand variables, or execute arbitrary input.

Supported command shapes are:

- `identity list handles`;
- `identity list bans`;
- `identity inspect handle <canonicalHandle>`;
- `identity inspect ban <playerId>`;
- `identity reserve <handle> <playerId> <reason>`;
- `identity unbind <handle> <expectedPlayerId> <reason>`;
- `identity rebind <handle> <expectedPlayerId> <replacementPlayerId> <reason>`;
- `identity ban-player-id <playerId> <reason>`;
- `identity unban-player-id <playerId> <reason>`.

The adapter supplies each reason as one token; a later console tokenizer may map a
quoted phrase to that token. Every value is reconstructed through existing bounded
domain types.

An authenticated principal contains a bounded administrator ID and an immutable
set of independent capabilities:

- view identity state;
- manage handle bindings;
- manage player bans.

The executor computes the required capability and returns `PERMISSION_DENIED`
before calling either domain service when it is absent. Successful mutations use
only `LocalHandleAdministrationService` or
`LocalPlayerBanAdministrationService`, preserving their transaction and audit
semantics. Responses are typed immutable values, not formatted log strings.

## Consequences

- unauthorized attempts cannot create a binding, ban, or audit event;
- read access does not imply mutation access, and handle management does not imply
  ban management;
- exact domain mutation codes remain available to the adapter;
- list and inspect responses preserve deterministic store ordering;
- private keys, IP addresses, credentials, sockets, and registry artifacts are not
  part of commands or responses;
- raw console tokenization, persistent administrator roles, registry reload and
  snapshot verification remain separate adapters and stories.

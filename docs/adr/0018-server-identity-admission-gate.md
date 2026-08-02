# ADR 0018: Server identity admission gate

- Status: accepted
- Date: 2026-08-02

## Context

The identity foundation now has independent decisions for a stable `playerId` ban
and for canonical-handle authorization. Both decisions may mutate durable local
state indirectly: `LOCAL_TOFU` can bind a previously unseen handle. A server that
checks the handle before checking the player ban would allow a banned identity to
claim a free handle even though the session is rejected afterwards.

The future transport listener also needs one bounded result that can be translated
to a protocol rejection without exposing stores, registry objects, or persistence
errors to the lobby.

## Decision

The `server` module owns `SessionIdentityAdmissionService` as the semantic boundary
after cryptographic authentication and before lobby admission.

The gate always evaluates in this order:

1. evaluate the stable `playerId` with `PlayerBanAdmissionService`;
2. return `PLAYER_BANNED` immediately when banned;
3. otherwise evaluate the canonical handle with `HandleAuthorizationService`;
4. map the existing handle decision without changing its code, acceptance state,
   or verification level.

The gate accepts an explicit `RegistrySnapshotAvailability`; it does not fetch or
verify registry data and cannot invent a fallback mode. A rejected result has no
verification level. Accepted local sessions carry `LOCAL_UNVERIFIED`, while
accepted global sessions carry `GLOBAL_VERIFIED`.

The gate does not accept a private key, raw public key, IP address, socket, TLS
session, SQLite connection, or engine object. Those remain on their respective
sides of the boundary.

## Consequences

- a banned first-use attempt cannot create a TOFU binding;
- ban rejection has deterministic precedence over registry and handle failures;
- `GLOBAL_ONLY` and `HYBRID` keep their existing fail-closed behavior;
- future transport code can translate one bounded enum to a wire rejection;
- future lobby code must never bypass the gate or call handle authorization first;
- disconnecting an already admitted session after a later ban remains separate
  work.

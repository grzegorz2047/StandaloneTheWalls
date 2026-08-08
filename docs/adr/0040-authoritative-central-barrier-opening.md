# ADR 0040: Authoritative central barrier opening

## Status

Accepted for the first `PREPARATION -> WALLS_OPENING -> OPEN_COMBAT` vertical slice.

## Context

The deterministic match lifecycle already modeled preparation, wall opening, and open combat, while the live lobby runtime stopped advancing after entering preparation. The verified collision map also treated every named obstacle as permanently active. As a result, clients could enter the 3D scene and move authoritatively, but the central walls never opened.

The first implementation must preserve the existing reliable-TLS movement protocol, bounded map verification, client prediction, and fixed server tick. It must not introduce wall-clock timing, approximate name matching, dynamic rigid-body physics, or partial wall animation.

## Decision

`MatchLifecycle` remains the only source of truth for phase duration and transition timing. `LobbyMatchCoordinator` advances every active timed phase through `PREPARATION` and `WALLS_OPENING`, then intentionally freezes the runtime-facing lifecycle in `OPEN_COMBAT` until combat and deathmatch receive their own vertical slices.

The existing fixed-size match snapshot adds explicit wire codes for `WALLS_OPENING` and `OPEN_COMBAT`; its schema version and payload size do not change. Every authoritative countdown revision and transition is published to connected clients after entering the 3D scene.

The map-format layer recognizes central barriers only by the exact verified collision names:

- `CentralWallXCollision`
- `CentralWallZCollision`

All other obstacle names remain permanent, including suffix-compatible aliases. Static body clearance, swept horizontal movement, standing headroom, and upward ceiling sweep receive the same explicit `CLOSED` or `OPEN` barrier policy.

The barrier policy is one-way per round. A new movement simulation and a newly loaded client scene begin `CLOSED`; after becoming `OPEN`, they reject or ignore attempts to close again.

The authoritative tick order is:

1. advance and commit the match phase snapshot;
2. publish the changed phase snapshot;
3. derive the barrier policy from the committed phase;
4. process movement for that same tick.

Therefore, barriers remain solid throughout the final `WALLS_OPENING` tick and are absent from collision during the first movement tick whose committed phase is `OPEN_COMBAT`.

Before opening, horizontal movement is clamped to the assigned team region. From the first open-combat movement tick, it is clamped to the union of all verified team regions. Support, permanent obstacles, ceilings, player-body dimensions, input bounds, and snapshot bounds remain unchanged.

Client prediction uses the same policy. At the one-way policy boundary it discards the pending closed-policy prediction tail instead of replaying old steps against new collision. Subsequent prediction and reconciliation use verified global bounds and open central-barrier queries.

The verified visual scene must contain the exact nodes `CentralWallX` and `CentralWallZ`. They are detached on the renderer thread only after the authoritative client phase reaches `OPEN_COMBAT`. Ground, perimeter geometry, supports, and all other scene nodes remain attached.

## Consequences

- The first open-combat tick has identical barrier and bounds semantics on server movement and client prediction.
- Sprint and large bounded prediction steps cannot tunnel through a closed central barrier.
- Permanent obstacles continue to block after opening.
- No movement, snapshot, input, TLS, realtime-ticket, or DTLS schema changes are required.
- Segment animation, VFX, audio, projectiles, combat, deathmatch, round reset orchestration, reconnect, and join-in-progress spawn assignment remain outside this slice.

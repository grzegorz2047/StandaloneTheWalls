# ADR 0037: Authoritative team roster and lobby readiness domain

- Status: Accepted
- Date: 2026-08-03
- Decision owners: Sunderfront maintainers
- Related: #2, #14, #21, #89, #120

## Context

The Direct Connect milestone authenticates a player, applies identity policy, and
transfers the accepted session into a bounded minimal lobby. That lobby owns
membership and complete player snapshots, but deliberately rejects every inbound
lobby command. It has no team assignment, ready state, or authoritative condition
for starting the existing deterministic match lifecycle.

Those rules must not first appear inside a protocol codec, jMonkeyEngine screen,
or server networking worker. Doing so would make transport or presentation a
second source of gameplay truth and would make balancing difficult to test without
sockets and renderer state.

## Decision

### Domain boundary

`game-domain` owns a stateless `LobbyRosterRules.apply(configuration, state,
command)` transition function. It depends only on the existing `TeamId` domain
value and JDK collections. It does not know `PlayerId`, handles, TLS sessions,
protocol envelopes, clocks, threads, persistence, or UI state.

A server adapter will later derive `LobbyParticipantId` from the already
authenticated session. Clients will never be authoritative for participant
identity, current team counts, readiness totals, or the start condition.

### State and revision

`LobbyRosterState` is immutable and stores participants in strictly increasing
participant-ID order. Duplicate or non-canonical input is rejected at
construction. Every accepted state-changing command increments one signed 64-bit
revision with exact arithmetic. Rejections and idempotent commands return the
same state instance, emit no events, and do not advance the revision.

Each participant is either unassigned or belongs to one enabled team. Readiness is
valid only with a selected team.

### Commands and expected rejections

The first domain slice accepts:

- join;
- leave;
- select or change team;
- set ready or not ready.

Expected invalid requests return stable rejection codes for full lobby, duplicate
or unknown participant, disabled or full team, avoidable imbalance, and ready
without a team. Invalid persisted/programmer-created state that contradicts the
configuration fails immediately instead of being repaired silently.

### Team balancing

Configuration explicitly selects at least two active teams and bounds total and
per-team capacity. The standard configuration enables Green, Blue, Red, and
Yellow with 40 total places and 10 places per team.

For a team-selection request, the participant is first removed from its current
team for the purpose of evaluating candidates. Every enabled non-full destination
is simulated. The request is accepted only when its resulting largest-minus-
smallest team-size spread equals the best spread available at that moment.

This rule is deterministic and prevents a client from choosing a destination that
worsens balance when an equally available better destination exists. It does not
automatically move players, choose a team on join, model parties, or preserve a
preferred team across reconnects.

Changing teams clears readiness in the same state transition. The player must
confirm readiness again against the new roster.

### Start readiness

`readyToStart` is a derived value, not a command and not a client claim. It is true
only when:

1. the configured minimum number of participants is present;
2. every participant has an enabled team;
3. every participant is ready;
4. at least two teams are represented.

A later server integration issue will translate this derived edge into the
existing `MatchLifecycle` countdown command. This ADR does not start a match or
make the lobby coordinator part of the fixed-tick simulation.

## Consequences

### Positive

- Team and readiness rules have one renderer- and transport-independent source of
  truth.
- The same state and command always produce the same decision, events, and
  revision.
- Manual team selection cannot bypass the best currently available balance.
- Team changes cannot accidentally retain stale readiness.
- Protocol, server composition, and UI can be implemented as separate bounded
  slices against a stable domain contract.

### Negative

- A player cannot deliberately stack a larger team even when friends prefer the
  same side; party-aware balancing is deferred.
- Join does not assign a team automatically, so readiness remains false until an
  explicit selection is accepted.
- State revisions are process-local and are not reconnect or persistence tokens.
- The domain identifier needs an explicit trusted mapping from authenticated
  server sessions in the integration layer.

## Alternatives rejected

### Put team selection directly in `MinimalLobbyRuntime`

Rejected because that runtime owns reliable I/O and session lifecycle. Embedding
rules there would couple gameplay decisions to virtual threads and protocol
failure handling.

### Trust a client-supplied team count or ready-to-start flag

Rejected because the dedicated server is authoritative and already owns the
complete roster.

### Accept any non-full destination and rebalance later

Rejected because it permits avoidable imbalance and requires disruptive automatic
moves after players have declared intent.

### Increase revision for every request

Rejected because idempotent retries and rejected client commands are not visible
state changes.

## Follow-up

- Add bounded reliable team/ready commands and roster snapshots without repeating
  authenticated identity fields.
- Integrate the domain with the process-owned minimal lobby outside the fixed-tick
  thread.
- Add the keyboard-first team/ready UI and localized public rejection messages.
- Feed the authoritative `readyToStart` transition into `MatchLifecycle` and
  reset readiness when a round or lobby epoch changes.

# ADR 0028: Automatic HTTPS registry refresh is single-flight and lifecycle-owned

- Status: accepted
- Date: 2026-08-02
- Issue: #75
- Depends on: ADR 0023, ADR 0025, ADR 0026, ADR 0027

## Context

The server can already fetch one detached registry artifact from explicit HTTPS
resources, verify it, atomically cache the exact verified bytes, and publish the
snapshot only after the cache commit succeeds. Startup always restores from the
local `SFRB` bundle and remains independent of the network.

A long-running server still needs an optional refresh cadence. Running HTTP or
cache I/O from the fixed-tick thread is forbidden. Fixed-rate scheduling is also
unsafe because a slow provider call can overlap the next nominal start. Retrying
without bounds can create an outage loop, while publishing a result after shutdown
has taken ownership can resurrect process state during termination.

## Decision

Automatic refresh is a dedicated process lifecycle component owned by
`ServerLauncher`. It is available only when
`identity.registry.refresh-source=HTTPS` and is disabled by default. The launcher
does not start it in validate-only mode or one-shot `--identity-command` mode.
`LOCAL_BUNDLE` rejects every scheduler key instead of ignoring it.

The configuration uses integer seconds:

| Property | Default | Accepted range |
|---|---:|---:|
| `identity.registry.scheduler.enabled` | `false` | exact `true` or `false` |
| `identity.registry.scheduler.initial-delay-seconds` | `60` | `0..604800` |
| `identity.registry.scheduler.success-interval-seconds` | `3600` | `1..604800` |
| `identity.registry.scheduler.initial-failure-backoff-seconds` | `30` | `1..604800` |
| `identity.registry.scheduler.maximum-failure-backoff-seconds` | `1800` | initial backoff through `604800` |
| `identity.registry.scheduler.maximum-jitter-seconds` | `5` | `0..3600` |

The loader rejects malformed, negative, overflowing, or out-of-range values before
runtime construction. Nanosecond conversion is checked explicitly. The maximum
failure backoff cannot be lower than the initial failure backoff.

One scheduler owns one named executor thread. The existing HTTPS administration
service serializes `verify-snapshot`, `reload-registry`, and automatic refresh on
one monitor, so the shared provider and cache-before-activation workflow are
single-flight even when a manual command races the scheduler.

A new attempt is scheduled only after the previous attempt returns. The selected
next delay is therefore measured from attempt completion, never from its nominal
start time.

`ACTIVATED` and `UNCHANGED` are successes. They reset consecutive failures to zero
and select the success interval. Every other semantic result is retried:

| Result | Classification | Base delay before jitter |
|---|---|---|
| `ACTIVATED` | success | success interval |
| `UNCHANGED` | success | success interval |
| provider failure | retryable failure | exponential backoff |
| snapshot rejection, including invalid signature | retryable failure | exponential backoff |
| rollback rejection | retryable failure | exponential backoff |
| equivocation rejection | retryable failure | exponential backoff |
| cache-write failure | retryable failure | exponential backoff |
| unexpected unchecked failure | retryable internal failure | exponential backoff |

For failure number `n`, the base delay is
`min(maximumBackoff, initialBackoff * 2^(n-1))`. The implementation doubles with a
pre-multiplication cap, so arithmetic cannot overflow and the calculation stops as
soon as the maximum is reached.

Jitter is an injected source returning an additive nanosecond offset. Production
uses a uniform offset within the configured maximum; tests inject exact boundary
values. An out-of-contract source is clamped. Retry delay is clamped to at least
one second and at most the configured maximum failure backoff. Success delay is
clamped to the same safe minimum and the global seven-day scheduling bound.

The immutable status exposes only:

- `DISABLED`, `RUNNING`, or `CLOSED`;
- the last bounded semantic result, if any;
- consecutive failure count;
- the delay until the next attempt, if one is waiting.

It never contains URIs, payload bytes, signatures, handles, provider exception
text, or transport details.

`close()` is idempotent. It first changes ownership to `CLOSED`, clears the next
attempt, and invalidates the active generation. It then cancels waiting work and
uses `shutdownNow()` to interrupt an active provider call. The JDK HTTPS provider
preserves interruption as a bounded provider failure. The executor is joined
before close returns. A result that arrives after shutdown ownership changed is
not published and cannot schedule another attempt.

## Consequences

- registry network and cache I/O never execute on the fixed-tick thread;
- slow attempts cannot overlap later nominal starts;
- manual and automatic provider access share one single-flight boundary;
- outages use bounded exponential retry instead of a tight loop;
- successful recovery immediately resets the failure history;
- provider, verifier, rollback, equivocation, and cache failures preserve the
  active last-known-good snapshot and prior valid `SFRB` through ADR 0026;
- an offline restart still verifies and activates the last valid local bundle
  before any optional scheduler can start;
- shutdown interrupts waiting or active work and suppresses late publication;
- mutable release discovery, authentication, transport changes, player sessions,
  lobby admission, display-name storage, and snapshot/SFRB format changes remain
  outside this decision.

# Automatic HTTPS registry refresh

Automatic refresh is an opt-in process feature for servers configured with:

```text
identity.registry.refresh-source=HTTPS
```

It is disabled by default and is never started for `LOCAL_BUNDLE`,
`--validate-config`, or one-shot `--identity-command` execution. Startup always
verifies the configured local `SFRB` bundle first and does not perform HTTP.

## Configuration

All duration properties use integer seconds:

```text
identity.registry.scheduler.enabled=false
identity.registry.scheduler.initial-delay-seconds=60
identity.registry.scheduler.success-interval-seconds=3600
identity.registry.scheduler.initial-failure-backoff-seconds=30
identity.registry.scheduler.maximum-failure-backoff-seconds=1800
identity.registry.scheduler.maximum-jitter-seconds=5
```

Accepted limits are:

| Property | Minimum | Maximum |
|---|---:|---:|
| initial delay | 0 | 604800 |
| success interval | 1 | 604800 |
| initial failure backoff | 1 | 604800 |
| maximum failure backoff | initial backoff | 604800 |
| maximum jitter | 0 | 3600 |

Scheduler properties are rejected when the refresh source is `LOCAL_BUNDLE`.
Malformed booleans, negative values, forbidden zero values, unit-conversion
overflow, values above the hard bounds, and a maximum backoff below the initial
backoff fail configuration loading.

## Retry behavior

`ACTIVATED` and `UNCHANGED` reset the consecutive-failure counter and schedule the
next attempt after the success interval. Provider failure, snapshot rejection,
rollback, equivocation, cache failure, and unexpected unchecked failure increment
the counter and use capped exponential backoff:

```text
min(maximumFailureBackoff, initialFailureBackoff * 2^(failures - 1))
```

A testable additive jitter offset is applied after selecting the base delay. The
final retry delay is clamped to at least one second and at most the configured
maximum failure backoff. The next delay is measured from completion of the prior
attempt, not from its nominal start.

## Lifecycle and status

One named executor thread owns automatic attempts. The same HTTPS administration
service serializes automatic refresh, manual verification, and manual reload, so
only one provider workflow can run at a time.

The immutable status contains only scheduler state, the last semantic result,
consecutive failures, and the next delay. It excludes URIs, payloads, signatures,
and exception text.

Closing is idempotent. It cancels waiting work, interrupts an active provider call,
joins the executor, and suppresses any result that arrives after shutdown takes
ownership. Provider, verification, activation, or cache failure retains the active
last-known-good snapshot and the prior valid local bundle.

See ADR 0028 for the complete decision and failure classification.

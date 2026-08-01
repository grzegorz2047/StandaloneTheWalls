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

Supported properties:

- `server.name`
- `server.tick-rate` — 10 through 60, default 20
- `server.reliable-port` — default 27420
- `server.realtime-port` — default 27421 and must differ from the reliable port
- `server.maximum-players` — 1 through 40

Unknown properties, malformed numbers, invalid ports, unsafe tick rates, and
capacities above the current product target fail closed.

## Smoke mode

A bounded headless run is available for CI and packaging checks:

```bash
./gradlew :server:run --args="--run-for-ticks 20"
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
transport queues, and administration belongs to later issues.

# ADR 0007: Owned asynchronous reliable channel over TLS framing

- Status: Accepted for the asynchronous reliable-channel slice
- Date: 2026-08-02
- Issues: #34, #48
- Depends on: ADR 0002, ADR 0005, ADR 0006

## Context

ADR 0005 establishes an authenticated TLS 1.3 connection and ADR 0006 adds a
strict blocking `TlsEnvelopeStream`. The fixed-tick simulation and UI must never
execute that blocking I/O. The original `ReliableChannel` placeholder also
accepted a caller-created `ProtocolEnvelope`, which would let callers choose the
session UUID and outbound sequence even though ADR 0006 assigns both inside the
transport.

Simply wrapping each operation in `CompletableFuture.supplyAsync` would use an
implicit common pool, provide no memory admission limit, allow multiple blocking
readers, and leave ownership of queued tasks and close behavior undefined.

## Decision

### Protocol boundary

`ReliableChannel` now exposes:

- `send(MessageType, byte[])`, returning a `ReliableSendResult` with the sequence
  assigned by the transport;
- `receive()`, returning `Optional<ProtocolEnvelope>`, where an empty result means
  a clean peer end-of-stream;
- `close()`, returning an idempotent `CompletionStage<Void>` that completes only
  after owned I/O resources have terminated;
- `isOpen()` for a non-blocking state observation.

The caller cannot select the outbound session UUID or sequence. Payload bytes are
copied before asynchronous execution so later caller mutation cannot change the
frame.

### Explicit executor ownership

`AsyncTlsReliableChannel` owns one named virtual-thread-per-task
`ExecutorService` created with Java 21 APIs. It also owns a separate named virtual
closer thread. It never uses `ForkJoinPool.commonPool`, a framework-global pool,
or an executor supplied by an unrelated runtime component.

The executor is private to one channel and is always shut down by that channel.
The package-private constructor permits deterministic tests with an injected
executor, but it has the same ownership contract: the channel takes ownership and
terminates it.

The separate closer is required because a receive virtual thread may be blocked
inside TLS. Close first closes the underlying `TlsEnvelopeStream`, which unblocks
I/O, and only then shuts down and awaits the I/O executor. The wait is bounded by
the configured close timeout; one forced-interruption attempt is also bounded.

### Admission limits

Every channel has immutable limits for:

- maximum pending send operations;
- maximum aggregate bytes retained by pending sends;
- close timeout.

A send reserves both count and byte capacity before executor submission. The
reservation includes running and waiting sends. Capacity is released exactly once
on success, failure, close, or executor rejection. A payload that cannot fit the
configured byte limit is rejected before it is copied or submitted. Exceeding an
admission limit does not close an otherwise healthy channel.

The initial safety caps are 4096 pending operations, 64 MiB pending bytes, and a
30-second close timeout. Defaults are 256 operations, 1 MiB, and 5 seconds.

### Receive ownership

Exactly one receive operation may be active per channel. A second concurrent
receive fails immediately with `RECEIVE_IN_PROGRESS` and never starts a second
reader. After a successful message, another receive may be submitted. Clean EOF
moves the channel to closing before the empty result is published to callbacks.

### State and failure model

The explicit states are:

- `OPEN`;
- `CLOSING`;
- `CLOSED`;
- `FAILED`.

A user close, clean EOF, protocol failure, I/O failure, or executor failure moves
the channel out of `OPEN` before operation callbacks observe the terminal result.
New operations are therefore rejected deterministically.

A terminal protocol or I/O exception is preserved as the primary failure for the
operation, the channel close stage, and later `FAILED` rejections. Secondary close
failures are attached as suppressed exceptions. Payload bytes, key material,
certificates, and channel-binding bytes are never included in error messages.

Pending sends and an active receive are completed exactly once when termination
begins. Closing the TLS stream releases a task that is already blocked in I/O.
Repeated `close()` calls return views of the same close future.

### Cancellation behavior

The API returns minimal `CompletionStage` views rather than exposing the internal
`CompletableFuture` instances. Cancelling a caller-created future view cannot
cancel or mutate an already admitted internal operation. Explicit `close()` is
the only supported operation-wide cancellation mechanism because interrupting a
partially written frame independently from the connection would make delivery
ambiguous.

## Validation requirements

Unit tests must prove:

- send and receive I/O run on executor threads, not the API caller;
- payload mutation after `send` does not change transmitted bytes;
- operation-count and aggregate-byte limits reject immediately without closing a
  healthy channel;
- only one receive may be active;
- close completes a blocked receive and all pending sends;
- clean EOF closes successfully;
- a terminal exception remains the primary cause for close and later operations;
- the owned executor is terminated before the close stage completes;
- configuration limits are bounded.

A real TLS loopback must prove:

- asynchronous multi-message send and receive in both directions;
- transport-assigned independent sequences;
- clean EOF propagation;
- close while receive is blocked returns immediately to the caller and completes
  after TLS shutdown;
- malformed framing propagates its `ProtocolException`, closes the channel, and
  remains the close failure.

The full repository quality gate remains required. No new dependency is needed.

## Consequences

- Client and server runtime code can consume the renderer-independent
  `ReliableChannel` without performing TLS I/O on their own threads.
- Backpressure has explicit count and memory dimensions.
- Virtual threads keep the blocking TLS implementation simple without hiding a
  shared executor.
- Channel close has an observable completion point suitable for runtime shutdown.
- This slice still does not own `ServerSocket` accept loops, connection admission,
  identity-flow orchestration, reconnect, public PKI, or realtime transport.

## Rejected alternatives

- **`CompletableFuture.supplyAsync` without an executor:** silently uses a common
  pool and gives the channel no resource ownership.
- **One unbounded executor queue:** retains attacker-controlled payloads without a
  memory ceiling.
- **Multiple concurrent receives:** starts competing readers on one ordered byte
  stream and makes delivery nondeterministic.
- **Caller-created outbound envelopes:** permits incorrect session identifiers
  and sequence numbers.
- **Interrupt-only close:** may not release provider I/O and does not define which
  partial frame reached the peer.
- **Closing the I/O executor before TLS:** can strand a receive task that needs the
  socket close to unblock.
- **Cancelling internal futures from caller views:** creates ambiguous delivery
  for writes already admitted to the TLS stream.

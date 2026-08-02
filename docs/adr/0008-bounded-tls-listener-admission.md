# ADR 0008: Bounded TLS listener admission and active-connection leases

- Status: Accepted for the server listener slice
- Date: 2026-08-02
- Issues: #34, #51
- Depends on: ADR 0005, ADR 0006, ADR 0007

## Context

The transport module can perform one authenticated TLS 1.3 handshake, frame
protocol envelopes and expose bounded asynchronous reliable I/O. The dedicated
server still needs an owner for the listening socket and for the resources that
exist before and after a handshake.

Opening one virtual thread for every accepted TCP socket without admission would
allow an unauthenticated peer to retain unbounded sockets, TLS state and task
objects. Limiting only authenticated players would not bound half-open or stalled
handshakes. Running accept or handshake work on the fixed-tick simulation thread
would also violate the server runtime boundary.

The current envelope cannot be used as the first post-TLS byte stream yet. Every
envelope already contains a session UUID, while no protocol step currently tells
both peers which UUID to use. The listener must not invent a different local UUID
on each side or silently overload the TLS exporter as a session identifier.

## Decision

### Endpoint and thread ownership

`Tls13ServerListener` owns:

- one already-bound `ServerSocket`;
- one named non-daemon platform accept thread;
- one private named virtual-thread-per-task executor for TLS handshakes and the
  accepted-connection callback;
- one separate named virtual closer;
- all unauthenticated sockets currently inside a handshake;
- every authenticated `AcceptedTlsConnection` lease until that lease is closed.

The listener does not use a common pool and does not run accept, TLS handshake or
handler work on the constructor, `start`, caller or simulation thread.

Construction binds the socket immediately so bind failures are synchronous and
cannot leave a partially started listener. `start()` may be called exactly once.
Closing a listener that has not been started is valid and releases the bound
socket and owned executor.

### Admission model

Two fair semaphores enforce independent hard limits:

1. an active-connection permit is reserved for every socket before TLS begins;
2. a concurrent-handshake permit is then reserved for the TLS work itself.

The active limit therefore bounds authenticated leases plus handshakes that may
become authenticated. It cannot be exceeded between handshake completion and
lease registration. The handshake limit independently bounds the CPU and memory
used by cryptographic work.

When no active permit is available, the new TCP socket is closed immediately and
an `ACTIVE_CONNECTION_LIMIT` event is emitted. When the active permit exists but
no handshake permit is available, the active permit is returned, the socket is
closed and a `CONCURRENT_HANDSHAKE_LIMIT` event is emitted. No handshake task is
submitted in either case.

Every accepted socket receives a finite read timeout before entering Bouncy
Castle TLS. A failed or timed-out handshake closes the socket and returns both
permits. One handshake failure does not stop the listener.

The immutable configuration has hard upper bounds for backlog, concurrent
handshakes, active connections, handshake timeout and shutdown timeout. Port zero
is allowed for tests and dynamically assigned local endpoints; unresolved bind
addresses are rejected.

### Authenticated lease

A completed handshake becomes one `AcceptedTlsConnection` with:

- a monotonically increasing positive process-local connection identifier;
- the remote socket address;
- authenticated `Tls13SessionSecurity` metadata;
- encrypted input and output streams;
- an idempotent `close()` operation.

The lease is the public ownership boundary. The raw `Tls13Connection` remains
package-private so a public caller cannot close TLS without returning the active
permit. Closing the lease closes TLS and releases listener admission exactly
once, even when TLS close throws.

The accepted-connection handler executes on the handshake virtual thread after
the handshake permit has been returned. A normal return transfers ownership of
the lease to the handler. If the handler throws, the listener closes the lease,
returns the active permit and emits `HANDLER_FAILED`; later clients may still be
accepted.

This slice intentionally does not construct `TlsEnvelopeStream` or
`AsyncTlsReliableChannel`. A later versioned bootstrap must first establish the
logical session UUID over the authenticated TLS channel.

### Event boundary

The listener reports bounded diagnostic events for:

- active-connection rejection;
- concurrent-handshake rejection;
- handshake executor rejection;
- handshake failure;
- handler failure;
- fatal accept-loop failure;
- shutdown failure.

Events may retain the original exception for an internal observer, but their
`toString()` exposes only the event code, whether a remote address exists and the
exception class. It never prints exception messages, certificates, keys,
exporter values or application bytes.

The observer is diagnostic only. An exception thrown by the observer is ignored
and cannot change admission, trust or listener lifecycle.

### Failure and shutdown

A single handshake or handler failure is connection-local. An unexpected accept
failure or handshake-executor rejection while running is terminal and initiates
listener shutdown.

Shutdown order is:

1. move atomically out of `RUNNING`;
2. close the listening socket to unblock `accept()`;
3. close every tracked handshake socket;
4. close every active lease;
5. shut down and await the handshake executor;
6. perform one bounded forced-interruption attempt when needed;
7. join the accept thread with a bounded wait;
8. complete the shared close stage as `CLOSED` or `FAILED`.

Closing the listening socket during an intentional shutdown is not an accept-loop
failure. Repeated close calls observe the same terminal close stage. Shutdown
failures are combined using one primary cause and suppressed secondary causes.

The synchronous `close()` wrapper waits for the asynchronous close only for a
bounded interval derived from the configured shutdown timeout.

## Validation requirements

Configuration and event unit tests must prove:

- unresolved addresses and out-of-range limits fail before bind;
- ephemeral loopback port zero is valid;
- sub-millisecond and over-maximum durations are rejected;
- public event text omits exception messages;
- limit and failure event constructors cannot be confused.

Real TLS loopback tests must prove:

- a valid connection is handed to a virtual-thread handler with authenticated
  server metadata;
- the active limit rejects an excess socket without starting TLS;
- closing a lease returns its permit exactly once and allows a later client;
- listener shutdown closes an active lease;
- one stalled handshake consumes the only handshake permit;
- another socket is rejected by the handshake limit;
- the stalled handshake times out and releases admission;
- a valid TLS client can connect after that timeout;
- a handler exception closes only its lease and does not stop later admission;
- shutdown closes a stalled handshake, terminates owned resources and does not
  report the intentional listener close as an accept failure.

The full repository quality gate remains required. The implementation adds no
external dependency.

## Consequences

- The unauthenticated and authenticated socket population has explicit upper
  bounds before integration with the server runtime.
- TLS work is isolated from the simulation thread.
- Runtime code receives an authenticated lease rather than an untracked raw
  socket.
- Listener shutdown has one bounded completion point and closes all known network
  resources.
- Capacity for 40 players is still a target requiring load and soak evidence; the
  admission limits do not prove that capacity.
- Session bootstrap, identity challenge orchestration, command queues, client
  dialing, reconnect, public PKI and realtime transport remain separate work.

## Rejected alternatives

- **One virtual thread per socket without semaphores:** virtual threads reduce
  thread cost but do not bound sockets, TLS state or retained memory.
- **Only an authenticated-player limit:** stalled handshakes remain unbounded.
- **Acquiring the active permit after TLS:** multiple successful handshakes could
  oversubscribe the active limit before registration.
- **Running the handler on the accept thread:** one slow callback stops all new
  admissions.
- **Returning a public raw `Tls13Connection`:** callers could bypass active-permit
  release.
- **Creating a random session UUID independently on each peer:** the first
  envelopes would disagree and fail the session binding.
- **Deriving the envelope session UUID directly from the exporter without a
  versioned protocol decision:** silently changes the session contract and makes
  rotation or multiple logical sessions ambiguous.
- **Treating every handshake failure as listener-fatal:** one malformed client
  could stop the dedicated server.
- **Unbounded shutdown waits:** a stalled handler or provider call could prevent
  process termination indefinitely.

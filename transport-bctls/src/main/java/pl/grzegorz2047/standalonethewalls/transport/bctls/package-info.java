/**
 * Low-level Bouncy Castle TLS 1.3 and reliable-envelope adapters for Sunderfront.
 *
 * <p>The module owns authenticated client/server handshakes, bounded listener admission, a strict
 * fixed-size session UUID bootstrap, fixed-header envelope framing, bounded asynchronous reliable
 * I/O and the channel-bound Identity Proof V2 exchange. Identity orchestration runs on named
 * virtual threads, has bounded step/overall/close deadlines, consumes or discards each challenge
 * and returns a post-authentication channel that forbids identity state-machine re-entry.
 * Server-side stream closure owns the full accepted lease and returns listener admission. Handle
 * authorization, runtime command delivery, public PKI, reconnect, realtime DTLS, persistence and
 * production certificate provisioning remain separate work.
 */
package pl.grzegorz2047.standalonethewalls.transport.bctls;

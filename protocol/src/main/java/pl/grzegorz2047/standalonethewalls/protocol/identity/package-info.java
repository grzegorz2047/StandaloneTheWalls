/**
 * Application-specific Ed25519 player identity, server pinning, and replay-resistant channel-bound
 * challenge-response contracts.
 *
 * <p>This package proves client-key possession and models server trust. It does not implement TLS,
 * QUIC, DTLS, certificate issuance, secure-channel exporter generation, local/global nickname
 * authorization, or private-key persistence. See ADR 0003 for player identity, ADR 0004 for server
 * identity/channel binding, and issue #34 for the transport adapter.
 */
package pl.grzegorz2047.standalonethewalls.protocol.identity;

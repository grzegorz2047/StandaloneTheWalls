/**
 * Offline verification and atomic activation of signed global-handle registry snapshots.
 *
 * <p>Providers supply untrusted artifact bytes. Trust comes only from locally configured Ed25519
 * roots, exact RFC 8785 canonical JSON, detached SHA-256 and a root signature over those exact
 * bytes. Canonicalization precedes bounded duplicate-detecting schema parsing. Verified immutable
 * snapshots can be activated monotonically without a GitHub, network, filesystem, SQLite or UI
 * dependency. Claim authoring, root-transition ceremonies, persistence, download adapters and
 * server authorization modes remain separate work.
 */
package pl.grzegorz2047.standalonethewalls.registry;

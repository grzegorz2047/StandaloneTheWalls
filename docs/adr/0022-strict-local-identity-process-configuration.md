# ADR 0022: Strict local identity process configuration

- Status: accepted
- Date: 2026-08-02

## Context

`LocalIdentityRuntime` requires explicit file paths, authorization mode, trusted
registry roots, and bounded snapshot policy. Supplying those values through generic
Java properties would silently overwrite duplicate keys and would make path
resolution depend on the process working directory. Trust roots also need a format
that cannot be confused with a private key, credential, or arbitrary text blob.

The process must never invent an authorization mode or trust root when required
configuration is missing.

## Decision

Local identity uses a separate bounded UTF-8 configuration file. It is parsed as
one literal `key=value` property per line rather than with `Properties.load`.
Blank lines and full-line `#` comments are allowed. Escapes, edge whitespace,
control characters, duplicate keys, unknown keys, malformed UTF-8, symlinks,
non-regular files, and files larger than 64 KiB are rejected.

The following keys are mandatory:

- `identity.sqlite-path`;
- `identity.registry-bundle-path`;
- `identity.authorization-mode`;
- `identity.trust-roots-path`.

Authorization mode must be exactly `LOCAL_TOFU`, `GLOBAL_ONLY`, or `HYBRID`.
Relative paths are resolved against the identity configuration file's directory.
The SQLite, registry bundle, and trust-root paths must identify three different
files.

Optional snapshot policy values use units in their property names and default
exactly to `RegistrySnapshotPolicy.DEFAULT`:

- minimum sequence;
- maximum age in seconds;
- maximum future skew in seconds;
- maximum canonical JSON bytes;
- maximum entry count.

The trust-root file is a separate regular UTF-8 file limited to 16 KiB and 1–64
non-empty lines. Each line must be lowercase hexadecimal X.509 DER for an Ed25519
public key. Uppercase, whitespace, comments, duplicate roots, malformed hex,
private-key DER, and non-Ed25519 keys are rejected. Error messages identify only
the line number and validation class; they never include the raw key material.

Example files contain no production root. The trust-root example is an explicit
placeholder and must be replaced before loading.

## Consequences

- required identity state cannot be silently defaulted;
- duplicate or misspelled configuration never changes semantics unnoticed;
- relative paths are stable across working directories;
- snapshot limits remain bounded by the existing domain constructor;
- private material cannot be accepted as a registry trust root;
- process launcher integration remains a separate step;
- generating, distributing, or rotating production registry roots remains outside
  the dedicated server.

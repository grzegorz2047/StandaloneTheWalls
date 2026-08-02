# ADR 0032: Pinned asset lock and atomic runtime cache

## Status

Accepted.

## Context

Runtime models, textures, audio, and fonts are too large and too frequently revised to be stored as ordinary source files. Fetching them from mutable URLs would make builds and installations irreproducible, while extracting untrusted archives directly into an active directory would risk path traversal, special files, zip bombs, incomplete updates, and loss of the last-known-good cache.

## Decision

Runtime packs are named only by `assets/assets.lock.json`. The schema is exact-byte canonical JSON and pins pack ID, semantic version, format version, immutable HTTPS URL, exact archive size, lowercase SHA-256, manifest path, and manifest SHA-256. Unknown fields, duplicate keys, mutable path aliases, noncanonical values, and alternate JSON serialization are rejected.

`:asset-pack:syncAssets` is the only repository task that performs network access. Normal compilation and tests never download production packs. The HTTPS provider disables redirects and applies bounded connect and request timeouts. Unit tests use an exact URI-to-file fixture provider.

A pack is downloaded to a temporary file with a hard byte limit. Size and archive SHA-256 are verified before extraction. ZIP central-directory metadata is validated before writes: UTF-8 names, entry count, sizes, compression ratio, duplicate paths, absolute paths, traversal, encryption, ZIP64, Unix symbolic links, and special files are rejected.

Extraction occurs in a fresh staging directory. The canonical manifest is verified against the lock, its license must be explicitly redistributable, and every non-manifest file must have an exact size and SHA-256 entry. Missing and orphan files fail closed.

Verified versions are stored under a content-addressed path and are never overwritten. The staging directory is atomically moved into place, then a separate active pointer is atomically replaced. Failure before or after extraction leaves the previous pointer untouched. Offline resolution never invokes a provider and revalidates the committed marker, manifest, and file tree.

Large runtime binaries and common asset/archive extensions are rejected from ordinary Git history, except for a bounded test-fixture location.

## Consequences

- Asset updates are reviewable lockfile changes.
- Repeated synchronization of the same lock resolves to the same content-addressed tree.
- A filesystem without atomic move support cannot activate a new pack.
- The initial lock may be empty until a separately governed production asset repository exists.
- Cryptographic pack signatures can be added later without changing the provider boundary or cache layout.

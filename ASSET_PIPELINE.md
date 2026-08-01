# Asset pipeline and Git history policy

This document defines how Sunderfront can use substantial free 3D, texture,
audio, animation, font, and map content without turning the code repository into
a permanent binary archive. Implementation is tracked by issue #33.

## Goals

- Keep ordinary clones, fetches, reviews, and repository history small.
- Preserve exact, reproducible asset versions and complete license evidence.
- Work without paid storage or a mandatory proprietary asset service.
- Allow the distribution host to move without changing the pack format.
- Support offline development and play after a verified pack has been cached.
- Keep optimized runtime content separate from large editable source files.

## Repository split

### Code repository

The `StandaloneTheWalls` repository stores:

- Java/Kotlin build and application code;
- schemas, manifests, lockfiles, license records, and import settings;
- small UI icons, tiny thumbnails, and fonts only when their size and license are
  appropriate for normal Git history;
- deliberately small test fixtures required by automated tests;
- scripts and deterministic tooling that construct or validate packs.

It does not normally store:

- full production `.twmap` archives;
- large `.glb`/`.gltf` scenes and character packs;
- high-resolution source textures;
- WAV masters or large music stems;
- `.blend`, sculpting, painting, or digital-audio-workstation source projects;
- downloaded, extracted, generated, converted, cached, or packaged assets.

A CI allowlist/size gate will enforce the exceptions for test fixtures.

### Asset repository and releases

Large content should use a separate repository such as `sunderfront-assets`.
Its normal Git history contains pack manifests, build scripts, license records,
and small metadata. Immutable versioned GitHub Releases carry the archives.

Two pack classes are kept separate:

- **runtime packs** contain optimized game-ready GLB, compressed textures, OGG,
  fonts, thumbnails, collision data, and production maps;
- **source packs** contain editable Blender/audio/texture sources and provenance
  needed to rebuild or modify the runtime content.

Players and ordinary game builds only need runtime packs. Content contributors
can fetch source packs explicitly.

Git LFS is not a mandatory dependency. It may be evaluated later for a narrowly
scoped contributor workflow, but free LFS storage/bandwidth limits must not make
building or distributing the game dependent on payment.

## Immutable release rule

A published pack version is immutable. Corrections create a new semantic version
and a new release asset; an existing archive is never silently replaced.

Builds and server configurations must not depend on a moving `latest` URL. They
pin a specific immutable release URL and verify the expected bytes.

## Lockfile

The code repository will contain `assets/assets.lock.json`. Each required pack
entry includes at least:

- lock schema version;
- pack ID and semantic version;
- pack format version;
- immutable download URL or provider-relative locator;
- exact compressed byte size and safe extracted-size limit;
- SHA-256 of the archive;
- manifest path and manifest SHA-256;
- license-summary path and hash;
- required/optional role and supported platform/content variant;
- expected game/protocol compatibility range.

The lockfile is reviewed like a dependency update. A changed URL, version, size,
hash, or license summary is visible in the PR. Reproducible release builds use
only the committed lockfile.

## Pack contents

A pack archive contains a canonical manifest listing every file with:

- relative normalized path;
- byte size and SHA-256;
- media/content type;
- logical asset ID and runtime role;
- source URL or source pack reference;
- author/creator attribution;
- SPDX-style license identifier or exact license reference;
- redistribution status and required attribution;
- modification/conversion notes;
- toolchain/importer version where reproducibility requires it.

Paths are data, never executable instructions. Packs cannot contain native
libraries, scripts to run, arbitrary Java classes, custom executable shaders, or
post-install hooks.

## `syncAssets` behavior

A Gradle/tooling task downloads packs into a user cache outside tracked source
paths. It must:

1. acquire an inter-process lock for the target pack;
2. stream into a temporary file with strict compressed-size and timeout limits;
3. verify the archive SHA-256 before extraction;
4. inspect the archive for absolute paths, traversal, links, duplicates, invalid
   Unicode/path forms, excessive entry counts, and zip-bomb limits;
5. extract to a temporary directory with per-file and total-size limits;
6. verify the canonical manifest, every listed file hash, required licenses, and
   the absence of unlisted files;
7. atomically move the verified directory into the content-addressed cache;
8. record only non-sensitive diagnostic metadata.

A failed download or validation never replaces a working cached pack. Partial
files are cleaned up or quarantined and are not visible to the game.

## Offline behavior

If the exact verified pack is cached, development, client startup, server startup,
and map loading can work without GitHub or Internet access. If a required pack is
missing, the program reports its ID, version, size, and expected source instead of
silently substituting a different version.

Developers may configure a local directory or static mirror provider. The provider
returns bytes for the same immutable locator; validation remains identical.

## Release assembly

Unit tests and ordinary domain builds do not download production packs. Separate
asset-aware integration/release tasks resolve the lockfile.

A release package records the exact asset lockfile and includes either:

- the verified runtime content required for a self-contained distribution; or
- an explicit first-run download manifest when a thin distribution is chosen.

The default player experience should not require development tooling or Git LFS.
Server packages include at least one legally redistributable verified map, either
embedded or resolved by the same pinned mechanism.

## Licenses and provenance

Every asset must have a redistribution license compatible with the intended pack.
A page merely offering a free download is not enough. Missing authorship, source,
license text, or modification history blocks inclusion.

CC0 and similarly permissive assets are preferred, but attribution-required
licenses can be used only when the runtime and release documentation satisfy all
terms. Share-alike, non-commercial, no-derivatives, trademark, personality,
font-embedding, and dataset/model-output restrictions require explicit review.

Generated or modified assets retain source prompts/settings or modification notes
where doing so is necessary to establish provenance. No Minecraft assets or close
copies of Minecraft's protected visual/audio/UI identity are accepted.

## Map distribution

User-created `.twmap` files remain separate from the official production asset
pack. A server can host the exact current map for clients, while client and server
verify the map package hash and format limits.

Official maps can also be versioned release assets. Referencing a map by ID alone
is insufficient; sessions and caches use an immutable version/hash tuple.

## Provider portability

Download code targets an `AssetPackProvider` boundary. A locator may initially
resolve to a GitHub Release, then later to a static HTTPS mirror, local filesystem,
LAN cache, or another free/owned host. Provider changes do not alter archive,
manifest, license, or lockfile verification semantics.

The trust anchor is the committed expected hash and reviewed provenance, not the
continued availability or account security of one hosting provider.

## Required CI checks

- reject tracked files above the configured limit unless explicitly allowlisted;
- reject forbidden production binary/source extensions in the code repository;
- validate lockfile schema, uniqueness, immutable locator, sizes, and hashes;
- validate manifests and licenses for locally supplied test packs;
- detect files not represented in a manifest or manifest entries without files;
- test correct cache/download behavior and malicious archive cases;
- produce a machine-readable asset inventory for releases.

## History-preserving changes

Replacing an asset means publishing a new pack and changing one reviewed lockfile
entry. Removing a pack reference does not rewrite Git history or delete the old
release required to reproduce historical tags. If hosting must be migrated, the
same bytes can be mirrored and an updated provider/locator can be released with
matching hashes and documented provenance.

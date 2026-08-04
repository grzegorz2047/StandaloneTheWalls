# Sunderfront map package format v1

A `.twmap` file is a data-only ZIP archive. It cannot contain executable scripts,
plugins, native libraries, custom shaders, Java classes, or serialized Java
objects. The shared `map-format` module defines the semantic manifest, strict JSON
decoders, preparation gameplay metadata and an in-memory fail-closed bundle loader.

## Required archive members

Every archive contains exactly one root `manifest.json`. A v1 manifest declares
hashes for at least:

- `scene.glb` — visual glTF/GLB scene;
- `collision.glb` — server/client collision geometry;
- `gameplay.json` — preparation regions and authoritative spawn definitions;
- `thumbnail.webp` — browser preview;
- `licenses.json` — per-asset authorship, source and redistribution license.

All entry paths are lowercase portable relative paths. Absolute paths, directories,
backslashes, drive prefixes, empty segments and `.`/`..` segments are rejected.
Every declared member digest is canonical lowercase SHA-256.

## Minimal manifest shape

```json
{
  "schemaVersion": 1,
  "id": "citadel_divide",
  "name": "Citadel Divide",
  "author": "Sunderfront Team",
  "version": "1.0.0",
  "minimumPlayers": 4,
  "maximumPlayers": 40,
  "teamCount": 4,
  "playersPerTeam": 10,
  "requiredProtocol": {
    "major": 1,
    "minor": 0
  },
  "license": "CC0-1.0",
  "files": {
    "scene.glb": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "collision.glb": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "gameplay.json": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "thumbnail.webp": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "licenses.json": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  },
  "limits": {
    "archiveBytes": 52428800,
    "uncompressedBytes": 125829120,
    "fileCount": 32,
    "sceneNodes": 20000,
    "triangles": 500000,
    "textureDimension": 2048
  }
}
```

The example hashes illustrate shape only and must be replaced by real file digests.

## Compatibility policy

- `schemaVersion` must be exactly `1` in the first implementation.
- Unknown JSON fields, duplicate object keys, wrong token types and trailing JSON
  data are rejected rather than ignored.
- `version` is canonical Semantic Versioning text.
- v1 supports exactly two or four teams and at most 40 players.
- A map cannot expand engine hard caps through its `limits` object. Declared values
  are budgets checked again against the actual archive.
- The required protocol is represented locally rather than importing the protocol
  module, preserving the map-format module's dependency boundary.

## In-memory verification

`TwMapBundleLoader` accepts archive bytes plus an explicit `TwMapLoadPolicy`. It
returns `VerifiedMapBundle` only after all of the following succeed:

1. the archive and declared budgets fit both engine caps and the local policy;
2. every ZIP entry is a unique safe file path and the entry count is bounded;
3. `manifest.json` passes strict streaming JSON parsing and semantic validation;
4. the actual entry set exactly equals `manifest.json` plus the declared files;
5. total expanded bytes and the compression ratio remain bounded while streaming;
6. every declared member byte array matches its manifest SHA-256;
7. `gameplay.json` passes preparation layout validation.

The loader performs two streaming ZIP passes so it can validate the manifest before
retaining member bytes. Returned archive metadata and members are defensively
copied. No partially verified map object is returned after a failure.

This implementation is intentionally in-memory and applies a stricter 256 MiB
maximum expanded-byte ceiling. Large-map disk extraction and atomic cache
activation must use a separate bounded implementation rather than increasing this
ceiling or exposing temporary unverified files.

## Hard semantic caps in v1

| Budget | Maximum |
|---|---:|
| archive bytes | 512 MiB |
| uncompressed bytes | 1 GiB |
| files | 10,000 |
| scene nodes | 100,000 |
| triangles | 2,000,000 |
| texture dimension | 4,096 |

Manifest limits do not replace GLB validation. Actual node/triangle traversal,
texture inspection, collision semantics and atomic disk-cache activation remain
separate work.

## Validation result

Manifest semantic validation returns either an immutable typed `MapManifest` or a
list of bounded issues with stable JSON-style paths and codes: `REQUIRED`,
`FORMAT`, `RANGE`, `UNSUPPORTED`, `CONFLICT`, and `UNSAFE_PATH`. Archive loading
uses stable terminal `TwMapBundleException` codes and never executes map content.

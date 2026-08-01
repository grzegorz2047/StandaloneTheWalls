# Sunderfront map package format v1

A `.twmap` file is a data-only archive. It cannot contain executable scripts,
plugins, native libraries, custom shaders, Java classes, or serialized Java
objects. Loading and extraction are separate follow-up work; this document freezes
the first semantic `manifest.json` contract used by the shared validator.

## Required archive members

A v1 manifest declares hashes for at least:

- `scene.glb` — visual glTF/GLB scene;
- `collision.glb` — server/client collision geometry;
- `gameplay.json` — spawns, zones, walls, mining and deathmatch data;
- `thumbnail.webp` — browser preview;
- `licenses.json` — per-asset authorship, source and redistribution license.

All declared paths are lowercase portable relative paths. Absolute paths,
backslashes, drive prefixes, empty segments and `.`/`..` segments are rejected.
Every digest is canonical lowercase SHA-256.

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
- Unknown JSON fields and duplicate object keys are rejected by the future parser;
  accepting and ignoring them could hide typos or security-sensitive data.
- `version` is canonical Semantic Versioning text.
- v1 supports exactly two or four teams and at most 40 players.
- A map cannot expand engine hard caps through its `limits` object. The declared
  values are budgets that are checked again against actual archive and GLB data.
- The required protocol is represented locally rather than importing the protocol
  module, preserving the map-format module's dependency boundary.

## Hard semantic caps in v1

| Budget | Maximum |
|---|---:|
| archive bytes | 512 MiB |
| uncompressed bytes | 1 GiB |
| files | 10,000 |
| scene nodes | 100,000 |
| triangles | 2,000,000 |
| texture dimension | 4,096 |

These checks do not replace extraction protections. Zip-bomb ratio limits,
streaming extraction, per-entry sizes, actual GLB traversal, hash verification and
atomic cache activation belong to later work in epic #5.

## Validation result

Validation never executes map content. It returns either an immutable typed
`MapManifest` or a list of bounded issues with stable JSON-style paths and codes:
`REQUIRED`, `FORMAT`, `RANGE`, `UNSUPPORTED`, `CONFLICT`, and `UNSAFE_PATH`.

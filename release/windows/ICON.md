# Sunderfront Windows icon

The Windows application icon is generated deterministically by
`generate_sunderfront_icon.py` during release packaging.

- Origin: original geometry created for this repository; no external image, font,
  logo, game asset, or trademark artwork is embedded.
- Design: four separated wall blocks and one gold fracture, representing the
  four-team map and the opening of the walls.
- Format: one 256 x 256 RGBA PNG image embedded in an ICO container.
- Dependencies: Python standard library only.
- License: the same MIT License as this repository.
- Modifications: edit the generator and review the resulting hash; do not replace
  the output with an untracked binary downloaded from another source.

The generated `.ico` is a build artifact and is not committed. This keeps its
source reviewable and guarantees byte-for-byte regeneration.

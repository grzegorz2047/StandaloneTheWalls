# Instructions for AI agents and contributors

These rules are mandatory for every change.

1. Read `README.md`, `ARCHITECTURE.md`, `GAMEPLAY.md`, and all documentation relevant to the issue.
2. Inspect open pull requests before starting and avoid overlapping work.
3. Work on one issue at a time. Do not mix unrelated refactors into the same PR.
4. Treat the legacy Minecraft plugin as a source of gameplay evidence, never as a technical base to copy.
5. Keep `game-domain`, `shared`, `protocol`, and `map-format` independent of jMonkeyEngine and native windowing libraries.
6. The server is authoritative. Never accept client claims about position, hits, damage, inventory, resources, placement, crafting, death, teams, or match results without validation.
7. Do not use Java native object serialization for network messages or map data.
8. Maps are data-only archives. Never add executable Java, JavaScript, Python, native libraries, or map-provided shaders.
9. Do not add paid services, paid APIs, closed asset dependencies, or runtime requirements for a central creator-owned server.
10. Do not copy Minecraft models, textures, sounds, fonts, interface designs, logos, or other protected assets.
11. Verify redistribution licenses before adding binary assets. Record every asset in `assets/ASSET_MANIFEST.json` and `docs/THIRD_PARTY_ASSETS.md` when that document is introduced.
12. Do not ship placeholders as final content. Temporary diagnostic geometry must be clearly scoped to tests or tooling.
13. Add unit tests for new domain behavior and integration tests for changed boundaries.
14. Run the checks relevant to the change and report exactly what was and was not executed.
15. Update documentation when behavior, protocol, map schema, configuration, security assumptions, or operational steps change.
16. Do not claim performance, player capacity, stability, or security without a reproducible measurement.
17. Never commit secrets, passwords, tokens, private server addresses, personal data, local databases, generated runtime state, player private keys, or recovery material.
18. Avoid `System.out`, empty catch blocks, using `NullPointerException` for control flow, global mutable state, and test-hostile singletons.
19. Preserve compatibility intentionally. Version network and map contracts and document migrations.
20. Keep pull requests reviewable and leave the repository buildable.
21. Player identity keys belong only to Sunderfront. Never import, request, reuse, log, transmit, or inspect a user's SSH, PGP, cryptocurrency, or other unrelated private key.
22. Nicknames are not identities. Authenticate possession of the application-specific Ed25519 private key and keep nickname policy behind a server-side adapter.
23. A global handle claim must be self-signed by the claimed player key. A GitHub account, pull-request author, branch name, or commit signature alone is not proof of the in-game identity.
24. Global registry snapshots must be deterministic, signed by an accepted registry root, versioned, cached, rollback-aware, and verified before activation.
25. Do not commit large runtime maps, `.blend` sources, large `.glb` files, high-resolution textures, WAV masters, downloaded asset packs, or asset caches to the code repository. Follow `ASSET_PIPELINE.md` and use exact hashes.
26. Reproducible builds must not resolve mutable `latest` asset or registry URLs. Pin a version, immutable URL, expected size, and cryptographic hash.

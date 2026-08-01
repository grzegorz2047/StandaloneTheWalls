# Legacy behavior matrix

The old `grzegorz2047/TheWalls` plugin is gameplay evidence only. Bukkit/Spigot
code, Minecraft assets, external database APIs, and server-specific infrastructure
are not a technical foundation for this repository.

| Legacy behavior | Standalone equivalent | Difference outside Minecraft | Configurable | Do not copy |
|---|---|---|---|---|
| Four teams selected in the lobby | Green, Blue, Red, Yellow teams with explicit IDs | Dedicated lobby UI and server-side balancing | Team capacity, supported team count, auto-assignment policy | Command/inventory-item coupling and fragile size rules |
| Countdown followed by preparation, walls, combat and deathmatch | One deterministic match state machine | Phases are explicit domain states rather than Bukkit counter events | All phase durations within safe bounds | Split `GameStatus`/`CounterStatus` state and wall-clock/server-hour rules |
| Physical WorldGuard sectors and wall regions | Map-defined protected sectors and wall barriers | Full 3D collision and server validation replace region plugins | Sector geometry, wall animation and opening sequence | WorldGuard/WorldEdit dependency and hard-coded region names |
| Walls removed block-by-block | Animated gates, descending walls, or staged segments | Bounded work per tick and synchronized client event | Presentation and segment schedule | Synchronous triple loops setting thousands of blocks to air |
| Random valuable drops from stone | Data-driven mining profiles and visible deposits | Only designated mining zones are destructible | Chances, tools, quantities, profile per server/map | Mutable shuffled lists, off-by-one probability and client influence |
| Six classes: Warrior, Lumberjack, Miner, Archer, Cook, Alchemist | Six free data-defined roles | Original items become original game items and stations | Starting loadout and descriptions | Hard-coded Java inventories and stronger VIP variants |
| Damage disabled before game and friendly fire blocked | Phase-aware damage policy, friendly fire off by default | Server validates melee, projectiles, explosions and spectators | Friendly fire and allowed damage sources | Bukkit event cancellation as the only authority |
| Death changes the player to spectator | No-respawn elimination and team-only spectating | Reconnect grace and controlled avatar prevent combat logging | Drop policy and reconnect timeout | VIP-only spectator access and Bungee lobby dependency |
| Kill grants a diamond, money and experience | Optional bounded match-local reward | Reward uses original resources and cannot persist power | Reward type and amount | Double rewards by rank and persistent power economy |
| Permanent and temporary database shop | Crafting and fair match-local economy | Recipes and stations are versioned data | Recipes, station availability and match currency | Permanent purchased items, external ShopAPI and pay-to-win |
| Furnace ownership, limit three | Ownership/permissions for team stations and storage | Generalized entities rather than Minecraft furnace locations | Limits and team sharing policy | In-memory Bukkit `Location` keys as the durable model |
| Random copied Minecraft worlds with four start and four DM spawns | Validated `.twmap` rotation with explicit spawns | GLB scene/collision plus gameplay data | Rotation and supported player/team ranges | Hard-coded `/home/...` paths, random global state and direct directory copies |
| Vote to start early | Lobby readiness/early-start vote | UI action validated by server | Threshold and minimum players | Disabling normal rewards merely because a vote started the game |
| Polish/English messages stored in MySQL | Versioned local localization bundles | Client renders message keys and parameters | Server default and player language | External MessageAPI/database as a runtime requirement |

## Legacy elements explicitly rejected

- VIP or rank-based stronger kits, doubled rewards, reserved power slots, commands,
  spectator rights, or permanent items.
- MySQL and private `DatabaseAPI`, `ServersManager`, `AuthMe`, BungeeCord,
  WorldGuard, and WorldEdit runtime dependencies.
- Bukkit event/scheduler classes in domain logic.
- Global mutable world state, hard-coded machine paths, empty catch blocks,
  `NullPointerException` control flow, and `System.out` production logging.
- Minecraft models, textures, sounds, fonts, UI, branding, map files, or copied
  item names used as final original-game assets.

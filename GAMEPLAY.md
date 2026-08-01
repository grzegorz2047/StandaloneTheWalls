# Gameplay baseline

## Match loop

The target match loop is:

1. choose a local player name and server;
2. connect, negotiate protocol compatibility, and download a missing map;
3. enter the lobby and choose a team and class;
4. prepare inside a protected sector by gathering, crafting, sharing and building;
5. open the central walls and enable combat between teams;
6. move surviving players to the central deathmatch arena;
7. finish when one team remains alive, show results, and reset for another round.

The complete phase model will be implemented in issue #21. This document records
product rules, not current implementation status.

## Canonical teams

The four canonical teams are Green, Blue, Red, and Yellow, with a default capacity
of ten players each. Small matches may use a two-team map variant. Team assignment
must keep active team sizes within one player whenever possible.

## Preparation

During preparation, players cannot damage each other or cross central team
boundaries. The intended strategic options include resource development,
fortification, ambush preparation, shared logistics, mobility, defense, and
survival; no single mandatory objective should replace this combination.

## Legacy resource profile

A configurable legacy mining profile may award rare resources while ordinary rock
is mined. Initial reference chances are approximately 1% rare crystal, 4% energy
resource, 6% gold, 7% iron, 8% coal, and 20% experience/progression. The server owns
the random source and tests use deterministic seeds.

## Classes

The first complete game version contains Warrior, Lumberjack, Miner, Archer, Cook,
and Alchemist. Classes describe team roles and starting equipment. Every class is
available to every player; paid or rank-based stronger kits are forbidden.

## Elimination and victory

The standard mode has no respawn. A dead player becomes a non-interacting
spectator. Disconnecting cannot evade elimination and will later use a bounded
reconnect grace period. The primary victory condition is exactly one living team.

## Economy

All power-affecting resources, crafting, and purchases are scoped to the current
match. Persistent pay-to-win items, doubled rank rewards, VIP slots, and premium
combat or spectator permissions from the legacy server are not part of this game.

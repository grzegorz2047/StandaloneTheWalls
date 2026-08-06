import os
import runpy
from pathlib import Path

simulation = Path(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulation.java"
)
text = simulation.read_text(encoding="utf-8")
marker = """        private static PlayerState atSpawn(
                PreparationSpawnAssignment assignment,
                PreparationRegionBounds region,
                PreparationWorldBounds worldBounds,"""
if marker not in text:
    old = """        private static PlayerState atSpawn(
                PreparationSpawnAssignment assignment,
                PreparationRegionBounds region,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap) {
            return new PlayerState(
                    region,
                    supportMap,"""
    new = """        private static PlayerState atSpawn(
                PreparationSpawnAssignment assignment,
                PreparationRegionBounds region,
                PreparationWorldBounds worldBounds,
                PreparationSupportMap supportMap,
                PreparationObstacleMap obstacleMap) {
            return new PlayerState(
                    region,
                    worldBounds,
                    supportMap,"""
    if old not in text:
        raise SystemExit("PlayerState.atSpawn anchor not found")
    simulation.write_text(text.replace(old, new, 1), encoding="utf-8")

runtime = Path(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java"
)
runtime_text = runtime.read_text(encoding="utf-8")
runtime_marker = """        if (snapshot.phase() == MatchPhase.PREPARATION
                && !state.preparationTransitionAttempted) {"""
if runtime_marker not in runtime_text:
    old_runtime = """        if (snapshot.phase() == MatchPhase.PREPARATION) {
            if (!state.preparationTransitionAttempted) {
                state.preparationTransitionAttempted = true;
                publishPreparationTransition(state, snapshot);
            }
            return;
        }"""
    new_runtime = """        if (snapshot.phase() == MatchPhase.PREPARATION
                && !state.preparationTransitionAttempted) {
            state.preparationTransitionAttempted = true;
            publishPreparationTransition(state, snapshot);
            return;
        }"""
    if old_runtime not in runtime_text:
        raise SystemExit("preparation phase publication anchor not found")
    runtime.write_text(runtime_text.replace(old_runtime, new_runtime, 1), encoding="utf-8")

runpy.run_path("tools/issue_198_client_apply.py", run_name="__main__")
runpy.run_path("tools/issue_198_runtime_test_apply.py", run_name="__main__")

collision = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationCollisionWorld.java"
)
collision_text = collision.read_text(encoding="utf-8")
old_ray = """            if (!belongsToSupport(result.getGeometry())
                    && result.getDistance() <= distance + COLLISION_EPSILON) {"""
new_ray = """            if (blocks(result.getGeometry(), barrierPolicy)
                    && result.getDistance() <= distance + COLLISION_EPSILON) {"""
if old_ray in collision_text:
    collision.write_text(collision_text.replace(old_ray, new_ray, 1), encoding="utf-8")
elif new_ray not in collision_text:
    raise SystemExit("open-barrier raycast anchor not found")

hook = Path(".git/hooks/pre-commit")
hook.write_text(
    """#!/usr/bin/env bash
set -euo pipefail
git add \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationCollisionWorld.java \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationMovementController.java \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPlayerState.java \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPredictionHistory.java \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationSceneLoader.java \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/VerifiedPreparationScene.java \
  client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java \
  client/src/main/resources/i18n/messages_en.properties \
  client/src/main/resources/i18n/messages_pl.properties \
  client/src/test/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPredictionHistoryTest.java \
  map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/PreparationObstacleMapTest.java \
  map-format/src/test/java/pl/grzegorz2047/standalonethewalls/mapformat/PreparationWorldBoundsTest.java \
  server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java \
  server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulation.java \
  server/src/test/java/pl/grzegorz2047/standalonethewalls/server/identity/session/MinimalLobbyRuntimeTest.java \
  server/src/test/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationObstacleMovementSimulationTest.java
""",
    encoding="utf-8",
)
os.chmod(hook, 0o755)

from pathlib import Path

path = Path(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulation.java"
)
text = path.read_text(encoding="utf-8")
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
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java",
    """        PreparationMovementSimulation movement = state.preparationMovement;
        if (movement != null) {
            movement.remove(member.identity.playerId());
        }
""",
    """        PreparationMovementSimulation movement = state.preparationMovement;
        if (movement != null
                && movement.remove(member.identity.playerId())
                && movement.playerCount() == 0) {
            state.preparationMovement = null;
        }
""",
)

replace_once(
    "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulationTest.java",
    """        assertThat(simulation.remove(BRAVO)).isFalse();
    }

    @Test
    void rejectsWrongRoundUnknownPlayersAndNonMonotonicTicks() {
""",
    """        assertThat(simulation.remove(BRAVO)).isFalse();
        assertThat(simulation.remove(ALPHA)).isTrue();
        assertThat(simulation.playerCount()).isZero();
        assertThat(simulation.currentSnapshot()).isEmpty();
    }

    @Test
    void rejectsWrongRoundUnknownPlayersAndNonMonotonicTicks() {
""",
)

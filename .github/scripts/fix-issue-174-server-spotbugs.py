from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


path = "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java"

replace_once(
    path,
    """import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPlanner;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPublisher;
""",
    """import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPlanner;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPublishException;
import pl.grzegorz2047.standalonethewalls.server.preparation.PreparationTransitionPublisher;
""",
)

replace_once(
    path,
    """        try {
            PreparationTransitionPublisher.publish(
                    plan, matchSnapshot, preparationChannels(state), sendTimeout);
        } catch (RuntimeException exception) {
""",
    """        try {
            PreparationTransitionPublisher.publish(
                    plan, matchSnapshot, preparationChannels(state), sendTimeout);
        } catch (PreparationTransitionPublishException exception) {
""",
)

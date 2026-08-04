from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


client_path = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
)
client = client_path.read_text(encoding="utf-8")
client = replace_once(
    client,
    """        if (screen == Screen.PREPARATION) {
            if (preparationInput.captured() && event.getDX() != 0) {
                rotatePreparation(event.getDX());
            }
            return;
        }
""",
    """        if (screen == Screen.PREPARATION) {
            if (preparationInput.captured() && (event.getDX() != 0 || event.getDY() != 0)) {
                rotatePreparation(event.getDX(), event.getDY());
            }
            return;
        }
""",
    "route horizontal and vertical mouse deltas",
)
client = replace_once(
    client,
    """    private void rotatePreparation(double horizontalMousePixels) {
        PreparationPlayerState current = preparationPlayerState;
        if (current == null) {
            failPreparationSceneEntry();
            return;
        }
        PreparationPlayerState rotated =
                PreparationMovementController.rotate(current, horizontalMousePixels);
""",
    """    private void rotatePreparation(
            double horizontalMousePixels, double verticalMousePixels) {
        PreparationPlayerState current = preparationPlayerState;
        if (current == null) {
            failPreparationSceneEntry();
            return;
        }
        PreparationPlayerState rotated =
                PreparationMovementController.rotate(
                        current, horizontalMousePixels, verticalMousePixels);
""",
    "apply bounded yaw and pitch",
)
client_path.write_text(client, encoding="utf-8")

transition_test_path = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontPreparationTransitionTest.java"
)
test = transition_test_path.read_text(encoding="utf-8")
test = replace_once(
    test,
    """        assertThat(player.yawDegrees()).isEqualTo(45.0d);
        assertThat(client.isPreparationInputCaptured()).isFalse();
""",
    """        assertThat(player.yawDegrees()).isEqualTo(45.0d);
        assertThat(player.pitchDegrees()).isZero();
        assertThat(client.isPreparationInputCaptured()).isFalse();
""",
    "initial application pitch",
)
transition_test_path.write_text(test, encoding="utf-8")

from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


client_path = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
)
client = client_path.read_text(encoding="utf-8")

client = replace_once(
    client,
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationMovementController;\n",
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationMovementController;\n"
    "import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationMovementDiagnostics;\n",
    "movement diagnostics import",
)
client = replace_once(
    client,
    "    private BitmapFont font;\n",
    "    private BitmapFont font;\n"
    "    private BitmapText preparationDiagnosticsText;\n",
    "diagnostics text field",
)
client = replace_once(
    client,
    "    private PreparationCollisionWorld preparationCollisionWorld;\n"
    "    private PreparationPredictionHistory preparationPredictionHistory;\n",
    "    private PreparationCollisionWorld preparationCollisionWorld;\n"
    "    private PreparationMovementDiagnostics preparationMovementDiagnostics;\n"
    "    private PreparationPredictionHistory preparationPredictionHistory;\n",
    "diagnostics model field",
)
client = replace_once(
    client,
    "        if (screen == Screen.PREPARATION && !smokeMode) {\n"
    "            applyPreparationSnapshot();\n"
    "            updatePreparationRemotePlayers(timePerFrame);\n"
    "            updatePreparationMovement(timePerFrame);\n"
    "            submitPreparationInput(timePerFrame);\n"
    "        }\n",
    "        if (screen == Screen.PREPARATION && !smokeMode) {\n"
    "            advancePreparationMovementDiagnostics(timePerFrame);\n"
    "            applyPreparationSnapshot();\n"
    "            updatePreparationRemotePlayers(timePerFrame);\n"
    "            updatePreparationMovement(timePerFrame);\n"
    "            submitPreparationInput(timePerFrame);\n"
    "            refreshPreparationMovementDiagnosticsHud();\n"
    "        }\n",
    "preparation update loop",
)

old_movement = """    private void updatePreparationMovement(float timePerFrame) {
        if (!preparationInput.captured()) {
            return;
        }
        PreparationPlayerState current = preparationPlayerState;
        PreparationCollisionWorld collisions = preparationCollisionWorld;
        PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
        if (current == null
                || collisions == null
                || predictionHistory == null
                || !Float.isFinite(timePerFrame)
                || timePerFrame < 0.0f) {
            failPreparationSceneEntry();
            return;
        }
        try {
            PreparationPlayerState moved =
                    predictionHistory.predict(
                            current,
                            collisions,
                            nextPreparationInputSequence.get(),
                            preparationInput.forwardAxis(),
                            preparationInput.rightAxis(),
                            Math.min(
                                    timePerFrame,
                                    PreparationMovementController.MAXIMUM_STEP_SECONDS));
            if (moved != current) {
                preparationPlayerState = moved;
                PreparationCameraPlacement.apply(cam, moved);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            failPreparationSceneEntry();
        }
    }
"""
new_movement = """    private void updatePreparationMovement(float timePerFrame) {
        if (!preparationInput.captured()) {
            return;
        }
        PreparationPlayerState current = preparationPlayerState;
        PreparationCollisionWorld collisions = preparationCollisionWorld;
        PreparationMovementDiagnostics movementDiagnostics = preparationMovementDiagnostics;
        PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
        if (current == null
                || collisions == null
                || movementDiagnostics == null
                || predictionHistory == null
                || !Float.isFinite(timePerFrame)
                || timePerFrame < 0.0f) {
            failPreparationSceneEntry();
            return;
        }
        try {
            PreparationPlayerState moved =
                    predictionHistory.predict(
                            current,
                            collisions,
                            nextPreparationInputSequence.get(),
                            preparationInput.forwardAxis(),
                            preparationInput.rightAxis(),
                            Math.min(
                                    timePerFrame,
                                    PreparationMovementController.MAXIMUM_STEP_SECONDS));
            movementDiagnostics.observeLocalState(
                    predictionHistory.highestSubmittedSequence(),
                    predictionHistory.pendingStepCount());
            if (moved != current) {
                preparationPlayerState = moved;
                PreparationCameraPlacement.apply(cam, moved);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            failPreparationSceneEntry();
        }
    }
"""
client = replace_once(client, old_movement, new_movement, "movement integration")

old_snapshot_header = """        PreparationCollisionWorld collisions = preparationCollisionWorld;
        PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
        PreparationRemoteSnapshotInterpolator remoteInterpolator = preparationRemoteInterpolator;
        if (controller == null
                || current == null
                || localPlayerId == null
                || collisions == null
                || predictionHistory == null
                || remoteInterpolator == null) {
"""
new_snapshot_header = """        PreparationCollisionWorld collisions = preparationCollisionWorld;
        PreparationMovementDiagnostics movementDiagnostics = preparationMovementDiagnostics;
        PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
        PreparationRemoteSnapshotInterpolator remoteInterpolator = preparationRemoteInterpolator;
        if (controller == null
                || current == null
                || localPlayerId == null
                || collisions == null
                || movementDiagnostics == null
                || predictionHistory == null
                || remoteInterpolator == null) {
"""
client = replace_once(
    client, old_snapshot_header, new_snapshot_header, "snapshot diagnostics dependency"
)
old_reconcile = """            preparationPlayerState =
                    predictionHistory.reconcile(
                            authoritativeState,
                            collisions,
                            authoritative.lastProcessedInputSequence());
            PreparationCameraPlacement.apply(cam, preparationPlayerState);
            remoteInterpolator.offer(snapshot);
"""
new_reconcile = """            long acknowledgedSequence = authoritative.lastProcessedInputSequence();
            preparationPlayerState =
                    predictionHistory.reconcile(
                            authoritativeState, collisions, acknowledgedSequence);
            movementDiagnostics.acceptSnapshot(
                    snapshot.authoritativeTick(),
                    acknowledgedSequence,
                    predictionHistory.highestSubmittedSequence(),
                    predictionHistory.pendingStepCount());
            PreparationCameraPlacement.apply(cam, preparationPlayerState);
            remoteInterpolator.offer(snapshot);
"""
client = replace_once(client, old_reconcile, new_reconcile, "snapshot diagnostic update")

old_submit = """            PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
            if (predictionHistory == null) {
                failPreparationSceneEntry();
                return;
            }
            try {
                predictionHistory.markSubmitted(inputSequence);
                nextPreparationInputSequence.incrementAndGet();
            } catch (IllegalArgumentException exception) {
"""
new_submit = """            PreparationMovementDiagnostics movementDiagnostics =
                    preparationMovementDiagnostics;
            PreparationPredictionHistory predictionHistory = preparationPredictionHistory;
            if (movementDiagnostics == null || predictionHistory == null) {
                failPreparationSceneEntry();
                return;
            }
            try {
                predictionHistory.markSubmitted(inputSequence);
                movementDiagnostics.observeLocalState(
                        predictionHistory.highestSubmittedSequence(),
                        predictionHistory.pendingStepCount());
                nextPreparationInputSequence.incrementAndGet();
            } catch (IllegalArgumentException exception) {
"""
client = replace_once(client, old_submit, new_submit, "submitted input diagnostics")

client = replace_once(
    client,
    "            preparationPredictionHistory = new PreparationPredictionHistory();\n"
    "            preparationRemoteInterpolator =\n",
    "            preparationMovementDiagnostics = new PreparationMovementDiagnostics();\n"
    "            preparationPredictionHistory = new PreparationPredictionHistory();\n"
    "            preparationRemoteInterpolator =\n",
    "diagnostics lifecycle creation",
)
client = replace_once(
    client,
    "        preparationPlayerId = null;\n"
    "        preparationPredictionHistory = null;\n"
    "        preparationRemoteInterpolator = null;\n",
    "        preparationPlayerId = null;\n"
    "        preparationMovementDiagnostics = null;\n"
    "        preparationPredictionHistory = null;\n"
    "        preparationRemoteInterpolator = null;\n"
    "        preparationDiagnosticsText = null;\n",
    "diagnostics lifecycle cleanup",
)
client = replace_once(
    client,
    "        guiNode.detachAllChildren();\n"
    "        List<UiHitTarget> targets = new ArrayList<>();\n",
    "        guiNode.detachAllChildren();\n"
    "        preparationDiagnosticsText = null;\n"
    "        List<UiHitTarget> targets = new ArrayList<>();\n",
    "detached diagnostics text cleanup",
)

old_hud = """    private void renderPreparationHud() {
        String message =
                messages.text(
                        preparationInput.captured()
                                ? "preparation.controls.captured"
                                : "preparation.controls.capture");
        addCenteredText(message, 17f, MUTED_TEXT, 42f);
        if (preparationInput.captured()) {
            addCenteredText("+", 24f, PRIMARY_TEXT, cam.getHeight() / 2.0f);
        }
    }
"""
new_hud = """    private void renderPreparationHud() {
        String message =
                messages.text(
                        preparationInput.captured()
                                ? "preparation.controls.captured"
                                : "preparation.controls.capture");
        addCenteredText(message, 17f, MUTED_TEXT, 42f);
        if (preparationInput.captured()) {
            addCenteredText("+", 24f, PRIMARY_TEXT, cam.getHeight() / 2.0f);
        }
        PreparationMovementDiagnostics diagnostics = preparationMovementDiagnostics;
        if (diagnostics != null) {
            PreparationMovementDiagnostics.Snapshot snapshot = diagnostics.current();
            preparationDiagnosticsText =
                    addText(
                            preparationDiagnosticsLine(snapshot),
                            15f,
                            preparationDiagnosticsColor(snapshot.quality()),
                            0f,
                            cam.getHeight() - 28f);
            centerPreparationDiagnosticsText(preparationDiagnosticsText);
        }
    }

    private void advancePreparationMovementDiagnostics(float timePerFrame) {
        PreparationMovementDiagnostics diagnostics = preparationMovementDiagnostics;
        if (diagnostics == null || !Float.isFinite(timePerFrame) || timePerFrame < 0.0f) {
            failPreparationSceneEntry();
            return;
        }
        try {
            diagnostics.advanceFrame(
                    Math.min(
                            timePerFrame,
                            PreparationMovementDiagnostics.MAXIMUM_FRAME_SECONDS));
        } catch (IllegalArgumentException exception) {
            failPreparationSceneEntry();
        }
    }

    private void refreshPreparationMovementDiagnosticsHud() {
        PreparationMovementDiagnostics diagnostics = preparationMovementDiagnostics;
        BitmapText diagnosticsText = preparationDiagnosticsText;
        if (diagnostics == null || diagnosticsText == null) {
            return;
        }
        PreparationMovementDiagnostics.Snapshot snapshot = diagnostics.current();
        diagnosticsText.setText(preparationDiagnosticsLine(snapshot));
        diagnosticsText.setColor(preparationDiagnosticsColor(snapshot.quality()));
        centerPreparationDiagnosticsText(diagnosticsText);
    }

    private String preparationDiagnosticsLine(
            PreparationMovementDiagnostics.Snapshot snapshot) {
        String quality =
                messages.text(
                        "preparation.network.quality."
                                + snapshot.quality().name().toLowerCase(Locale.ROOT));
        String age =
                snapshot.snapshotAvailable()
                        ? Long.toString(snapshot.snapshotAgeMillis())
                        : messages.text("preparation.network.age.unavailable");
        return messages.text(
                "preparation.network.summary",
                quality,
                age,
                snapshot.acknowledgementLagInputs(),
                snapshot.pendingPredictionSteps());
    }

    private static ColorRGBA preparationDiagnosticsColor(
            PreparationMovementDiagnostics.Quality quality) {
        return switch (quality) {
            case WAITING -> MUTED_TEXT;
            case GOOD -> SUCCESS_TEXT;
            case DELAYED -> WARNING_TEXT;
            case STALE -> ERROR_TEXT;
        };
    }

    private void centerPreparationDiagnosticsText(BitmapText diagnosticsText) {
        float y = cam.getHeight() - 28f;
        diagnosticsText.setLocalTranslation(
                Math.max(20f, (cam.getWidth() - diagnosticsText.getLineWidth()) / 2f), y, 0f);
    }
"""
client = replace_once(client, old_hud, new_hud, "preparation diagnostics HUD")
client_path.write_text(client, encoding="utf-8")

for language, values in {
    "en": (
        "SYNC {0} | snapshot {1} ms | ACK lag {2} | prediction steps {3}",
        "WAITING",
        "GOOD",
        "DELAYED",
        "STALE",
    ),
    "pl": (
        "SYNC {0} | snapshot {1} ms | zaleglosc ACK {2} | kroki predykcji {3}",
        "OCZEKIWANIE",
        "DOBRA",
        "OPOZNIONA",
        "NIEAKTUALNA",
    ),
}.items():
    messages_path = Path(f"client/src/main/resources/i18n/messages_{language}.properties")
    messages = messages_path.read_text(encoding="utf-8")
    anchor = "preparation.controls.captured="
    lines = messages.splitlines()
    insertion_index = next(
        index + 1 for index, line in enumerate(lines) if line.startswith(anchor)
    )
    summary, waiting, good, delayed, stale = values
    new_lines = [
        f"preparation.network.summary={summary}",
        "preparation.network.age.unavailable=--",
        f"preparation.network.quality.waiting={waiting}",
        f"preparation.network.quality.good={good}",
        f"preparation.network.quality.delayed={delayed}",
        f"preparation.network.quality.stale={stale}",
    ]
    if any(line.startswith("preparation.network.") for line in lines):
        raise RuntimeError(f"messages_{language}: diagnostics keys already exist")
    lines[insertion_index:insertion_index] = new_lines
    messages_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

messages_test_path = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/i18n/ClientMessagesTest.java"
)
messages_test = messages_test_path.read_text(encoding="utf-8")
messages_test = replace_once(
    messages_test,
    "                    \"menu.help\");\n",
    "                    \"menu.help\");\n"
    "    private static final List<String> PREPARATION_NETWORK_KEYS =\n"
    "            List.of(\n"
    "                    \"preparation.network.summary\",\n"
    "                    \"preparation.network.age.unavailable\",\n"
    "                    \"preparation.network.quality.waiting\",\n"
    "                    \"preparation.network.quality.good\",\n"
    "                    \"preparation.network.quality.delayed\",\n"
    "                    \"preparation.network.quality.stale\");\n",
    "diagnostic localization key list",
)
messages_test = replace_once(
    messages_test,
    "    @Test\n"
    "    void failsLoudlyForUnknownKeysInsteadOfDisplayingTheKey() {\n",
    "    @Test\n"
    "    void providesLocalizedPreparationNetworkDiagnostics() {\n"
    "        for (ClientLanguage language : ClientLanguage.values()) {\n"
    "            ClientMessages messages = ClientMessages.forLanguage(language);\n"
    "            for (String key : PREPARATION_NETWORK_KEYS) {\n"
    "                String text =\n"
    "                        key.equals(\"preparation.network.summary\")\n"
    "                                ? messages.text(key, \"GOOD\", \"100\", 2L, 6)\n"
    "                                : messages.text(key);\n"
    "                assertFalse(text.isBlank());\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    @Test\n"
    "    void failsLoudlyForUnknownKeysInsteadOfDisplayingTheKey() {\n",
    "diagnostic localization test",
)
messages_test_path.write_text(messages_test, encoding="utf-8")

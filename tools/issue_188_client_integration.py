from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}: {old[:80]!r}")
    file.write_text(text.replace(old, new), encoding="utf-8")


CLIENT = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"

replace_once(
    CLIENT,
    '    private static final String INPUT_CROUCH = "sunderfront-crouch";\n'
    '    private static final double PREPARATION_INPUT_INTERVAL_SECONDS = 0.05d;\n',
    '    private static final String INPUT_CROUCH = "sunderfront-crouch";\n'
    '    private static final String INPUT_JUMP = "sunderfront-jump";\n'
    '    private static final double PREPARATION_INPUT_INTERVAL_SECONDS = 0.05d;\n',
)

replace_once(
    CLIENT,
    '    private volatile double preparationInputAccumulator;\n'
    '    private volatile int renderedWidth = -1;\n',
    '    private volatile double preparationInputAccumulator;\n'
    '    private volatile boolean pendingPreparationJump;\n'
    '    private volatile int renderedWidth = -1;\n',
)

replace_once(
    CLIENT,
    '        inputManager.addMapping(INPUT_CROUCH, new KeyTrigger(KeyInput.KEY_LCONTROL));\n'
    '        inputManager.addListener(\n',
    '        inputManager.addMapping(INPUT_CROUCH, new KeyTrigger(KeyInput.KEY_LCONTROL));\n'
    '        inputManager.addMapping(INPUT_JUMP, new KeyTrigger(KeyInput.KEY_SPACE));\n'
    '        inputManager.addListener(\n',
)

replace_once(
    CLIENT,
    '                INPUT_MOVE_RIGHT,\n'
    '                INPUT_SPRINT,\n'
    '                INPUT_CROUCH);\n',
    '                INPUT_MOVE_RIGHT,\n'
    '                INPUT_SPRINT,\n'
    '                INPUT_CROUCH,\n'
    '                INPUT_JUMP);\n',
)

replace_once(
    CLIENT,
    '            case INPUT_SPRINT -> preparationInput.setSprinting(pressed);\n'
    '            case INPUT_CROUCH -> updatePreparationCrouching(pressed);\n'
    '            case INPUT_SELECT -> {\n',
    '            case INPUT_SPRINT -> preparationInput.setSprinting(pressed);\n'
    '            case INPUT_CROUCH -> updatePreparationCrouching(pressed);\n'
    '            case INPUT_JUMP -> updatePreparationJumping(pressed);\n'
    '            case INPUT_SELECT -> {\n',
)

replace_once(
    CLIENT,
    '        if (inputManager != null) {\n'
    '            inputManager.setCursorVisible(true);\n'
    '        }\n'
    '        PreparationPlayerState current = preparationPlayerState;\n',
    '        pendingPreparationJump = false;\n'
    '        if (inputManager != null) {\n'
    '            inputManager.setCursorVisible(true);\n'
    '        }\n'
    '        PreparationPlayerState current = preparationPlayerState;\n',
)

replace_once(
    CLIENT,
    '    private void updatePreparationCrouching(boolean pressed) {\n'
    '        preparationInput.setCrouching(pressed);\n'
    '        PreparationPlayerState current = preparationPlayerState;\n'
    '        if (cam != null && current != null) {\n'
    '            PreparationCameraPlacement.apply(cam, current, preparationInput.crouching());\n'
    '        }\n'
    '    }\n\n'
    '    private void updatePreparationMovement(float timePerFrame) {\n',
    '    private void updatePreparationCrouching(boolean pressed) {\n'
    '        preparationInput.setCrouching(pressed);\n'
    '        if (pressed) {\n'
    '            pendingPreparationJump = false;\n'
    '        }\n'
    '        PreparationPlayerState current = preparationPlayerState;\n'
    '        if (cam != null && current != null) {\n'
    '            PreparationCameraPlacement.apply(cam, current, preparationInput.crouching());\n'
    '        }\n'
    '    }\n\n'
    '    private void updatePreparationJumping(boolean pressed) {\n'
    '        PreparationPlayerState current = preparationPlayerState;\n'
    '        preparationInput.setJumping(pressed, current != null && current.grounded());\n'
    '    }\n\n'
    '    private void updatePreparationMovement(float timePerFrame) {\n',
)

replace_once(
    CLIENT,
    '        try {\n'
    '            PreparationPlayerState moved =\n'
    '                    predictionHistory.predict(\n'
    '                            current,\n'
    '                            collisions,\n'
    '                            nextPreparationInputSequence.get(),\n'
    '                            preparationInput.forwardAxis(),\n'
    '                            preparationInput.rightAxis(),\n'
    '                            preparationInput.sprinting(),\n'
    '                            preparationInput.crouching(),\n'
    '                            Math.min(\n'
    '                                    timePerFrame,\n'
    '                                    PreparationMovementController.MAXIMUM_STEP_SECONDS));\n',
    '        try {\n'
    '            if (!pendingPreparationJump) {\n'
    '                pendingPreparationJump = preparationInput.consumeJumpRequest();\n'
    '            }\n'
    '            PreparationPlayerState moved =\n'
    '                    predictionHistory.predict(\n'
    '                            current,\n'
    '                            collisions,\n'
    '                            nextPreparationInputSequence.get(),\n'
    '                            preparationInput.forwardAxis(),\n'
    '                            preparationInput.rightAxis(),\n'
    '                            preparationInput.sprinting(),\n'
    '                            preparationInput.crouching(),\n'
    '                            pendingPreparationJump,\n'
    '                            Math.min(\n'
    '                                    timePerFrame,\n'
    '                                    PreparationMovementController.MAXIMUM_STEP_SECONDS));\n',
)

replace_once(
    CLIENT,
    '                            authoritative.zMetres(),\n'
    '                            authoritative.yawDegrees(),\n'
    '                            authoritative.pitchDegrees());\n',
    '                            authoritative.zMetres(),\n'
    '                            authoritative.verticalVelocityMetresPerSecond(),\n'
    '                            authoritative.grounded(),\n'
    '                            authoritative.yawDegrees(),\n'
    '                            authoritative.pitchDegrees());\n',
)

replace_once(
    CLIENT,
    '                        preparationInput.sprinting(),\n'
    '                        preparationInput.crouching(),\n'
    '                        quantizeYaw(current.yawDegrees()),\n',
    '                        preparationInput.sprinting(),\n'
    '                        preparationInput.crouching(),\n'
    '                        pendingPreparationJump,\n'
    '                        quantizeYaw(current.yawDegrees()),\n',
)

replace_once(
    CLIENT,
    '                nextPreparationInputSequence.incrementAndGet();\n'
    '            } catch (IllegalArgumentException exception) {\n',
    '                nextPreparationInputSequence.incrementAndGet();\n'
    '                pendingPreparationJump = false;\n'
    '            } catch (IllegalArgumentException exception) {\n',
)

replace_once(
    CLIENT,
    '            preparationInputAccumulator = 0.0d;\n'
    '            preparationMovementDiagnostics = new PreparationMovementDiagnostics();\n',
    '            preparationInputAccumulator = 0.0d;\n'
    '            pendingPreparationJump = false;\n'
    '            preparationMovementDiagnostics = new PreparationMovementDiagnostics();\n',
)

replace_once(
    CLIENT,
    '        preparationInputAccumulator = 0.0d;\n'
    '        PreparationRemotePlayerRenderer remotePlayers = preparationRemotePlayers;\n',
    '        preparationInputAccumulator = 0.0d;\n'
    '        pendingPreparationJump = false;\n'
    '        PreparationRemotePlayerRenderer remotePlayers = preparationRemotePlayers;\n',
)

replace_once(
    "client/src/main/resources/i18n/messages_en.properties",
    "preparation.controls.capture=Click or press Enter to capture controls. Esc disconnects. Left Shift sprints. Left Ctrl crouches.\n"
    "preparation.controls.captured=WASD moves. Mouse turns. Hold Left Shift to sprint and Left Ctrl to crouch. Esc releases the cursor.\n",
    "preparation.controls.capture=Click or press Enter to capture controls. Esc disconnects. Left Shift sprints. Left Ctrl crouches. Space jumps.\n"
    "preparation.controls.captured=WASD moves. Mouse turns. Hold Left Shift to sprint, Left Ctrl to crouch, and press Space to jump. Esc releases the cursor.\n",
)

replace_once(
    "client/src/main/resources/i18n/messages_pl.properties",
    "preparation.controls.capture=Kliknij lub nacisnij Enter, aby przejac sterowanie. Esc rozlacza. Lewy Shift: sprint. Lewy Ctrl: kucanie.\n"
    "preparation.controls.captured=WASD porusza. Mysz obraca. Przytrzymaj lewy Shift, aby sprintowac, i lewy Ctrl, aby kucac. Esc zwalnia kursor.\n",
    "preparation.controls.capture=Kliknij lub nacisnij Enter, aby przejac sterowanie. Esc rozlacza. Lewy Shift: sprint. Lewy Ctrl: kucanie. Spacja: skok.\n"
    "preparation.controls.captured=WASD porusza. Mysz obraca. Przytrzymaj lewy Shift, aby sprintowac, lewy Ctrl, aby kucac, i nacisnij Spacje, aby skoczyc. Esc zwalnia kursor.\n",
)

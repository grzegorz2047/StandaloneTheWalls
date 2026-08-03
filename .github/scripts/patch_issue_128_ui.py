from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")


def replace_count(path: Path, old: str, new: str, expected: int) -> None:
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != expected:
        raise RuntimeError(f"{path}: expected {expected} matches, found {count}")
    path.write_text(content.replace(old, new), encoding="utf-8")


controller = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/DirectConnectUiController.java"
)

replace_once(
    controller,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandOutcome;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;\n",
)
replace_once(
    controller,
    "    private final AtomicLong connectedRevision = new AtomicLong(-1L);\n"
    "    private final AtomicLong generation = new AtomicLong();\n",
    "    private final AtomicLong connectedRevision = new AtomicLong(-1L);\n"
    "    private final AtomicLong connectedMatchRevision = new AtomicLong(-1L);\n"
    "    private final AtomicLong generation = new AtomicLong();\n",
)
replace_count(
    controller,
    "allowedFocuses(model.phase())",
    "allowedFocuses(model)",
    2,
)
replace_count(
    controller,
    "        connectedRevision.set(-1L);\n",
    "        connectedRevision.set(-1L);\n        connectedMatchRevision.set(-1L);\n",
    6,
)
replace_once(
    controller,
    "    /** Called from the renderer update loop to publish newer immutable lobby snapshots. */\n"
    "    public void refreshConnectedSnapshot() {\n"
    "        requireOpen();\n"
    "        ConnectedLobbySession session = connectedSession;\n"
    "        if (session == null || model.phase() != DirectConnectUiPhase.CONNECTED) {\n"
    "            return;\n"
    "        }\n"
    "        LobbySnapshot snapshot = session.currentSnapshot();\n"
    "        boolean uiPending = pendingLobbyCommand != null;\n"
    "        boolean sessionPending = session.commandInFlight();\n"
    "        boolean busy = uiPending || sessionPending;\n"
    "        ConnectedLobbyScreenModel current = model.connectedLobby().orElseThrow();\n"
    "        boolean busyChanged = current.commandInFlight() != busy;\n"
    "        if (snapshot.revision() <= connectedRevision.get() && !busyChanged) {\n"
    "            return;\n"
    "        }\n"
    "        if (uiPending && !sessionPending) {\n"
    "            return;\n"
    "        }\n"
    "        if (snapshot.revision() > connectedRevision.get()) {\n"
    "            connectedRevision.set(snapshot.revision());\n"
    "        }\n"
    "        publish(connectedModel(snapshot, busy, lobbyCommandStatus));\n"
    "    }\n",
    "    /** Called from the renderer update loop to publish newer immutable lobby snapshots. */\n"
    "    public void refreshConnectedSnapshot() {\n"
    "        requireOpen();\n"
    "        ConnectedLobbySession session = connectedSession;\n"
    "        if (session == null || model.phase() != DirectConnectUiPhase.CONNECTED) {\n"
    "            return;\n"
    "        }\n"
    "        LobbySnapshot snapshot = session.currentSnapshot();\n"
    "        LobbyMatchPhaseSnapshot matchSnapshot = session.currentMatchSnapshot();\n"
    "        if (!snapshotsSynchronized(snapshot, matchSnapshot)) {\n"
    "            return;\n"
    "        }\n"
    "        boolean uiPending = pendingLobbyCommand != null;\n"
    "        boolean sessionPending = session.commandInFlight();\n"
    "        boolean busy = uiPending || sessionPending;\n"
    "        ConnectedLobbyScreenModel current = model.connectedLobby().orElseThrow();\n"
    "        boolean busyChanged = current.commandInFlight() != busy;\n"
    "        boolean rosterChanged = snapshot.revision() > connectedRevision.get();\n"
    "        boolean matchChanged = matchSnapshot.revision() > connectedMatchRevision.get();\n"
    "        if (!rosterChanged && !matchChanged && !busyChanged) {\n"
    "            return;\n"
    "        }\n"
    "        if (uiPending && !sessionPending) {\n"
    "            return;\n"
    "        }\n"
    "        publishConnectedIfSynchronized(session, snapshot, busy, lobbyCommandStatus);\n"
    "    }\n",
)
replace_once(
    controller,
    "    private void submitTeam(LobbyTeam team, ConnectedLobbyScreenModel connected) {\n"
    "        ConnectedLobbySession session = connectedSession;\n"
    "        if (session == null) {\n"
    "            transitionClosedLobby();\n"
    "            return;\n"
    "        }\n"
    "        if (!connected.controlsEnabled()) {\n"
    "            publishBusyStatus(session);\n"
    "            return;\n"
    "        }\n"
    "        submitLobbyCommand(session, session.selectTeam(team));\n"
    "    }\n",
    "    private void submitTeam(LobbyTeam team, ConnectedLobbyScreenModel connected) {\n"
    "        ConnectedLobbySession session = connectedSession;\n"
    "        if (session == null) {\n"
    "            transitionClosedLobby();\n"
    "            return;\n"
    "        }\n"
    "        if (!connected.match().lobbyControlsAllowed()) {\n"
    "            publishLockedStatus(session);\n"
    "            return;\n"
    "        }\n"
    "        if (connected.commandInFlight()) {\n"
    "            publishBusyStatus(session);\n"
    "            return;\n"
    "        }\n"
    "        submitLobbyCommand(session, session.selectTeam(team));\n"
    "    }\n",
)
replace_once(
    controller,
    "    private void submitReady(ConnectedLobbyScreenModel connected) {\n"
    "        ConnectedLobbySession session = connectedSession;\n"
    "        if (session == null) {\n"
    "            transitionClosedLobby();\n"
    "            return;\n"
    "        }\n"
    "        if (!connected.controlsEnabled()) {\n"
    "            publishBusyStatus(session);\n"
    "            return;\n"
    "        }\n"
    "        boolean currentlyReady = connected.lobby().ownMember().orElseThrow().ready();\n"
    "        submitLobbyCommand(session, session.setReady(!currentlyReady));\n"
    "    }\n",
    "    private void submitReady(ConnectedLobbyScreenModel connected) {\n"
    "        ConnectedLobbySession session = connectedSession;\n"
    "        if (session == null) {\n"
    "            transitionClosedLobby();\n"
    "            return;\n"
    "        }\n"
    "        if (!connected.match().lobbyControlsAllowed()) {\n"
    "            publishLockedStatus(session);\n"
    "            return;\n"
    "        }\n"
    "        if (connected.commandInFlight()) {\n"
    "            publishBusyStatus(session);\n"
    "            return;\n"
    "        }\n"
    "        boolean currentlyReady = connected.lobby().ownMember().orElseThrow().ready();\n"
    "        submitLobbyCommand(session, session.setReady(!currentlyReady));\n"
    "    }\n",
)
replace_count(
    controller,
    "publish(connectedModel(session.currentSnapshot(), true, lobbyCommandStatus));",
    "publishConnectedIfSynchronized(\n                    session, session.currentSnapshot(), true, lobbyCommandStatus);",
    2,
)
replace_once(
    controller,
    "    private void publishBusyStatus(ConnectedLobbySession session) {\n"
    "        lobbyCommandStatus = messages.text(\"direct.lobby.command.busy\");\n"
    "        publishConnectedIfSynchronized(\n"
    "                    session, session.currentSnapshot(), true, lobbyCommandStatus);\n"
    "    }\n",
    "    private void publishBusyStatus(ConnectedLobbySession session) {\n"
    "        lobbyCommandStatus = messages.text(\"direct.lobby.command.busy\");\n"
    "        publishConnectedIfSynchronized(\n"
    "                session, session.currentSnapshot(), true, lobbyCommandStatus);\n"
    "    }\n"
    "\n"
    "    private void publishLockedStatus(ConnectedLobbySession session) {\n"
    "        lobbyCommandStatus = messages.text(\"direct.lobby.command.match_already_started\");\n"
    "        publishConnectedIfSynchronized(\n"
    "                session, session.currentSnapshot(), false, lobbyCommandStatus);\n"
    "    }\n",
)
replace_once(
    controller,
    "            case LobbyCommandResolution.Completed completed -> {\n"
    "                LobbySnapshot snapshot = completed.snapshot();\n"
    "                connectedRevision.set(snapshot.revision());\n"
    "                lobbyCommandStatus = commandOutcomeMessage(completed.result().outcome());\n"
    "                publish(connectedModel(snapshot, false, lobbyCommandStatus));\n"
    "            }\n",
    "            case LobbyCommandResolution.Completed completed -> {\n"
    "                LobbySnapshot snapshot = completed.snapshot();\n"
    "                connectedRevision.set(snapshot.revision());\n"
    "                lobbyCommandStatus = commandOutcomeMessage(completed.result().outcome());\n"
    "                publishConnectedIfSynchronized(\n"
    "                        expectedSession, snapshot, false, lobbyCommandStatus);\n"
    "            }\n",
)
replace_once(
    controller,
    "                LobbySnapshot snapshot = transferred.currentSnapshot();\n"
    "                connectedRevision.set(snapshot.revision());\n"
    "                lobbyCommandStatus = messages.text(\"direct.lobby.command.idle\");\n"
    "                ConnectedLobbyModel lobby =\n"
    "                        ConnectedLobbyModel.from(snapshot, Optional.of(transferred.playerId()));\n"
    "                focus = focusForTeam(lobby.ownMember().orElseThrow().team());\n"
    "                publish(connectedModel(snapshot, false, lobbyCommandStatus));\n",
    "                LobbySnapshot snapshot = transferred.currentSnapshot();\n"
    "                LobbyMatchPhaseSnapshot matchSnapshot = transferred.currentMatchSnapshot();\n"
    "                connectedRevision.set(snapshot.revision());\n"
    "                connectedMatchRevision.set(matchSnapshot.revision());\n"
    "                lobbyCommandStatus = messages.text(\"direct.lobby.command.idle\");\n"
    "                ConnectedLobbyModel lobby =\n"
    "                        ConnectedLobbyModel.from(snapshot, Optional.of(transferred.playerId()));\n"
    "                focus =\n"
    "                        matchSnapshot.phase() == LobbyMatchPhase.PREPARATION\n"
    "                                ? DirectConnectUiFocus.PRIMARY_ACTION\n"
    "                                : focusForTeam(lobby.ownMember().orElseThrow().team());\n"
    "                publish(connectedModel(snapshot, matchSnapshot, false, lobbyCommandStatus));\n",
)
replace_once(
    controller,
    "    private DirectConnectScreenModel connectedModel(\n"
    "            LobbySnapshot snapshot, boolean commandInFlight, String commandStatus) {\n"
    "        ConnectedLobbySession session =\n"
    "                Objects.requireNonNull(connectedSession, \"connected session\");\n"
    "        ConnectedLobbyModel lobby =\n"
    "                ConnectedLobbyModel.from(snapshot, Optional.of(session.playerId()));\n"
    "        boolean ready = lobby.ownMember().orElseThrow().ready();\n"
    "        ConnectedLobbyScreenModel connected =\n"
    "                new ConnectedLobbyScreenModel(\n"
    "                        lobby,\n"
    "                        commandInFlight,\n"
    "                        messages.text(\n"
    "                                ready\n"
    "                                        ? \"direct.lobby.action.not_ready\"\n"
    "                                        : \"direct.lobby.action.ready\"),\n"
    "                        commandStatus);\n"
    "        return new DirectConnectScreenModel(\n"
    "                DirectConnectUiPhase.CONNECTED,\n"
    "                focus,\n"
    "                endpointText,\n"
    "                handleText,\n"
    "                messages.text(\"direct.lobby.title\"),\n"
    "                messages.text(\"direct.status.connected\"),\n"
    "                messages.text(\"direct.lobby.members\", lobby.totalMembers()),\n"
    "                messages.text(\"direct.action.disconnect\"),\n"
    "                messages.text(\"direct.action.menu\"),\n"
    "                true,\n"
    "                true,\n"
    "                Optional.empty(),\n"
    "                Optional.of(connected));\n"
    "    }\n",
    "    private DirectConnectScreenModel connectedModel(\n"
    "            LobbySnapshot snapshot,\n"
    "            LobbyMatchPhaseSnapshot matchSnapshot,\n"
    "            boolean commandInFlight,\n"
    "            String commandStatus) {\n"
    "        if (!snapshotsSynchronized(snapshot, matchSnapshot)) {\n"
    "            throw new IllegalArgumentException(\n"
    "                    \"connected roster and match snapshot must be synchronized\");\n"
    "        }\n"
    "        ConnectedLobbySession session =\n"
    "                Objects.requireNonNull(connectedSession, \"connected session\");\n"
    "        ConnectedLobbyModel lobby =\n"
    "                ConnectedLobbyModel.from(snapshot, Optional.of(session.playerId()));\n"
    "        ConnectedLobbyMatchModel match = connectedMatchModel(matchSnapshot);\n"
    "        if (!match.lobbyControlsAllowed() && isLobbyControlFocus(focus)) {\n"
    "            focus = DirectConnectUiFocus.PRIMARY_ACTION;\n"
    "        }\n"
    "        boolean ready = lobby.ownMember().orElseThrow().ready();\n"
    "        ConnectedLobbyScreenModel connected =\n"
    "                new ConnectedLobbyScreenModel(\n"
    "                        lobby,\n"
    "                        match,\n"
    "                        commandInFlight,\n"
    "                        messages.text(\n"
    "                                ready\n"
    "                                        ? \"direct.lobby.action.not_ready\"\n"
    "                                        : \"direct.lobby.action.ready\"),\n"
    "                        commandStatus);\n"
    "        return new DirectConnectScreenModel(\n"
    "                DirectConnectUiPhase.CONNECTED,\n"
    "                focus,\n"
    "                endpointText,\n"
    "                handleText,\n"
    "                messages.text(\"direct.lobby.title\"),\n"
    "                messages.text(\"direct.status.connected\"),\n"
    "                messages.text(\"direct.lobby.members\", lobby.totalMembers()),\n"
    "                messages.text(\"direct.action.disconnect\"),\n"
    "                messages.text(\"direct.action.menu\"),\n"
    "                true,\n"
    "                true,\n"
    "                Optional.empty(),\n"
    "                Optional.of(connected));\n"
    "    }\n"
    "\n"
    "    private ConnectedLobbyMatchModel connectedMatchModel(\n"
    "            LobbyMatchPhaseSnapshot snapshot) {\n"
    "        String status =\n"
    "                switch (snapshot.phase()) {\n"
    "                    case WAITING_FOR_PLAYERS -> messages.text(\"direct.lobby.phase.waiting\");\n"
    "                    case START_COUNTDOWN ->\n"
    "                            messages.text(\n"
    "                                    \"direct.lobby.phase.countdown\", snapshot.ticksRemaining());\n"
    "                    case PREPARATION -> messages.text(\"direct.lobby.phase.preparation\");\n"
    "                };\n"
    "        Optional<String> cancellation =\n"
    "                switch (snapshot.cancellationReason()) {\n"
    "                    case NONE -> Optional.empty();\n"
    "                    case INSUFFICIENT_PLAYERS ->\n"
    "                            Optional.of(\n"
    "                                    messages.text(\n"
    "                                            \"direct.lobby.countdown_cancelled.insufficient_players\"));\n"
    "                    case LOBBY_NOT_READY ->\n"
    "                            Optional.of(\n"
    "                                    messages.text(\n"
    "                                            \"direct.lobby.countdown_cancelled.lobby_not_ready\"));\n"
    "                };\n"
    "        return new ConnectedLobbyMatchModel(\n"
    "                snapshot.revision(),\n"
    "                snapshot.authoritativeTick(),\n"
    "                snapshot.phase(),\n"
    "                snapshot.ticksRemaining(),\n"
    "                status,\n"
    "                cancellation);\n"
    "    }\n"
    "\n"
    "    private boolean publishConnectedIfSynchronized(\n"
    "            ConnectedLobbySession session,\n"
    "            LobbySnapshot snapshot,\n"
    "            boolean commandInFlight,\n"
    "            String commandStatus) {\n"
    "        LobbyMatchPhaseSnapshot matchSnapshot = session.currentMatchSnapshot();\n"
    "        if (!snapshotsSynchronized(snapshot, matchSnapshot)) {\n"
    "            return false;\n"
    "        }\n"
    "        connectedRevision.set(snapshot.revision());\n"
    "        connectedMatchRevision.set(matchSnapshot.revision());\n"
    "        publish(connectedModel(snapshot, matchSnapshot, commandInFlight, commandStatus));\n"
    "        return true;\n"
    "    }\n"
    "\n"
    "    private static boolean snapshotsSynchronized(\n"
    "            LobbySnapshot roster, LobbyMatchPhaseSnapshot match) {\n"
    "        return match.rosterRevision() == roster.revision()\n"
    "                && match.connectedPlayers() == roster.members().size();\n"
    "    }\n"
    "\n"
    "    private static boolean isLobbyControlFocus(DirectConnectUiFocus candidate) {\n"
    "        return candidate == DirectConnectUiFocus.TEAM_RED\n"
    "                || candidate == DirectConnectUiFocus.TEAM_BLUE\n"
    "                || candidate == DirectConnectUiFocus.TEAM_GREEN\n"
    "                || candidate == DirectConnectUiFocus.TEAM_YELLOW\n"
    "                || candidate == DirectConnectUiFocus.READY_ACTION;\n"
    "    }\n",
)
replace_once(
    controller,
    "    private static List<DirectConnectUiFocus> allowedFocuses(DirectConnectUiPhase phase) {\n"
    "        return switch (phase) {\n",
    "    private static List<DirectConnectUiFocus> allowedFocuses(\n"
    "            DirectConnectScreenModel model) {\n"
    "        return switch (model.phase()) {\n",
)
replace_once(
    controller,
    "            case CONNECTED ->\n"
    "                    List.of(\n"
    "                            DirectConnectUiFocus.TEAM_RED,\n"
    "                            DirectConnectUiFocus.TEAM_BLUE,\n"
    "                            DirectConnectUiFocus.TEAM_GREEN,\n"
    "                            DirectConnectUiFocus.TEAM_YELLOW,\n"
    "                            DirectConnectUiFocus.READY_ACTION,\n"
    "                            DirectConnectUiFocus.PRIMARY_ACTION,\n"
    "                            DirectConnectUiFocus.SECONDARY_ACTION);\n",
    "            case CONNECTED -> {\n"
    "                ConnectedLobbyScreenModel connected = model.connectedLobby().orElseThrow();\n"
    "                if (!connected.match().lobbyControlsAllowed()) {\n"
    "                    yield List.of(\n"
    "                            DirectConnectUiFocus.PRIMARY_ACTION,\n"
    "                            DirectConnectUiFocus.SECONDARY_ACTION);\n"
    "                }\n"
    "                yield List.of(\n"
    "                        DirectConnectUiFocus.TEAM_RED,\n"
    "                        DirectConnectUiFocus.TEAM_BLUE,\n"
    "                        DirectConnectUiFocus.TEAM_GREEN,\n"
    "                        DirectConnectUiFocus.TEAM_YELLOW,\n"
    "                        DirectConnectUiFocus.READY_ACTION,\n"
    "                        DirectConnectUiFocus.PRIMARY_ACTION,\n"
    "                        DirectConnectUiFocus.SECONDARY_ACTION);\n"
    "            }\n",
)

renderer = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
)
replace_once(
    renderer,
    "        String unassigned =\n"
    "                connected.lobby().unassignedMembers().isEmpty()\n"
    "                        ? messages.text(\"direct.lobby.unassigned.none\")\n"
    "                        : connected.lobby().unassignedMembers().stream()\n"
    "                                .map(this::memberLine)\n"
    "                                .collect(Collectors.joining(\", \"));\n"
    "        addCenteredText(\n"
    "                messages.text(\"direct.lobby.unassigned\", unassigned), 14f, MUTED_TEXT, 210f);\n"
    "        addCenteredText(\n"
    "                connected.commandStatus(),\n"
    "                15f,\n"
    "                connected.commandInFlight() ? WARNING_TEXT : MUTED_TEXT,\n"
    "                178f);\n"
    "\n"
    "        BitmapText ready =\n"
    "                addText(\n"
    "                        actionLabel(\n"
    "                                connected.readyAction(),\n"
    "                                screenModel.focus() == DirectConnectUiFocus.READY_ACTION,\n"
    "                                connected.controlsEnabled()),\n"
    "                        21f,\n"
    "                        actionColor(\n"
    "                                screenModel.focus() == DirectConnectUiFocus.READY_ACTION,\n"
    "                                connected.controlsEnabled()),\n"
    "                        0f,\n"
    "                        135f);\n"
    "        ready.setLocalTranslation(Math.max(20f, (width - ready.getLineWidth()) / 2f), 135f, 0f);\n",
    "        addCenteredText(\n"
    "                connected.match().status(),\n"
    "                16f,\n"
    "                connected.match().lobbyControlsAllowed() ? MUTED_TEXT : WARNING_TEXT,\n"
    "                244f);\n"
    "        connected.match().cancellationMessage()\n"
    "                .ifPresent(message -> addCenteredText(message, 14f, WARNING_TEXT, 220f));\n"
    "\n"
    "        String unassigned =\n"
    "                connected.lobby().unassignedMembers().isEmpty()\n"
    "                        ? messages.text(\"direct.lobby.unassigned.none\")\n"
    "                        : connected.lobby().unassignedMembers().stream()\n"
    "                                .map(this::memberLine)\n"
    "                                .collect(Collectors.joining(\", \"));\n"
    "        addCenteredText(\n"
    "                messages.text(\"direct.lobby.unassigned\", unassigned), 14f, MUTED_TEXT, 194f);\n"
    "        addCenteredText(\n"
    "                connected.commandStatus(),\n"
    "                15f,\n"
    "                connected.commandInFlight() ? WARNING_TEXT : MUTED_TEXT,\n"
    "                164f);\n"
    "\n"
    "        BitmapText ready =\n"
    "                addText(\n"
    "                        actionLabel(\n"
    "                                connected.readyAction(),\n"
    "                                screenModel.focus() == DirectConnectUiFocus.READY_ACTION,\n"
    "                                connected.controlsEnabled()),\n"
    "                        21f,\n"
    "                        actionColor(\n"
    "                                screenModel.focus() == DirectConnectUiFocus.READY_ACTION,\n"
    "                                connected.controlsEnabled()),\n"
    "                        0f,\n"
    "                        124f);\n"
    "        ready.setLocalTranslation(Math.max(20f, (width - ready.getLineWidth()) / 2f), 124f, 0f);\n",
)

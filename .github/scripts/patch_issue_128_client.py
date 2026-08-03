from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    content = file_path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}")
    file_path.write_text(content.replace(old, new, 1), encoding="utf-8")


service = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/network/DirectConnectService.java"
replace_once(
    service,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;\n",
)
replace_once(
    service,
    "                LobbySnapshot initialSnapshot = receiveInitialSnapshot(authenticated, joined);\n"
    "                if (initialSnapshot == null) {\n"
    "                    return;\n"
    "                }\n"
    "                try {\n",
    "                LobbySnapshot initialSnapshot = receiveInitialSnapshot(authenticated, joined);\n"
    "                if (initialSnapshot == null) {\n"
    "                    return;\n"
    "                }\n"
    "                LobbyMatchPhaseSnapshot initialMatchSnapshot =\n"
    "                        receiveInitialMatchSnapshot(authenticated, initialSnapshot);\n"
    "                if (initialMatchSnapshot == null) {\n"
    "                    return;\n"
    "                }\n"
    "                try {\n",
)
replace_once(
    service,
    "                        new ConnectedLobbySession(\n"
    "                                authenticated,\n"
    "                                initialSnapshot,\n"
    "                                session -> connected.compareAndSet(session, null));\n",
    "                        new ConnectedLobbySession(\n"
    "                                authenticated,\n"
    "                                initialSnapshot,\n"
    "                                initialMatchSnapshot,\n"
    "                                session -> connected.compareAndSet(session, null));\n",
)
replace_once(
    service,
    "        private ProtocolEnvelope receive(\n"
    "                AuthenticatedReliableSession authenticated,\n",
    "        private LobbyMatchPhaseSnapshot receiveInitialMatchSnapshot(\n"
    "                AuthenticatedReliableSession authenticated, LobbySnapshot initialSnapshot) {\n"
    "            ProtocolEnvelope envelope =\n"
    "                    receive(\n"
    "                            authenticated,\n"
    "                            DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_TIMEOUT,\n"
    "                            DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_MALFORMED);\n"
    "            if (envelope == null) {\n"
    "                return null;\n"
    "            }\n"
    "            if (envelope.messageType() != MessageType.LOBBY_MATCH_SNAPSHOT) {\n"
    "                completeFailure(DirectConnectFailureCode.UNEXPECTED_MESSAGE);\n"
    "                return null;\n"
    "            }\n"
    "            try {\n"
    "                LobbyMatchPhaseSnapshot matchSnapshot =\n"
    "                        LobbyMatchProtocolCodec.decodeSnapshot(envelope.payload());\n"
    "                if (matchSnapshot.rosterRevision() != initialSnapshot.revision()\n"
    "                        || matchSnapshot.connectedPlayers()\n"
    "                                != initialSnapshot.members().size()) {\n"
    "                    completeFailure(\n"
    "                            DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_ROSTER_MISMATCH);\n"
    "                    return null;\n"
    "                }\n"
    "                return matchSnapshot;\n"
    "            } catch (LobbyProtocolException exception) {\n"
    "                completeFailure(DirectConnectFailureCode.LOBBY_MATCH_SNAPSHOT_MALFORMED);\n"
    "                return null;\n"
    "            }\n"
    "        }\n"
    "\n"
    "        private ProtocolEnvelope receive(\n"
    "                AuthenticatedReliableSession authenticated,\n",
)

session_test = "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbySessionTest.java"
replace_once(
    session_test,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;\n",
)
replace_once(
    session_test,
    "                new ConnectedLobbySession(\n"
    "                        transport, snapshot(1L, HANDLE), ignored -> releases.incrementAndGet());\n",
    "                new ConnectedLobbySession(\n"
    "                        transport,\n"
    "                        snapshot(1L, HANDLE),\n"
    "                        matchSnapshot(1L, 1L, 1),\n"
    "                        ignored -> releases.incrementAndGet());\n",
)
replace_once(
    session_test,
    "        return new ConnectedLobbySession(transport, initial, ignored -> {});\n",
    "        return new ConnectedLobbySession(\n"
    "                transport,\n"
    "                initial,\n"
    "                matchSnapshot(1L, initial.revision(), initial.members().size()),\n"
    "                ignored -> {});\n",
)
replace_once(
    session_test,
    "    private static LobbySnapshot snapshot(long revision, CanonicalHandle handle) {\n",
    "    private static LobbyMatchPhaseSnapshot matchSnapshot(\n"
    "            long revision, long rosterRevision, int connectedPlayers) {\n"
    "        return new LobbyMatchPhaseSnapshot(\n"
    "                revision,\n"
    "                rosterRevision,\n"
    "                LobbyMatchPhaseSnapshot.BEFORE_FIRST_TICK,\n"
    "                LobbyMatchPhase.WAITING_FOR_PLAYERS,\n"
    "                0L,\n"
    "                connectedPlayers,\n"
    "                1L,\n"
    "                LobbyCountdownCancellationReason.NONE);\n"
    "    }\n"
    "\n"
    "    private static LobbySnapshot snapshot(long revision, CanonicalHandle handle) {\n",
)

fixtures = "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/network/DirectConnectUiTestFixtures.java"
replace_once(
    fixtures,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
)
replace_once(
    fixtures,
    "        return new ConnectedLobbySession(authenticated, initialSnapshot, ignored -> {});\n",
    "        return new ConnectedLobbySession(\n"
    "                authenticated,\n"
    "                initialSnapshot,\n"
    "                initialMatchSnapshot(initialSnapshot),\n"
    "                ignored -> {});\n",
)
replace_once(
    fixtures,
    "    private static void start(ConnectedLobbySession session) {\n",
    "    private static LobbyMatchPhaseSnapshot initialMatchSnapshot(LobbySnapshot roster) {\n"
    "        return new LobbyMatchPhaseSnapshot(\n"
    "                1L,\n"
    "                roster.revision(),\n"
    "                LobbyMatchPhaseSnapshot.BEFORE_FIRST_TICK,\n"
    "                LobbyMatchPhase.WAITING_FOR_PLAYERS,\n"
    "                0L,\n"
    "                roster.members().size(),\n"
    "                1L,\n"
    "                LobbyCountdownCancellationReason.NONE);\n"
    "    }\n"
    "\n"
    "    private static void start(ConnectedLobbySession session) {\n",
)

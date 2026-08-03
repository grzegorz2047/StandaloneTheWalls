from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")


fixtures = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/network/DirectConnectUiTestFixtures.java"
)
replace_once(
    fixtures,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
)
replace_once(
    fixtures,
    "        public void deliverSnapshot(LobbySnapshot snapshot, long sequence) {\n"
    "            channel.deliver(\n"
    "                    new ProtocolEnvelope(\n"
    "                            ProtocolVersion.CURRENT,\n"
    "                            MessageType.LOBBY_SNAPSHOT,\n"
    "                            SESSION_ID,\n"
    "                            sequence,\n"
    "                            LobbyProtocolCodec.encodeSnapshot(snapshot)));\n"
    "        }\n"
    "\n"
    "        public void deliverEof() {\n",
    "        public void deliverSnapshot(LobbySnapshot snapshot, long sequence) {\n"
    "            channel.deliver(\n"
    "                    new ProtocolEnvelope(\n"
    "                            ProtocolVersion.CURRENT,\n"
    "                            MessageType.LOBBY_SNAPSHOT,\n"
    "                            SESSION_ID,\n"
    "                            sequence,\n"
    "                            LobbyProtocolCodec.encodeSnapshot(snapshot)));\n"
    "        }\n"
    "\n"
    "        public void deliverMatchSnapshot(\n"
    "                LobbyMatchPhaseSnapshot snapshot, long sequence) {\n"
    "            channel.deliver(\n"
    "                    new ProtocolEnvelope(\n"
    "                            ProtocolVersion.CURRENT,\n"
    "                            MessageType.LOBBY_MATCH_SNAPSHOT,\n"
    "                            SESSION_ID,\n"
    "                            sequence,\n"
    "                            LobbyMatchProtocolCodec.encodeSnapshot(snapshot)));\n"
    "        }\n"
    "\n"
    "        public void deliverEof() {\n",
)

ui_test = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/ConnectedLobbyUiCommandTest.java"
)
replace_once(
    ui_test,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyProtocolCodec;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbySnapshot;\n",
)
replace_once(
    ui_test,
    "        lobby.deliverSnapshot(\n"
    "                DirectConnectUiTestFixtures.snapshot(\n"
    "                        2L, LobbyTeam.GREEN, false, LobbyTeam.BLUE, false),\n"
    "                2L);\n",
    "        LobbySnapshot teamRoster =\n"
    "                DirectConnectUiTestFixtures.snapshot(\n"
    "                        2L, LobbyTeam.GREEN, false, LobbyTeam.BLUE, false);\n"
    "        lobby.deliverSnapshot(teamRoster, 2L);\n"
    "        lobby.deliverMatchSnapshot(waitingMatchSnapshot(2L, teamRoster), 3L);\n",
)
replace_once(
    ui_test,
    "        lobby.deliverResult(new LobbyCommandResult(2L, 3L, LobbyCommandOutcome.APPLIED), 3L);\n"
    "        lobby.deliverSnapshot(\n"
    "                DirectConnectUiTestFixtures.snapshot(\n"
    "                        3L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, false),\n"
    "                4L);\n",
    "        lobby.deliverResult(new LobbyCommandResult(2L, 3L, LobbyCommandOutcome.APPLIED), 4L);\n"
    "        LobbySnapshot readyRoster =\n"
    "                DirectConnectUiTestFixtures.snapshot(\n"
    "                        3L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, false);\n"
    "        lobby.deliverSnapshot(readyRoster, 5L);\n"
    "        lobby.deliverMatchSnapshot(waitingMatchSnapshot(3L, readyRoster), 6L);\n",
)
replace_once(
    ui_test,
    "        lobby.deliverResult(new LobbyCommandResult(3L, 3L, LobbyCommandOutcome.NO_CHANGE), 5L);\n",
    "        lobby.deliverResult(new LobbyCommandResult(3L, 3L, LobbyCommandOutcome.NO_CHANGE), 7L);\n",
)
replace_once(
    ui_test,
    "        lobby.deliverSnapshot(\n"
    "                DirectConnectUiTestFixtures.snapshot(\n"
    "                        4L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, true),\n"
    "                6L);\n",
    "        LobbySnapshot externalRoster =\n"
    "                DirectConnectUiTestFixtures.snapshot(\n"
    "                        4L, LobbyTeam.GREEN, true, LobbyTeam.BLUE, true);\n"
    "        lobby.deliverSnapshot(externalRoster, 8L);\n"
    "        lobby.deliverMatchSnapshot(waitingMatchSnapshot(4L, externalRoster), 9L);\n",
)
replace_once(
    ui_test,
    "        long requestId = 4L;\n"
    "        long sequence = 7L;\n",
    "        long requestId = 4L;\n"
    "        long sequence = 10L;\n",
)
replace_once(
    ui_test,
    "    private static void waitUntil(java.util.function.BooleanSupplier condition)\n",
    "    private static LobbyMatchPhaseSnapshot waitingMatchSnapshot(\n"
    "            long revision, LobbySnapshot roster) {\n"
    "        return new LobbyMatchPhaseSnapshot(\n"
    "                revision,\n"
    "                roster.revision(),\n"
    "                revision - 1L,\n"
    "                LobbyMatchPhase.WAITING_FOR_PLAYERS,\n"
    "                0L,\n"
    "                roster.members().size(),\n"
    "                1L,\n"
    "                LobbyCountdownCancellationReason.NONE);\n"
    "    }\n"
    "\n"
    "    private static void waitUntil(java.util.function.BooleanSupplier condition)\n",
)

server_test = Path(
    "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/ServerLauncherTest.java"
)
replace_once(
    server_test,
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyJoined;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchProtocolCodec;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
)
replace_once(
    server_test,
    "            assertEquals(java.util.List.of(joined.self()), snapshot.members());\n"
    "\n"
    "            send(\n",
    "            assertEquals(java.util.List.of(joined.self()), snapshot.members());\n"
    "            LobbyMatchPhaseSnapshot initialMatch = receiveMatchSnapshot(authenticated);\n"
    "            assertEquals(snapshot.revision(), initialMatch.rosterRevision());\n"
    "            assertEquals(snapshot.members().size(), initialMatch.connectedPlayers());\n"
    "            assertEquals(LobbyMatchPhase.WAITING_FOR_PLAYERS, initialMatch.phase());\n"
    "\n"
    "            send(\n",
)
replace_once(
    server_test,
    "            assertEquals(2L, teamSnapshot.revision());\n"
    "\n"
    "            send(\n",
    "            assertEquals(2L, teamSnapshot.revision());\n"
    "            LobbyMatchPhaseSnapshot teamMatch = receiveMatchSnapshot(authenticated);\n"
    "            assertEquals(teamSnapshot.revision(), teamMatch.rosterRevision());\n"
    "            assertEquals(LobbyMatchPhase.WAITING_FOR_PLAYERS, teamMatch.phase());\n"
    "\n"
    "            send(\n",
)
replace_once(
    server_test,
    "            assertEquals(3L, readySnapshot.revision());\n"
    "\n"
    "            authenticated\n",
    "            assertEquals(3L, readySnapshot.revision());\n"
    "            LobbyMatchPhaseSnapshot readyMatch = receiveMatchSnapshot(authenticated);\n"
    "            assertEquals(readySnapshot.revision(), readyMatch.rosterRevision());\n"
    "            assertEquals(LobbyMatchPhase.WAITING_FOR_PLAYERS, readyMatch.phase());\n"
    "\n"
    "            authenticated\n",
)
replace_once(
    server_test,
    "    private static ProtocolEnvelope receive(AuthenticatedReliableSession session)\n",
    "    private static LobbyMatchPhaseSnapshot receiveMatchSnapshot(\n"
    "            AuthenticatedReliableSession session)\n"
    "            throws InterruptedException,\n"
    "                    ExecutionException,\n"
    "                    TimeoutException,\n"
    "                    LobbyProtocolException {\n"
    "        ProtocolEnvelope envelope = receive(session);\n"
    "        assertEquals(MessageType.LOBBY_MATCH_SNAPSHOT, envelope.messageType());\n"
    "        return LobbyMatchProtocolCodec.decodeSnapshot(envelope.payload());\n"
    "    }\n"
    "\n"
    "    private static ProtocolEnvelope receive(AuthenticatedReliableSession session)\n",
)

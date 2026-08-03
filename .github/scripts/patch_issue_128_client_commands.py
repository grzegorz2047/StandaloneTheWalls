from pathlib import Path


path = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/network/ConnectedLobbyCommandTest.java"
)
content = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global content
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"expected exactly one match, found {count}")
    content = content.replace(old, new, 1)


replace_once(
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCommandResult;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyCountdownCancellationReason;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhase;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMatchPhaseSnapshot;\n"
    "import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyMember;\n",
)
replace_once(
    "        return new ConnectedLobbySession(transport, initial, released);\n",
    "        return new ConnectedLobbySession(\n"
    "                transport,\n"
    "                initial,\n"
    "                initialMatchSnapshot(initial),\n"
    "                released);\n",
)
replace_once(
    "    private static LobbySnapshot snapshot(long revision) {\n",
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
    "    private static LobbySnapshot snapshot(long revision) {\n",
)

path.write_text(content, encoding="utf-8")

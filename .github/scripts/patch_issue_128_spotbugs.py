from pathlib import Path


def replace_exact(path: Path, old: str, new: str, expected: int = 1) -> None:
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != expected:
        raise RuntimeError(f"{path}: expected {expected} matches, found {count}")
    path.write_text(content.replace(old, new), encoding="utf-8")


runtime = Path(
    "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/lobby/MinimalLobbyRuntime.java"
)
replace_exact(runtime, "    private final MatchConfiguration matchConfiguration;\n", "")
replace_exact(
    runtime,
    "        this.matchConfiguration = Objects.requireNonNull(matchConfiguration, \"matchConfiguration\");\n",
    "        MatchConfiguration lifecycleConfiguration =\n"
    "                Objects.requireNonNull(matchConfiguration, \"matchConfiguration\");\n",
)
replace_exact(
    runtime,
    "        matchCoordinator = new LobbyMatchCoordinator(configuration, matchConfiguration);\n",
    "        matchCoordinator = new LobbyMatchCoordinator(configuration, lifecycleConfiguration);\n",
)

protocol_test = Path(
    "protocol/src/test/java/pl/grzegorz2047/standalonethewalls/protocol/lobby/LobbyMatchProtocolCodecTest.java"
)
replace_exact(
    protocol_test,
    " throws Exception {",
    " throws LobbyProtocolException {",
    expected=2,
)

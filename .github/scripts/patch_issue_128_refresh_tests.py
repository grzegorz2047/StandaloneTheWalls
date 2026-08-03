from pathlib import Path


path = Path(
    "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/ui/directconnect/ConnectedLobbyUiCommandTest.java"
)
content = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global content
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"expected exactly one match, found {count}")
    content = content.replace(old, new, 1)


replace_once(
    "        waitUntil(\n"
    "                () ->\n"
    "                        connected(controller).lobby().revision() == 2L\n"
    "                                && connected(controller).controlsEnabled());\n",
    "        waitUntil(\n"
    "                () -> {\n"
    "                    controller.refreshConnectedSnapshot();\n"
    "                    return connected(controller).lobby().revision() == 2L\n"
    "                            && connected(controller).controlsEnabled();\n"
    "                });\n",
)
replace_once(
    "        waitUntil(\n"
    "                () ->\n"
    "                        connected(controller).lobby().revision() == 3L\n"
    "                                && connected(controller).controlsEnabled());\n",
    "        waitUntil(\n"
    "                () -> {\n"
    "                    controller.refreshConnectedSnapshot();\n"
    "                    return connected(controller).lobby().revision() == 3L\n"
    "                            && connected(controller).controlsEnabled();\n"
    "                });\n",
)

path.write_text(content, encoding="utf-8")

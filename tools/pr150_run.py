from pathlib import Path
import subprocess

source_path = Path("tools/pr150_apply.py")
source = source_path.read_text(encoding="utf-8")
source = source.replace(
    "menu.help=Uzyj strzalek gora/dol i Enter. Esc zamyka gre.\\n",
    "menu.help=Strzalki gora/dol i Enter. Esc: koniec.\\n",
)
exec(compile(source, str(source_path), "exec"), {"__name__": "__main__"})

client_path = Path(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
)
client = client_path.read_text(encoding="utf-8")
old = """    private void returnToStartMenu() {
        screen = Screen.START_MENU;
        menuStatus = "";
        preparationTransitionGate = new PreparationTransitionGate();
        preparationPlayerState = null;
        if (!smokeMode && inputManager != null) {
"""
new = """    private void returnToStartMenu() {
        screen = Screen.START_MENU;
        menuStatus = "";
        preparationTransitionGate = new PreparationTransitionGate();
        preparationPlayerState = null;
        detachPreparationWorld();
        if (!smokeMode && inputManager != null) {
"""
old_count = client.count(old)
new_count = client.count(new)
if old_count == 1 and new_count == 0:
    client = client.replace(old, new, 1)
elif old_count != 0 or new_count != 1:
    raise SystemExit(
        "return-to-menu world detach: expected one patched or one unpatched block, "
        f"found old={old_count}, new={new_count}"
    )
client_path.write_text(client, encoding="utf-8")

subprocess.run(
    [
        "./gradlew",
        "--no-daemon",
        "--no-configuration-cache",
        ":client:dependencies",
        "--write-locks",
        "--write-verification-metadata",
        "sha256",
    ],
    check=True,
)

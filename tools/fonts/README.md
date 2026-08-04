# UI font generation

The client uses a project-owned BMFont derived from Andika Regular 6.200 under the SIL Open Font License 1.1.

GitHub Actions downloads the official `silnrsi/font-andika` release, verifies the pinned source SHA-256, generates the atlas with pinned Pillow/fontTools versions, and passes six bounded Base64 chunks to the build jobs as a workflow artifact. Generated chunks are not committed.

For a local build:

```bash
python -m pip install --requirement tools/fonts/requirements.txt
python tools/fonts/install_sunderfront_ui_font.py
./gradlew check
```

On Windows, run the same commands with the Python launcher and Gradle wrapper appropriate for the shell, for example `py -3` and `gradlew.bat`.

The installation helper writes ignored chunks under `client/src/main/resources/Interface/Fonts/`. Gradle reconstructs the PNG and verifies its encoded length, decoded size, PNG CRCs, and SHA-256 before packaging it.

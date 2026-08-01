# Sunderfront client bootstrap

This module contains the first real jMonkeyEngine client process and start screen.
It is not yet a playable game and does not connect to a server.

## Start screen

The display client opens at 1280x720 with VSync enabled and a resizable window.
The first menu contains:

- Play;
- Settings;
- Exit.

Play and Settings currently show a localized unavailable message. They do not
open placeholder workflows. Exit closes the application. The menu is controlled
with Up/Down, Enter, and Escape.

The menu model and localization are renderer-independent, so their behavior can
be tested without OpenGL.

## Language

The client supports `en` and `pl` bundles and selects Polish only when the system
locale language is `pl`; all other locales use English. Override it explicitly:

```bash
./gradlew :client:run --args="--lang pl"
```

Unknown localization keys fail instead of appearing as raw keys in the UI.

### Temporary font limitation

jMonkeyEngine's bundled `Interface/Fonts/Default.fnt` declares an ASCII charset
and does not cover the complete Polish alphabet. Until issue #40 supplies a
redistributable PL/EN font through the verified asset pipeline, the initial
Polish start-screen copy is intentionally limited to printable ASCII. This is a
temporary compatibility constraint, not the final localization style.

The game must not depend on operating-system fonts and must not silently replace
missing glyphs.

## Headless smoke mode

The same application lifecycle has a bounded headless initialization path:

```bash
./gradlew :client:run --args="--lang en --smoke"
```

Smoke mode creates a jMonkeyEngine headless context, loads the localization and
menu model, waits up to 20 seconds for initialization, then stops the context. It
does not initialize display-only font or GUI resources.

A successful smoke proves only client bootstrap and resource initialization. It
is not evidence that the desktop renderer, final font, gameplay, networking, or
asset packs work.

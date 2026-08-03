# ADR 0036: Direct Connect UI renderer boundary

- Status: Accepted
- Date: 2026-08-03
- Decision owners: Sunderfront maintainers
- Related: #40, #86, #88, #89, #90, #91

## Context

ADR 0035 defines a production Direct Connect operation whose DNS, persistence,
socket, TLS, signing, admission, and lobby work runs outside the jMonkeyEngine
renderer thread. The client still needs a user-visible flow for editing an
endpoint and handle, explicitly confirming first-use server identity, observing
connection progress, entering the minimal lobby, and leaving safely.

A presentation layer that reads transport internals, parses exception strings,
or mutates jMonkeyEngine scene objects from completion threads would reintroduce
blocking and race conditions at the final integration boundary. Cancellation and
screen changes also create stale callbacks that must not overwrite newer state or
leak a successfully connected session.

## Decision

### Immutable presentation state

`DirectConnectUiController` owns one immutable `DirectConnectScreenModel`. The
model contains only bounded presentation data: phase, focus, edited endpoint and
handle, localized status/detail text, action labels, optional public fingerprint,
and an immutable lobby member snapshot.

The phases are deliberately disjoint:

- form;
- resolving and TCP connecting;
- TLS securing;
- player authentication;
- policy admission;
- lobby joining;
- first-use identity confirmation;
- changed-identity security alert;
- admission rejection;
- ordinary failure;
- connected lobby;
- disconnected.

Changed or unexpectedly replaced server identity is not an ordinary retryable
network error. It has a separate alarming phase with no trust action. The default
Enter action returns to the form; trust replacement remains outside this alpha.

### Renderer ownership

Network callbacks receive only stable progress stages and terminal results. Every
callback is submitted through `UiDispatcher`; the jMonkeyEngine adapter implements
that boundary with `enqueue`. The observer that renders models therefore executes
only on the renderer owner.

The controller never performs DNS, persistence, socket I/O, TLS, cryptography, or
blocking protocol work. Potentially blocking cancellation, session close, and
service shutdown are initiated on named virtual threads. The render loop may read
`ConnectedLobbySession.currentSnapshot()` because it is immutable and non-blocking.

### Stale callback protection

Every connection or confirmation attempt captures a monotonically increasing UI
generation. Cancel, disconnect, close, or a newer attempt advances the generation.
A callback from an older generation cannot publish state. If such a callback
contains a connected lobby session, that session is closed asynchronously rather
than leaked.

First-use Escape or Cancel calls `discardPendingConfirmation()` and returns to the
form without reconnecting or writing trust. Enter on the explicit confirmation
action starts the second exact-pin connection defined by ADR 0035.

### Keyboard-first interaction

The alpha UI is fully operable with keyboard input:

- Tab and arrow keys move focus;
- Enter activates the focused action;
- Backspace edits the focused field;
- Escape cancels the current attempt, rejects first-use confirmation, disconnects
  an owned lobby, or returns to the previous screen.

The form validates canonical `host:port` and `[a-z0-9_]{3,24}` handle input before
starting network work. The renderer uses the existing bitmap font, so Polish copy
remains ASCII until the font work tracked by #40 is delivered.

### Connected lobby presentation

Connected means the production service already completed trusted TLS, Identity
Proof V2, accepted policy admission, `LOBBY_JOINED`, and the first exact-self lobby
snapshot. The screen displays connection status, the requested handle, and all
members from the immutable snapshot. Later revisions are adopted only through the
fail-closed `ConnectedLobbySession` receiver.

## Consequences

### Positive

- Renderer input and drawing remain responsive during DNS, TLS, proof, timeout,
  server shutdown, cancellation, and session close.
- The UI cannot accidentally trust a first-use or changed server identity.
- Stable codes and admission statuses have complete English and Polish mappings;
  raw exceptions never reach the screen.
- Stale completion callbacks cannot overwrite a newer form or lobby.
- The same controller is testable headlessly and through a real `ServerLauncher`
  loopback without bypassing production Direct Connect.

### Negative

- The alpha presentation is intentionally text-based and keyboard-first.
- Polish diacritics remain unavailable until #40 supplies the required font.
- Snapshot polling occurs from `simpleUpdate`; event-driven rendering can replace
  it later without changing the immutable model contract.
- Trust replacement, reconnect, server profiles, discovery, and gameplay remain
  separate work.

## Verification

The implementation requires:

- headless state-machine tests for validation, progress, TOFU, security alert,
  admission rejection, timeout, cancellation, disconnection, and stale callbacks;
- headless jMonkeyEngine navigation smoke for Start -> Direct Connect -> Back;
- complete localization coverage for every public failure and admission status;
- a real `ServerLauncher` loopback proving that network callbacks are queued and
  the UI observer executes only on its owner thread.

## Follow-up

- #91 packages the client and server and repeats this path from unpacked release
  artifacts for `v0.1.0-alpha.1`.
- #40 supplies production font coverage and allows native Polish diacritics.
- Reconnect, server discovery, profile storage, mouse-first widgets, and gameplay
  remain outside Direct Connect Alpha.

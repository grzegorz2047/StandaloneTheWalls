from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
""",
    """import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
""",
)

replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """    private PlayerId preparationPlayerId;
    private long preparationRoundNumber;
    private long nextPreparationInputSequence = 1L;
    private long appliedPreparationSnapshotTick = -1L;
    private double preparationInputAccumulator;
""",
    """    private PlayerId preparationPlayerId;
    private volatile long preparationRoundNumber;
    private final AtomicLong nextPreparationInputSequence = new AtomicLong(1L);
    private volatile long appliedPreparationSnapshotTick = -1L;
    private volatile double preparationInputAccumulator;
""",
)

replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """        if (nextPreparationInputSequence == Long.MAX_VALUE) {
            failPreparationSceneEntry();
            return;
        }
        PreparationInput input =
                new PreparationInput(
                        preparationRoundNumber,
                        nextPreparationInputSequence,
                        quantizeAxis(preparationInput.forwardAxis()),
                        quantizeAxis(preparationInput.rightAxis()),
                        quantizeYaw(current.yawDegrees()),
                        quantizePitch(current.pitchDegrees()));
        if (controller.submitPreparationInput(input)) {
            nextPreparationInputSequence++;
        }
""",
    """        long inputSequence = nextPreparationInputSequence.get();
        if (inputSequence == Long.MAX_VALUE) {
            failPreparationSceneEntry();
            return;
        }
        PreparationInput input =
                new PreparationInput(
                        preparationRoundNumber,
                        inputSequence,
                        quantizeAxis(preparationInput.forwardAxis()),
                        quantizeAxis(preparationInput.rightAxis()),
                        quantizeYaw(current.yawDegrees()),
                        quantizePitch(current.pitchDegrees()));
        if (controller.submitPreparationInput(input)) {
            nextPreparationInputSequence.incrementAndGet();
        }
""",
)

replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """            nextPreparationInputSequence = 1L;
            appliedPreparationSnapshotTick = -1L;
""",
    """            nextPreparationInputSequence.set(1L);
            appliedPreparationSnapshotTick = -1L;
""",
)

replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """        nextPreparationInputSequence = 1L;
        appliedPreparationSnapshotTick = -1L;
""",
    """        nextPreparationInputSequence.set(1L);
        appliedPreparationSnapshotTick = -1L;
""",
)

replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationRemotePlayerRenderer.java",
    """    public Node root() {
        return root;
    }

    public void apply(PreparationWorldSnapshot snapshot, PlayerId localPlayerId) {
""",
    """    public void attachTo(Node parent) {
        Objects.requireNonNull(parent, \"parent\").attachChild(root);
    }

    public void apply(PreparationWorldSnapshot snapshot, PlayerId localPlayerId) {
""",
)

replace_once(
    "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java",
    """            rootNode.attachChild(loadedWorld);
            rootNode.attachChild(preparationRemotePlayers.root());
""",
    """            rootNode.attachChild(loadedWorld);
            preparationRemotePlayers.attachTo(rootNode);
""",
)

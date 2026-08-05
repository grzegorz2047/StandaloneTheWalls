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
    """    private PlayerId preparationPlayerId;
    private long preparationRoundNumber;
    private long nextPreparationInputSequence = 1L;
    private long appliedPreparationSnapshotTick = -1L;
    private double preparationInputAccumulator;
""",
    """    private PlayerId preparationPlayerId;
    private volatile long preparationRoundNumber;
    private volatile long nextPreparationInputSequence = 1L;
    private volatile long appliedPreparationSnapshotTick = -1L;
    private volatile double preparationInputAccumulator;
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

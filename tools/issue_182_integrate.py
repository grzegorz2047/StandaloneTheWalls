from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def split_top_level_arguments(inner: str) -> list[str]:
    arguments: list[str] = []
    start = 0
    round_depth = 0
    square_depth = 0
    brace_depth = 0
    quote: str | None = None
    escaped = False
    for index, character in enumerate(inner):
        if quote is not None:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character in ('"', "'"):
            quote = character
        elif character == "(":
            round_depth += 1
        elif character == ")":
            round_depth -= 1
        elif character == "[":
            square_depth += 1
        elif character == "]":
            square_depth -= 1
        elif character == "{":
            brace_depth += 1
        elif character == "}":
            brace_depth -= 1
        elif (
            character == ","
            and round_depth == 0
            and square_depth == 0
            and brace_depth == 0
        ):
            arguments.append(inner[start:index].strip())
            start = index + 1
    arguments.append(inner[start:].strip())
    return arguments


def matching_parenthesis(text: str, opening: int) -> int:
    depth = 1
    quote: str | None = None
    escaped = False
    for index in range(opening + 1, len(text)):
        character = text[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character in ('"', "'"):
            quote = character
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
            if depth == 0:
                return index
    raise RuntimeError("unterminated PreparationInput constructor")


def migrate_preparation_input_constructors() -> int:
    target = "new PreparationInput("
    migrated = 0
    for path in ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        replacements: list[tuple[int, int, str]] = []
        cursor = 0
        while True:
            start = text.find(target, cursor)
            if start < 0:
                break
            opening = start + len(target) - 1
            closing = matching_parenthesis(text, opening)
            arguments = split_top_level_arguments(text[opening + 1 : closing])
            if len(arguments) == 6:
                arguments.insert(4, "false")
                replacements.append((opening + 1, closing, ", ".join(arguments)))
                migrated += 1
            elif len(arguments) != 7:
                raise RuntimeError(
                    f"{path}: PreparationInput constructor has {len(arguments)} arguments"
                )
            cursor = closing + 1
        for start, end, replacement in reversed(replacements):
            text = text[:start] + replacement + text[end:]
        if replacements:
            path.write_text(text, encoding="utf-8")
    if migrated < 1:
        raise RuntimeError("no PreparationInput constructors were migrated")
    return migrated


# Protocol value object.
path = "protocol/src/main/java/pl/grzegorz2047/standalonethewalls/protocol/preparation/PreparationInput.java"
text = read(path)
text = replace_once(
    text,
    "        int rightAxis,\n        int yawCentidegrees,\n",
    "        int rightAxis,\n        boolean sprinting,\n        int yawCentidegrees,\n",
    "PreparationInput sprint field",
)
write(path, text)

# Strict input schema v2 while snapshots remain schema v1.
path = "protocol/src/main/java/pl/grzegorz2047/standalonethewalls/protocol/preparation/PreparationMovementProtocolCodec.java"
text = read(path)
text = replace_once(text, "public static final int INPUT_BYTES = 23;", "public static final int INPUT_BYTES = 24;", "input size")
text = replace_once(
    text,
    "    private static final int SCHEMA_VERSION = 1;\n",
    "    private static final int INPUT_SCHEMA_VERSION = 2;\n"
    "    private static final int SNAPSHOT_SCHEMA_VERSION = 1;\n"
    "    private static final int INPUT_FLAG_SPRINT = 1;\n"
    "    private static final int KNOWN_INPUT_FLAGS = INPUT_FLAG_SPRINT;\n",
    "codec schema constants",
)
text = replace_once(
    text,
    "        return ByteBuffer.allocate(INPUT_BYTES)\n"
    "                .put((byte) SCHEMA_VERSION)\n"
    "                .putLong(value.roundNumber())\n"
    "                .putLong(value.sequence())\n"
    "                .put((byte) value.forwardAxis())\n"
    "                .put((byte) value.rightAxis())\n"
    "                .putShort((short) value.yawCentidegrees())\n",
    "        return ByteBuffer.allocate(INPUT_BYTES)\n"
    "                .put((byte) INPUT_SCHEMA_VERSION)\n"
    "                .putLong(value.roundNumber())\n"
    "                .putLong(value.sequence())\n"
    "                .put((byte) value.forwardAxis())\n"
    "                .put((byte) value.rightAxis())\n"
    "                .put((byte) (value.sprinting() ? INPUT_FLAG_SPRINT : 0))\n"
    "                .putShort((short) value.yawCentidegrees())\n",
    "encode sprint flag",
)
text = replace_once(
    text,
    "        requireSchema(input.get());\n"
    "        long roundNumber = requireRoundNumber(input.getLong());\n"
    "        long sequence = requirePositiveSequence(input.getLong());\n"
    "        int forwardAxis = requireAxis(input.get());\n"
    "        int rightAxis = requireAxis(input.get());\n"
    "        int yawCentidegrees = requireYaw(input.getShort());\n"
    "        int pitchCentidegrees = requirePitch(input.getShort());\n"
    "        return new PreparationInput(\n"
    "                roundNumber, sequence, forwardAxis, rightAxis, yawCentidegrees, pitchCentidegrees);\n",
    "        requireInputSchema(input.get());\n"
    "        long roundNumber = requireRoundNumber(input.getLong());\n"
    "        long sequence = requirePositiveSequence(input.getLong());\n"
    "        int forwardAxis = requireAxis(input.get());\n"
    "        int rightAxis = requireAxis(input.get());\n"
    "        boolean sprinting = requireInputFlags(input.get());\n"
    "        int yawCentidegrees = requireYaw(input.getShort());\n"
    "        int pitchCentidegrees = requirePitch(input.getShort());\n"
    "        return new PreparationInput(\n"
    "                roundNumber,\n"
    "                sequence,\n"
    "                forwardAxis,\n"
    "                rightAxis,\n"
    "                sprinting,\n"
    "                yawCentidegrees,\n"
    "                pitchCentidegrees);\n",
    "decode sprint flag",
)
text = replace_once(
    text,
    "                        .put((byte) SCHEMA_VERSION)\n"
    "                        .putLong(value.roundNumber())\n"
    "                        .putLong(value.authoritativeTick())\n",
    "                        .put((byte) SNAPSHOT_SCHEMA_VERSION)\n"
    "                        .putLong(value.roundNumber())\n"
    "                        .putLong(value.authoritativeTick())\n",
    "snapshot schema encode",
)
text = replace_once(
    text,
    "        requireSchema(input.get());\n"
    "        long roundNumber = requireRoundNumber(input.getLong());\n"
    "        long authoritativeTick = requireTick(input.getLong());\n",
    "        requireSnapshotSchema(input.get());\n"
    "        long roundNumber = requireRoundNumber(input.getLong());\n"
    "        long authoritativeTick = requireTick(input.getLong());\n",
    "snapshot schema decode",
)
text = replace_once(
    text,
    "    private static void requireSchema(byte raw) throws PreparationProtocolException {\n"
    "        if (Byte.toUnsignedInt(raw) != SCHEMA_VERSION) {\n"
    "            throw failure(\n"
    "                    PreparationProtocolException.Code.UNSUPPORTED_SCHEMA,\n"
    "                    \"preparation movement schema is unsupported\");\n"
    "        }\n"
    "    }\n",
    "    private static void requireInputSchema(byte raw) throws PreparationProtocolException {\n"
    "        if (Byte.toUnsignedInt(raw) != INPUT_SCHEMA_VERSION) {\n"
    "            throw failure(\n"
    "                    PreparationProtocolException.Code.UNSUPPORTED_SCHEMA,\n"
    "                    \"preparation input schema is unsupported\");\n"
    "        }\n"
    "    }\n\n"
    "    private static void requireSnapshotSchema(byte raw)\n"
    "            throws PreparationProtocolException {\n"
    "        if (Byte.toUnsignedInt(raw) != SNAPSHOT_SCHEMA_VERSION) {\n"
    "            throw failure(\n"
    "                    PreparationProtocolException.Code.UNSUPPORTED_SCHEMA,\n"
    "                    \"preparation snapshot schema is unsupported\");\n"
    "        }\n"
    "    }\n\n"
    "    private static boolean requireInputFlags(byte raw)\n"
    "            throws PreparationProtocolException {\n"
    "        int flags = Byte.toUnsignedInt(raw);\n"
    "        if ((flags & ~KNOWN_INPUT_FLAGS) != 0) {\n"
    "            throw failure(\n"
    "                    PreparationProtocolException.Code.INVALID_STATE,\n"
    "                    \"preparation input flags are invalid\");\n"
    "        }\n"
    "        return (flags & INPUT_FLAG_SPRINT) != 0;\n"
    "    }\n",
    "schema validation split",
)
write(path, text)

# Renderer input latch.
path = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationInputState.java"
text = read(path)
text = replace_once(
    text,
    "    private boolean left;\n    private boolean right;\n",
    "    private boolean left;\n    private boolean right;\n    private boolean sprinting;\n",
    "input sprint field",
)
text = replace_once(
    text,
    "    public double forwardAxis() {\n",
    "    public void setSprinting(boolean pressed) {\n"
    "        if (captured) {\n"
    "            sprinting = pressed;\n"
    "        }\n"
    "    }\n\n"
    "    public boolean sprinting() {\n"
    "        return sprinting;\n"
    "    }\n\n"
    "    public double forwardAxis() {\n",
    "input sprint methods",
)
text = replace_once(
    text,
    "        right = false;\n",
    "        right = false;\n        sprinting = false;\n",
    "clear sprint latch",
)
write(path, text)

# Client movement uses the same deterministic walking and sprint speeds.
path = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationMovementController.java"
text = read(path)
text = replace_once(
    text,
    "    public static final double MOVEMENT_SPEED_METRES_PER_SECOND = 5.0d;\n",
    "    public static final double MOVEMENT_SPEED_METRES_PER_SECOND = 5.0d;\n"
    "    public static final double SPRINTING_SPEED_METRES_PER_SECOND = 8.0d;\n",
    "client sprint speed",
)
text = replace_once(
    text,
    "    public static PreparationPlayerState move(\n"
    "            PreparationPlayerState current,\n"
    "            PreparationCollisionWorld collisions,\n"
    "            double forwardAxis,\n"
    "            double rightAxis,\n"
    "            double elapsedSeconds) {\n"
    "        PreparationPlayerState player = Objects.requireNonNull(current, \"current\");\n",
    "    public static PreparationPlayerState move(\n"
    "            PreparationPlayerState current,\n"
    "            PreparationCollisionWorld collisions,\n"
    "            double forwardAxis,\n"
    "            double rightAxis,\n"
    "            double elapsedSeconds) {\n"
    "        return move(current, collisions, forwardAxis, rightAxis, false, elapsedSeconds);\n"
    "    }\n\n"
    "    public static PreparationPlayerState move(\n"
    "            PreparationPlayerState current,\n"
    "            PreparationCollisionWorld collisions,\n"
    "            double forwardAxis,\n"
    "            double rightAxis,\n"
    "            boolean sprinting,\n"
    "            double elapsedSeconds) {\n"
    "        PreparationPlayerState player = Objects.requireNonNull(current, \"current\");\n",
    "client sprint overload",
)
text = replace_once(
    text,
    "        double step =\n"
    "                MOVEMENT_SPEED_METRES_PER_SECOND * Math.min(elapsedSeconds, MAXIMUM_STEP_SECONDS);\n",
    "        double speed =\n"
    "                sprinting\n"
    "                        ? SPRINTING_SPEED_METRES_PER_SECOND\n"
    "                        : MOVEMENT_SPEED_METRES_PER_SECOND;\n"
    "        double step = speed * Math.min(elapsedSeconds, MAXIMUM_STEP_SECONDS);\n",
    "client sprint step",
)
write(path, text)

# Prediction history preserves sprint mode per renderer step.
path = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPredictionHistory.java"
text = read(path)
text = replace_once(
    text,
    "    public PreparationPlayerState predict(\n"
    "            PreparationPlayerState current,\n"
    "            PreparationCollisionWorld collisions,\n"
    "            long sequence,\n"
    "            double forwardAxis,\n"
    "            double rightAxis,\n"
    "            double elapsedSeconds) {\n"
    "        PreparationPlayerState player = Objects.requireNonNull(current, \"current\");\n",
    "    public PreparationPlayerState predict(\n"
    "            PreparationPlayerState current,\n"
    "            PreparationCollisionWorld collisions,\n"
    "            long sequence,\n"
    "            double forwardAxis,\n"
    "            double rightAxis,\n"
    "            double elapsedSeconds) {\n"
    "        return predict(\n"
    "                current,\n"
    "                collisions,\n"
    "                sequence,\n"
    "                forwardAxis,\n"
    "                rightAxis,\n"
    "                false,\n"
    "                elapsedSeconds);\n"
    "    }\n\n"
    "    public PreparationPlayerState predict(\n"
    "            PreparationPlayerState current,\n"
    "            PreparationCollisionWorld collisions,\n"
    "            long sequence,\n"
    "            double forwardAxis,\n"
    "            double rightAxis,\n"
    "            boolean sprinting,\n"
    "            double elapsedSeconds) {\n"
    "        PreparationPlayerState player = Objects.requireNonNull(current, \"current\");\n",
    "prediction sprint overload",
)
text = replace_once(
    text,
    "                        rightAxis,\n"
    "                        player.yawDegrees(),\n",
    "                        rightAxis,\n"
    "                        sprinting,\n"
    "                        player.yawDegrees(),\n",
    "prediction sprint step",
)
text = replace_once(
    text,
    "                oriented, collisions, step.forwardAxis(), step.rightAxis(), step.elapsedSeconds());\n",
    "                oriented,\n"
    "                collisions,\n"
    "                step.forwardAxis(),\n"
    "                step.rightAxis(),\n"
    "                step.sprinting(),\n"
    "                step.elapsedSeconds());\n",
    "prediction sprint replay",
)
text = replace_once(
    text,
    "            double rightAxis,\n"
    "            double yawDegrees,\n",
    "            double rightAxis,\n"
    "            boolean sprinting,\n"
    "            double yawDegrees,\n",
    "prediction record sprint field",
)
write(path, text)

# Client input mapping and runtime propagation.
path = "client/src/main/java/pl/grzegorz2047/standalonethewalls/client/SunderfrontClient.java"
text = read(path)
text = replace_once(
    text,
    "    private static final String INPUT_MOVE_RIGHT = \"sunderfront-move-right\";\n",
    "    private static final String INPUT_MOVE_RIGHT = \"sunderfront-move-right\";\n"
    "    private static final String INPUT_SPRINT = \"sunderfront-sprint\";\n",
    "client sprint mapping constant",
)
text = replace_once(
    text,
    "        inputManager.addMapping(INPUT_MOVE_RIGHT, new KeyTrigger(KeyInput.KEY_D));\n",
    "        inputManager.addMapping(INPUT_MOVE_RIGHT, new KeyTrigger(KeyInput.KEY_D));\n"
    "        inputManager.addMapping(INPUT_SPRINT, new KeyTrigger(KeyInput.KEY_LSHIFT));\n",
    "client sprint key",
)
text = replace_once(
    text,
    "                INPUT_MOVE_LEFT,\n"
    "                INPUT_MOVE_RIGHT);\n",
    "                INPUT_MOVE_LEFT,\n"
    "                INPUT_MOVE_RIGHT,\n"
    "                INPUT_SPRINT);\n",
    "client sprint listener",
)
text = replace_once(
    text,
    "            case INPUT_MOVE_RIGHT -> preparationInput.set(Direction.RIGHT, pressed);\n",
    "            case INPUT_MOVE_RIGHT -> preparationInput.set(Direction.RIGHT, pressed);\n"
    "            case INPUT_SPRINT -> preparationInput.setSprinting(pressed);\n",
    "client sprint action",
)
text = replace_once(
    text,
    "                            preparationInput.rightAxis(),\n"
    "                            Math.min(\n",
    "                            preparationInput.rightAxis(),\n"
    "                            preparationInput.sprinting(),\n"
    "                            Math.min(\n",
    "client prediction sprint",
)
text = replace_once(
    text,
    "                        quantizeAxis(preparationInput.rightAxis()),\n"
    "                        quantizeYaw(current.yawDegrees()),\n",
    "                        quantizeAxis(preparationInput.rightAxis()),\n"
    "                        preparationInput.sprinting(),\n"
    "                        quantizeYaw(current.yawDegrees()),\n",
    "client wire sprint",
)
write(path, text)

# Authoritative fixed-tick server speed.
path = "server/src/main/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulation.java"
text = read(path)
text = replace_once(
    text,
    "    public static final int MOVEMENT_SPEED_MILLIMETRES_PER_SECOND = 5_000;\n"
    "    private static final double STEP_MILLIMETRES =\n"
    "            (double) MOVEMENT_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;\n",
    "    public static final int MOVEMENT_SPEED_MILLIMETRES_PER_SECOND = 5_000;\n"
    "    public static final int SPRINTING_SPEED_MILLIMETRES_PER_SECOND = 8_000;\n"
    "    private static final double WALKING_STEP_MILLIMETRES =\n"
    "            (double) MOVEMENT_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;\n"
    "    private static final double SPRINTING_STEP_MILLIMETRES =\n"
    "            (double) SPRINTING_SPEED_MILLIMETRES_PER_SECOND / TICKS_PER_SECOND;\n",
    "server sprint speed",
)
text = replace_once(
    text,
    "            double deltaX = STEP_MILLIMETRES * ((forward * forwardX) + (right * rightX));\n"
    "            double deltaZ = STEP_MILLIMETRES * ((forward * forwardZ) + (right * rightZ));\n",
    "            double step =\n"
    "                    input.sprinting()\n"
    "                            ? SPRINTING_STEP_MILLIMETRES\n"
    "                            : WALKING_STEP_MILLIMETRES;\n"
    "            double deltaX = step * ((forward * forwardX) + (right * rightX));\n"
    "            double deltaZ = step * ((forward * forwardZ) + (right * rightX));\n",
    "server sprint step",
)
# Correct the intentionally matched second expression to use rightZ.
text = replace_once(
    text,
    "            double deltaZ = step * ((forward * forwardZ) + (right * rightX));\n",
    "            double deltaZ = step * ((forward * forwardZ) + (right * rightZ));\n",
    "server right vector correction",
)
write(path, text)

migrated = migrate_preparation_input_constructors()
print(f"migrated_preparation_input_constructors={migrated}")

# Protocol golden vectors and malformed flags.
path = "protocol/src/test/java/pl/grzegorz2047/standalonethewalls/protocol/preparation/PreparationMovementProtocolCodecTest.java"
text = read(path)
text = replace_once(
    text,
    "    private static final int INPUT_FORWARD_OFFSET = INPUT_SEQUENCE_OFFSET + Long.BYTES;\n"
    "    private static final int INPUT_YAW_OFFSET = INPUT_FORWARD_OFFSET + 2;\n",
    "    private static final int INPUT_FORWARD_OFFSET = INPUT_SEQUENCE_OFFSET + Long.BYTES;\n"
    "    private static final int INPUT_FLAGS_OFFSET = INPUT_FORWARD_OFFSET + 2;\n"
    "    private static final int INPUT_YAW_OFFSET = INPUT_FLAGS_OFFSET + 1;\n",
    "protocol input offsets",
)
text = replace_once(
    text,
    "new PreparationInput(3L, 5L, 127, -127, false, 9_000, -2_500)",
    "new PreparationInput(3L, 5L, 127, -127, true, 9_000, -2_500)",
    "sprint golden input",
)
text = replace_once(
    text,
    "                        .put((byte) 1)\n"
    "                        .putLong(3L)\n"
    "                        .putLong(5L)\n"
    "                        .put((byte) 127)\n"
    "                        .put((byte) -127)\n"
    "                        .putShort((short) 9_000)\n",
    "                        .put((byte) 2)\n"
    "                        .putLong(3L)\n"
    "                        .putLong(5L)\n"
    "                        .put((byte) 127)\n"
    "                        .put((byte) -127)\n"
    "                        .put((byte) 1)\n"
    "                        .putShort((short) 9_000)\n",
    "sprint golden bytes",
)
text = replace_once(
    text,
    "        assertThat(input.rightAxisValue()).isEqualTo(-1.0d);\n",
    "        assertThat(input.rightAxisValue()).isEqualTo(-1.0d);\n"
    "        assertThat(input.sprinting()).isTrue();\n",
    "sprint round trip assertion",
)
text = replace_once(
    text,
    "        byte[] schema = validInputPayload();\n        schema[0] = 2;\n",
    "        byte[] schema = validInputPayload();\n        schema[0] = 1;\n",
    "reject legacy input schema",
)
text = replace_once(
    text,
    "        byte[] yaw = validInputPayload();\n",
    "        byte[] flags = validInputPayload();\n"
    "        flags[INPUT_FLAGS_OFFSET] = 2;\n"
    "        assertInputCode(flags, PreparationProtocolException.Code.INVALID_STATE);\n\n"
    "        byte[] yaw = validInputPayload();\n",
    "reject unknown input flags",
)
text = replace_once(
    text,
    "    @Test\n    void encodesTheExactFixedPointSnapshotVector()",
    "    @Test\n"
    "    void encodesWalkingWithAZeroFlagsByte() throws PreparationProtocolException {\n"
    "        PreparationInput input =\n"
    "                new PreparationInput(1L, 2L, 0, 0, false, 0, 0);\n\n"
    "        byte[] encoded = PreparationMovementProtocolCodec.encodeInput(input);\n\n"
    "        assertThat(encoded[INPUT_FLAGS_OFFSET]).isZero();\n"
    "        assertThat(PreparationMovementProtocolCodec.decodeInput(encoded).sprinting()).isFalse();\n"
    "    }\n\n"
    "    @Test\n    void encodesTheExactFixedPointSnapshotVector()",
    "walking flags vector",
)
write(path, text)

# Input latch tests.
path = "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationInputStateTest.java"
text = read(path)
text = replace_once(
    text,
    "        input.set(Direction.RIGHT, true);\n\n"
    "        assertThat(input.captured()).isFalse();\n",
    "        input.set(Direction.RIGHT, true);\n"
    "        input.setSprinting(true);\n\n"
    "        assertThat(input.captured()).isFalse();\n",
    "uncaptured sprint ignored",
)
text = replace_once(
    text,
    "        assertThat(input.rightAxis()).isZero();\n    }\n\n    @Test\n    void resolvesOpposingDirectionsToBoundedAxes()",
    "        assertThat(input.rightAxis()).isZero();\n"
    "        assertThat(input.sprinting()).isFalse();\n"
    "    }\n\n"
    "    @Test\n    void resolvesOpposingDirectionsToBoundedAxes()",
    "uncaptured sprint assertion",
)
text = replace_once(
    text,
    "        input.set(Direction.LEFT, true);\n\n"
    "        assertThat(input.release()).isTrue();\n",
    "        input.set(Direction.LEFT, true);\n"
    "        input.setSprinting(true);\n\n"
    "        assertThat(input.release()).isTrue();\n",
    "release sprint setup",
)
text = replace_once(
    text,
    "        assertThat(input.rightAxis()).isZero();\n"
    "        assertThat(input.release()).isFalse();\n",
    "        assertThat(input.rightAxis()).isZero();\n"
    "        assertThat(input.sprinting()).isFalse();\n"
    "        assertThat(input.release()).isFalse();\n",
    "release clears sprint",
)
text = replace_once(
    text,
    "        input.set(Direction.BACKWARD, true);\n\n"
    "        assertThat(input.capture()).isFalse();\n"
    "        assertThat(input.forwardAxis()).isEqualTo(-1.0d);\n",
    "        input.set(Direction.BACKWARD, true);\n"
    "        input.setSprinting(true);\n\n"
    "        assertThat(input.capture()).isFalse();\n"
    "        assertThat(input.forwardAxis()).isEqualTo(-1.0d);\n"
    "        assertThat(input.sprinting()).isTrue();\n",
    "repeated capture keeps sprint",
)
write(path, text)

# Client deterministic movement test.
path = "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationMovementControllerTest.java"
text = read(path)
text = replace_once(
    text,
    "    @Test\n    void rejectsInvalidAxesTimeAndMouseDelta()",
    "    @Test\n"
    "    void sprintsAtDeterministicSpeedAndStillNormalizesDiagonalInput()\n"
    "            throws PreparationSceneLoadException, PreparationSceneGraphException {\n"
    "        PreparationPlayerState player = player();\n"
    "        PreparationCollisionWorld collisions = collisions(player);\n\n"
    "        PreparationPlayerState walking =\n"
    "                PreparationMovementController.move(\n"
    "                        player, collisions, 1.0d, 0.0d, false, 0.1d);\n"
    "        PreparationPlayerState sprinting =\n"
    "                PreparationMovementController.move(\n"
    "                        player, collisions, 1.0d, 0.0d, true, 0.1d);\n"
    "        PreparationPlayerState diagonalSprint =\n"
    "                PreparationMovementController.move(\n"
    "                        player, collisions, 1.0d, 1.0d, true, 0.1d);\n\n"
    "        assertThat(distance(player.position(), walking.position()))\n"
    "                .isCloseTo(0.5d, within(0.000001d));\n"
    "        assertThat(distance(player.position(), sprinting.position()))\n"
    "                .isCloseTo(0.8d, within(0.000001d));\n"
    "        assertThat(distance(player.position(), diagonalSprint.position()))\n"
    "                .isCloseTo(0.8d, within(0.000001d));\n"
    "    }\n\n"
    "    @Test\n    void rejectsInvalidAxesTimeAndMouseDelta()",
    "client sprint movement test",
)
write(path, text)

# Prediction replay retains mixed sprint modes.
path = "client/src/test/java/pl/grzegorz2047/standalonethewalls/client/preparation/PreparationPredictionHistoryTest.java"
text = read(path)
text = replace_once(
    text,
    "    @Test\n    void acceptsAcknowledgementForSubmittedZeroInputWithoutPredictionSteps()",
    "    @Test\n"
    "    void replayPreservesSprintModeForEachUnacknowledgedStep()\n"
    "            throws PreparationSceneLoadException, PreparationSceneGraphException {\n"
    "        PreparationPlayerState spawn = player();\n"
    "        PreparationCollisionWorld collisions = collisions(spawn);\n"
    "        PreparationPredictionHistory history = new PreparationPredictionHistory();\n"
    "        PreparationPlayerState walking =\n"
    "                history.predict(spawn, collisions, 1L, 1.0d, 0.0d, false, 0.05d);\n"
    "        history.markSubmitted(1L);\n"
    "        history.predict(walking, collisions, 2L, 1.0d, 0.0d, true, 0.05d);\n"
    "        PreparationPlayerState authoritative =\n"
    "                spawn.withAuthoritativeState(-15.5d, 0.5d, -14.5d, 45.0d, 0.0d);\n\n"
    "        PreparationPlayerState reconciled = history.reconcile(authoritative, collisions, 1L);\n"
    "        PreparationPlayerState expected =\n"
    "                PreparationMovementController.move(\n"
    "                        authoritative, collisions, 1.0d, 0.0d, true, 0.05d);\n\n"
    "        assertThat(reconciled.position()).isEqualTo(expected.position());\n"
    "        assertThat(history.pendingStepCount()).isOne();\n"
    "    }\n\n"
    "    @Test\n    void acceptsAcknowledgementForSubmittedZeroInputWithoutPredictionSteps()",
    "prediction sprint replay test",
)
write(path, text)

# Server speed and diagonal bounds.
path = "server/src/test/java/pl/grzegorz2047/standalonethewalls/server/preparation/PreparationMovementSimulationTest.java"
text = read(path)
text = replace_once(
    text,
    "    @Test\n    void clampsMovementToTheVerifiedTeamRegionAndRemovesDisconnectedPlayers()",
    "    @Test\n"
    "    void appliesAuthoritativeSprintSpeedAndNormalizesItsDiagonal() {\n"
    "        PreparationMovementSimulation walkingSimulation = simulation();\n"
    "        PreparationMovementSimulation sprintingSimulation = simulation();\n"
    "        PreparationMovementSimulation diagonalSimulation = simulation();\n\n"
    "        PreparationWorldSnapshot walking =\n"
    "                walkingSimulation.advanceTick(\n"
    "                        11L,\n"
    "                        Map.of(\n"
    "                                ALPHA,\n"
    "                                new PreparationInput(\n"
    "                                        2L, 1L, 127, 0, false, 0, 0)));\n"
    "        PreparationWorldSnapshot sprinting =\n"
    "                sprintingSimulation.advanceTick(\n"
    "                        11L,\n"
    "                        Map.of(\n"
    "                                ALPHA,\n"
    "                                new PreparationInput(\n"
    "                                        2L, 1L, 127, 0, true, 0, 0)));\n"
    "        PreparationWorldSnapshot diagonal =\n"
    "                diagonalSimulation.advanceTick(\n"
    "                        11L,\n"
    "                        Map.of(\n"
    "                                ALPHA,\n"
    "                                new PreparationInput(\n"
    "                                        2L, 1L, 127, 127, true, 0, 0)));\n\n"
    "        assertThat(player(walking, ALPHA).xMillimetres()).isEqualTo(250);\n"
    "        assertThat(player(sprinting, ALPHA).xMillimetres()).isEqualTo(400);\n"
    "        assertThat(player(diagonal, ALPHA).xMillimetres()).isEqualTo(283);\n"
    "        assertThat(player(diagonal, ALPHA).zMillimetres()).isEqualTo(283);\n"
    "    }\n\n"
    "    @Test\n    void clampsMovementToTheVerifiedTeamRegionAndRemovesDisconnectedPlayers()",
    "server sprint test",
)
write(path, text)

# Visible controls copy.
for language, capture, captured in (
    (
        "en",
        "Click or press Enter to capture controls. Esc disconnects. Left Shift sprints.",
        "WASD moves. Mouse turns. Hold Left Shift to sprint. Esc releases the cursor.",
    ),
    (
        "pl",
        "Kliknij lub nacisnij Enter, aby przejac sterowanie. Esc rozlacza. Lewy Shift: sprint.",
        "WASD porusza. Mysz obraca. Przytrzymaj lewy Shift, aby sprintowac. Esc zwalnia kursor.",
    ),
):
    path = f"client/src/main/resources/i18n/messages_{language}.properties"
    lines = read(path).splitlines()
    replacements = {
        "preparation.controls.capture": capture,
        "preparation.controls.captured": captured,
    }
    seen: set[str] = set()
    for index, line in enumerate(lines):
        key, separator, _ = line.partition("=")
        if separator and key in replacements:
            lines[index] = f"{key}={replacements[key]}"
            seen.add(key)
    if seen != set(replacements):
        raise RuntimeError(f"{path}: missing controls keys")
    write(path, "\n".join(lines) + "\n")

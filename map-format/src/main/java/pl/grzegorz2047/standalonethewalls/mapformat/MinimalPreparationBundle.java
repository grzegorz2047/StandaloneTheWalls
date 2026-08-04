package pl.grzegorz2047.standalonethewalls.mapformat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministically generates the project-authored minimal four-team preparation map. */
public final class MinimalPreparationBundle {
    public static final String MAP_ID = "minimal_preparation";
    public static final String MAP_VERSION = "1.0.0";
    public static final String EXPECTED_ARCHIVE_SHA256 =
            "ec80f3b454699cb0a90d3d12309210939b3a97950222d7b5541fdc9ebb0e834b";

    private MinimalPreparationBundle() {
        throw new AssertionError("No instances");
    }

    public static byte[] createArchive() {
        try {
            Map<String, byte[]> members = members();
            byte[] manifest = manifest(members).getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                writeStored(zip, "manifest.json", manifest);
                for (String path :
                        List.of(
                                "collision.glb",
                                "gameplay.json",
                                "licenses.json",
                                "scene.glb",
                                "thumbnail.webp")) {
                    writeStored(zip, path, members.get(path));
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("in-memory map archive generation failed", exception);
        }
    }

    private static Map<String, byte[]> members() {
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("collision.glb", collisionGlb());
        members.put("gameplay.json", gameplay().getBytes(StandardCharsets.UTF_8));
        members.put(
                "licenses.json",
                readTextResource("licenses.json").stripTrailing().getBytes(StandardCharsets.UTF_8));
        members.put("scene.glb", sceneGlb());
        members.put(
                "thumbnail.webp",
                Base64.getMimeDecoder().decode(readTextResource("thumbnail.webp.b64")));
        return members;
    }

    private static String manifest(Map<String, byte[]> members) {
        StringBuilder files = new StringBuilder();
        boolean first = true;
        for (String path :
                List.of(
                        "collision.glb",
                        "gameplay.json",
                        "licenses.json",
                        "scene.glb",
                        "thumbnail.webp")) {
            if (!first) {
                files.append(',');
            }
            first = false;
            files.append('"')
                    .append(path)
                    .append("\":\"")
                    .append(shaHex(members.get(path)))
                    .append('"');
        }
        return "{\"author\":\"Sunderfront Team\",\"files\":{"
                + files
                + "},\"id\":\"minimal_preparation\",\"license\":\"CC0-1.0\",\"limits\":{\"archiveBytes\":1048576,\"fileCount\":5,\"sceneNodes\":64,\"textureDimension\":64,\"triangles\":256,\"uncompressedBytes\":2097152},\"maximumPlayers\":40,\"minimumPlayers\":4,\"name\":\"Minimal Preparation\",\"playersPerTeam\":10,\"requiredProtocol\":{\"major\":1,\"minor\":0},\"schemaVersion\":1,\"teamCount\":4,\"version\":\"1.0.0\"}";
    }

    private static String gameplay() {
        StringBuilder gameplay = new StringBuilder();
        gameplay.append("{\"regions\":[");
        gameplay.append(
                "{\"maximum\":[-1,6,-1],\"minimum\":[-18,0,-18],\"team\":\"GREEN\"},");
        gameplay.append(
                "{\"maximum\":[18,6,-1],\"minimum\":[1,0,-18],\"team\":\"BLUE\"},");
        gameplay.append(
                "{\"maximum\":[-1,6,18],\"minimum\":[-18,0,1],\"team\":\"RED\"},");
        gameplay.append(
                "{\"maximum\":[18,6,18],\"minimum\":[1,0,1],\"team\":\"YELLOW\"}],\"schema\":1,\"spawns\":[");
        int index = 0;
        index =
                appendSpawns(
                        gameplay,
                        index,
                        "GREEN",
                        new int[] {-15, -12, -9, -6, -3},
                        new int[] {-14, -6},
                        45);
        index =
                appendSpawns(
                        gameplay,
                        index,
                        "BLUE",
                        new int[] {3, 6, 9, 12, 15},
                        new int[] {-14, -6},
                        135);
        index =
                appendSpawns(
                        gameplay,
                        index,
                        "RED",
                        new int[] {-15, -12, -9, -6, -3},
                        new int[] {6, 14},
                        -45);
        appendSpawns(
                gameplay,
                index,
                "YELLOW",
                new int[] {3, 6, 9, 12, 15},
                new int[] {6, 14},
                -135);
        return gameplay.append("]}").toString();
    }

    private static int appendSpawns(
            StringBuilder gameplay,
            int index,
            String team,
            int[] xCoordinates,
            int[] zCoordinates,
            int yaw) {
        for (int z : zCoordinates) {
            for (int x : xCoordinates) {
                if (index > 0) {
                    gameplay.append(',');
                }
                gameplay.append("{\"index\":")
                        .append(index)
                        .append(",\"position\":[")
                        .append(x)
                        .append(",0.5,")
                        .append(z)
                        .append("],\"team\":\"")
                        .append(team)
                        .append("\",\"yaw\":")
                        .append(yaw)
                        .append('}');
                index++;
            }
        }
        return index;
    }

    private static byte[] sceneGlb() {
        return glb(sceneJson(), cubeBinary(true));
    }

    private static byte[] collisionGlb() {
        return glb(collisionJson(), cubeBinary(false));
    }

    private static String sceneJson() {
        return readTextResource("scene.gltf.json").stripTrailing();
    }

    private static String collisionJson() {
        return readTextResource("collision.gltf.json").stripTrailing();
    }

    private static String readTextResource(String name) {
        String path =
                "/pl/grzegorz2047/standalonethewalls/mapformat/minimal-preparation/" + name;
        try (InputStream input = MinimalPreparationBundle.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("missing embedded minimal map resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("embedded minimal map resource could not be read", exception);
        }
    }

    private static byte[] cubeBinary(boolean normals) {
        float[][] positions = {
            {0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f},
            {0.5f, 0.5f, -0.5f}, {-0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, -0.5f},
            {-0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, -0.5f},
            {0.5f, 0.5f, -0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f},
            {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, -0.5f},
            {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, 0.5f}, {-0.5f, -0.5f, 0.5f},
            {-0.5f, 0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, -0.5f, -0.5f},
            {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f}
        };
        float[][] faceNormals = {
            {1.0f, 0.0f, 0.0f}, {-1.0f, 0.0f, 0.0f}, {0.0f, 1.0f, 0.0f},
            {0.0f, -1.0f, 0.0f}, {0.0f, 0.0f, 1.0f}, {0.0f, 0.0f, -1.0f}
        };
        int size = positions.length * 12 + (normals ? positions.length * 12 : 0) + 36 * 2;
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        for (float[] position : positions) {
            for (float value : position) {
                buffer.putFloat(value);
            }
        }
        if (normals) {
            for (float[] normal : faceNormals) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    for (float value : normal) {
                        buffer.putFloat(value);
                    }
                }
            }
        }
        int[] faceIndices = {0, 1, 2, 0, 2, 3};
        for (int face = 0; face < 6; face++) {
            int offset = face * 4;
            for (int index : faceIndices) {
                buffer.putShort((short) (offset + index));
            }
        }
        return buffer.array();
    }

    private static byte[] glb(String json, byte[] binary) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int paddedJsonBytes = (jsonBytes.length + 3) & ~3;
        int paddedBinaryBytes = (binary.length + 3) & ~3;
        int totalBytes = 12 + 8 + paddedJsonBytes + 8 + paddedBinaryBytes;
        ByteBuffer output = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(0x46546C67).putInt(2).putInt(totalBytes);
        output.putInt(paddedJsonBytes).putInt(0x4E4F534A).put(jsonBytes);
        while (output.position() < 20 + paddedJsonBytes) {
            output.put((byte) ' ');
        }
        output.putInt(paddedBinaryBytes).putInt(0x004E4942).put(binary);
        while (output.hasRemaining()) {
            output.put((byte) 0);
        }
        return output.array();
    }

    private static void writeStored(ZipOutputStream zip, String path, byte[] bytes)
            throws IOException {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ZipEntry entry = new ZipEntry(path);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    static String shaHex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(64);
            for (byte current : digest) {
                value.append(Character.forDigit((current >>> 4) & 0x0F, 16));
                value.append(Character.forDigit(current & 0x0F, 16));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java platform", exception);
        }
    }
}

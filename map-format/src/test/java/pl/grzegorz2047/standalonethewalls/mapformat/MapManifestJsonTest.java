package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MapManifestJsonTest {
    @Test
    void decodesCompleteManifestDraftWithoutApplyingSemanticDefaults()
            throws MapManifestJsonException {
        MapManifestDraft draft =
                MapManifestJson.decode(validJson().getBytes(StandardCharsets.UTF_8));

        assertThat(draft.schemaVersion()).isEqualTo(1);
        assertThat(draft.id()).isEqualTo("minimal_preparation");
        assertThat(draft.requiredProtocolMajor()).isEqualTo(1);
        assertThat(draft.requiredProtocolMinor()).isZero();
        assertThat(draft.files()).containsEntry("scene.glb", "a".repeat(64));
        assertThat(draft.limits().fileCount()).isEqualTo(5);
    }

    @Test
    void rejectsUnknownDuplicateTrailingAndWrongTokenData() {
        assertCode(
                validJson().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"x\":0"),
                MapManifestJsonException.Code.UNKNOWN_FIELD);
        assertCode(
                validJson()
                        .replace(
                                "\"requiredProtocol\":{\"major\":1,\"minor\":0}",
                                "\"requiredProtocol\":{\"major\":1,\"minor\":0,\"x\":0}"),
                MapManifestJsonException.Code.UNKNOWN_FIELD);
        assertCode(
                validJson()
                        .replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                MapManifestJsonException.Code.MALFORMED_JSON);
        assertCode(validJson() + "{}", MapManifestJsonException.Code.MALFORMED_JSON);
        assertCode(
                validJson().replace("\"maximumPlayers\":40", "\"maximumPlayers\":\"40\""),
                MapManifestJsonException.Code.MALFORMED_JSON);
    }

    @Test
    void rejectsNullEmptyOversizedAndTooManyDeclaredFiles() {
        assertCode((byte[]) null, MapManifestJsonException.Code.INVALID_SIZE);
        assertCode(new byte[0], MapManifestJsonException.Code.INVALID_SIZE);
        assertCode(
                new byte[MapManifestJson.MAXIMUM_BYTES + 1],
                MapManifestJsonException.Code.INVALID_SIZE);

        StringBuilder files = new StringBuilder();
        for (int index = 0; index < 129; index++) {
            if (index > 0) {
                files.append(',');
            }
            files.append('"')
                    .append("file")
                    .append(index)
                    .append(".bin\":\"")
                    .append("a".repeat(64))
                    .append('"');
        }
        assertCode(
                validJson().replace(filesObject(), files.toString()),
                MapManifestJsonException.Code.TOO_MANY_FILES);
    }

    private static void assertCode(String json, MapManifestJsonException.Code expected) {
        assertCode(json.getBytes(StandardCharsets.UTF_8), expected);
    }

    private static void assertCode(byte[] json, MapManifestJsonException.Code expected) {
        assertThatThrownBy(() -> MapManifestJson.decode(json))
                .isInstanceOfSatisfying(
                        MapManifestJsonException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static String filesObject() {
        return "\"scene.glb\":\""
                + "a".repeat(64)
                + "\",\"collision.glb\":\""
                + "b".repeat(64)
                + "\",\"gameplay.json\":\""
                + "c".repeat(64)
                + "\",\"thumbnail.webp\":\""
                + "d".repeat(64)
                + "\",\"licenses.json\":\""
                + "e".repeat(64)
                + "\"";
    }

    private static String validJson() {
        return """
                {"author":"Sunderfront Team","files":{%s},"id":"minimal_preparation","license":"CC0-1.0","limits":{"archiveBytes":1048576,"fileCount":5,"sceneNodes":100,"textureDimension":256,"triangles":1000,"uncompressedBytes":2097152},"maximumPlayers":40,"minimumPlayers":4,"name":"Minimal Preparation","playersPerTeam":10,"requiredProtocol":{"major":1,"minor":0},"schemaVersion":1,"teamCount":4,"version":"1.0.0"}
                """
                .formatted(filesObject());
    }
}

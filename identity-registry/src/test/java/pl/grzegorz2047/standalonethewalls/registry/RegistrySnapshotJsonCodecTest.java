package pl.grzegorz2047.standalonethewalls.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class RegistrySnapshotJsonCodecTest {
    private static final String PUBLIC_KEY_BASE64 =
            "MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=";
    private static final String PLAYER_ID =
            "sf1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua";
    private static final String ROOT_ID =
            "sfr1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua";
    private static final String CANONICAL_JSON =
            "{\"entries\":[{\"handle\":\"player_one\",\"playerId\":\""
                    + PLAYER_ID
                    + "\",\"publicKey\":\""
                    + PUBLIC_KEY_BASE64
                    + "\",\"status\":\"ACTIVE\"}],\"generatedAt\":\"2026-08-02T00:00:00Z\",\"rootKeyId\":\""
                    + ROOT_ID
                    + "\",\"schema\":1,\"sequence\":7}";

    @Test
    void encodesAndDecodesPublicCanonicalVector()
            throws RegistrySnapshotException, NoSuchAlgorithmException {
        RegistrySnapshotPayload payload =
                new RegistrySnapshotPayload(
                        7L,
                        Instant.parse("2026-08-02T00:00:00Z"),
                        new RegistryRootId(ROOT_ID),
                        List.of(vectorEntry("player_one", PLAYER_ID)));

        byte[] encoded = RegistrySnapshotJsonCodec.encode(payload);

        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo(CANONICAL_JSON);
        assertThat(encoded).hasSize(333);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)))
                .isEqualTo("f160bf701d0e1291d50f958ac55941cc2fb63a4e9807ef9c847582affd9e3899");
        RegistrySnapshotPayload decoded =
                RegistrySnapshotJsonCodec.decodeCanonical(encoded, RegistrySnapshotPolicy.DEFAULT);
        assertThat(decoded.sequence()).isEqualTo(7L);
        assertThat(decoded.generatedAt()).isEqualTo(Instant.parse("2026-08-02T00:00:00Z"));
        assertThat(decoded.rootKeyId()).isEqualTo(new RegistryRootId(ROOT_ID));
        assertThat(decoded.entries()).containsExactly(payload.entries().getFirst());
    }

    @Test
    void rejectsWhitespaceDifferentFieldOrderDuplicateKeysUnknownFieldsAndTrailingData() {
        byte[] whitespace = (" " + CANONICAL_JSON).getBytes(StandardCharsets.UTF_8);
        byte[] fieldOrder =
                ("{\"sequence\":7,\"schema\":1,\"rootKeyId\":\""
                                + ROOT_ID
                                + "\",\"generatedAt\":\"2026-08-02T00:00:00Z\",\"entries\":[]}")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] duplicate =
                ("{\"entries\":[],\"generatedAt\":\"2026-08-02T00:00:00Z\",\"rootKeyId\":\""
                                + ROOT_ID
                                + "\",\"schema\":1,\"schema\":1,\"sequence\":7}")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] unknown =
                ("{\"entries\":[],\"extra\":0,\"generatedAt\":\"2026-08-02T00:00:00Z\",\"rootKeyId\":\""
                                + ROOT_ID
                                + "\",\"schema\":1,\"sequence\":7}")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] trailing = (CANONICAL_JSON + "{}").getBytes(StandardCharsets.UTF_8);

        assertRejected(whitespace);
        assertRejected(fieldOrder);
        assertRejected(duplicate);
        assertCode(unknown, RegistrySnapshotException.Code.UNKNOWN_FIELD);
        assertRejected(trailing);
    }

    @Test
    void rejectsUnsortedDuplicateAndMismatchedEntries() throws RegistrySnapshotException {
        String secondHandle = "z_player";
        RegistrySnapshotEntry first = vectorEntry(secondHandle, PLAYER_ID);
        RegistrySnapshotEntry second = vectorEntry("a_player", PLAYER_ID);
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotPayload(
                                        1L,
                                        Instant.parse("2026-08-02T00:00:00Z"),
                                        new RegistryRootId(ROOT_ID),
                                        List.of(first, second)))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(
                                                RegistrySnapshotException.Code.UNSORTED_ENTRIES));
        assertThatThrownBy(
                        () ->
                                new RegistrySnapshotPayload(
                                        1L,
                                        Instant.parse("2026-08-02T00:00:00Z"),
                                        new RegistryRootId(ROOT_ID),
                                        List.of(
                                                vectorEntry("same_name", PLAYER_ID),
                                                vectorEntry("same_name", PLAYER_ID))))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(
                                                RegistrySnapshotException.Code.DUPLICATE_HANDLE));
        assertThatThrownBy(
                        () ->
                                RegistrySnapshotEntry.create(
                                        new CanonicalHandle("player_one"),
                                        new PlayerId("sf1_" + "a".repeat(52)),
                                        Base64.getDecoder().decode(PUBLIC_KEY_BASE64),
                                        RegistryEntryStatus.ACTIVE))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure ->
                                assertThat(failure.code())
                                        .isEqualTo(
                                                RegistrySnapshotException.Code.PLAYER_ID_MISMATCH));
    }

    @Test
    void rejectsNonCanonicalTimestampAndUnsupportedStatus() {
        byte[] timestamp =
                CANONICAL_JSON
                        .replace("2026-08-02T00:00:00Z", "2026-08-02T00:00:00.000Z")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] status =
                CANONICAL_JSON
                        .replace("\"ACTIVE\"", "\"UNKNOWN\"")
                        .getBytes(StandardCharsets.UTF_8);

        assertCode(timestamp, RegistrySnapshotException.Code.INVALID_TIMESTAMP);
        assertCode(status, RegistrySnapshotException.Code.INVALID_ENTRY);
    }

    private static RegistrySnapshotEntry vectorEntry(String handle, String playerId)
            throws RegistrySnapshotException {
        return RegistrySnapshotEntry.create(
                new CanonicalHandle(handle),
                new PlayerId(playerId),
                Base64.getDecoder().decode(PUBLIC_KEY_BASE64),
                RegistryEntryStatus.ACTIVE);
    }

    private static void assertRejected(byte[] encoded) {
        assertThatThrownBy(
                        () ->
                                RegistrySnapshotJsonCodec.decodeCanonical(
                                        encoded, RegistrySnapshotPolicy.DEFAULT))
                .isInstanceOf(RegistrySnapshotException.class);
    }

    private static void assertCode(byte[] encoded, RegistrySnapshotException.Code code) {
        assertThatThrownBy(
                        () ->
                                RegistrySnapshotJsonCodec.decodeCanonical(
                                        encoded, RegistrySnapshotPolicy.DEFAULT))
                .isInstanceOfSatisfying(
                        RegistrySnapshotException.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }
}

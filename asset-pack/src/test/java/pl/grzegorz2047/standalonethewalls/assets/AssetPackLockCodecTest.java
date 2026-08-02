package pl.grzegorz2047.standalonethewalls.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetPackLockCodecTest {
    private static final String ARCHIVE_DIGEST = "a".repeat(64);
    private static final String MANIFEST_DIGEST = "b".repeat(64);

    @Test
    void exactCanonicalBytesRoundTripDeterministically() throws AssetPackLockException {
        AssetPackLock lock = new AssetPackLock(1, List.of(reference("core", "1.2.3")));

        byte[] encoded = AssetPackLockCodec.encode(lock);

        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .isEqualTo(
                        "{\"packs\":[{\"formatVersion\":1,\"id\":\"core\",\"manifestPath\":\"manifest.json\",\"manifestSha256\":\""
                                + MANIFEST_DIGEST
                                + "\",\"sha256\":\""
                                + ARCHIVE_DIGEST
                                + "\",\"size\":4096,\"url\":\"https://assets.example.invalid/releases/core-1.2.3.zip\",\"version\":\"1.2.3\"}],\"schema\":1}");
        assertThat(AssetPackLockCodec.decode(encoded)).isEqualTo(lock);
        assertThat(AssetPackLockCodec.encode(AssetPackLockCodec.decode(encoded)))
                .containsExactly(encoded);
    }

    @Test
    void rejectsWhitespaceUnknownFieldsTrailingBytesAndEscapes() {
        String canonical =
                new String(
                        AssetPackLockCodec.encode(
                                new AssetPackLock(1, List.of(reference("core", "1.0.0")))),
                        StandardCharsets.UTF_8);

        assertThatThrownBy(
                        () -> AssetPackLockCodec.decode((" " + canonical).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AssetPackLockException.class);
        assertThatThrownBy(
                        () ->
                                AssetPackLockCodec.decode(
                                        canonical
                                                .replace("\"schema\":1", "\"unknown\":1,\"schema\":1")
                                                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AssetPackLockException.class);
        assertThatThrownBy(
                        () -> AssetPackLockCodec.decode((canonical + "\n").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AssetPackLockException.class);
        assertThatThrownBy(
                        () ->
                                AssetPackLockCodec.decode(
                                        canonical
                                                .replace("\"core\"", "\"co\\u0072e\"")
                                                .getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AssetPackLockException.class);
    }

    @Test
    void rejectsMutableUrlsInvalidDigestsUnsortedPacksAndDuplicateIds() {
        assertThatThrownBy(
                        () ->
                                new AssetPackReference(
                                        "core",
                                        "1.0.0",
                                        1,
                                        URI.create("https://assets.example.invalid/latest/core.zip"),
                                        1,
                                        ARCHIVE_DIGEST,
                                        "manifest.json",
                                        MANIFEST_DIGEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutable");
        assertThatThrownBy(
                        () ->
                                new AssetPackReference(
                                        "core",
                                        "1.0.0",
                                        1,
                                        URI.create("https://assets.example.invalid/core-1.0.0.zip"),
                                        1,
                                        ARCHIVE_DIGEST.toUpperCase(java.util.Locale.ROOT),
                                        "manifest.json",
                                        MANIFEST_DIGEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase SHA-256");
        assertThatThrownBy(
                        () ->
                                new AssetPackLock(
                                        1,
                                        List.of(
                                                reference("zeta", "1.0.0"),
                                                reference("alpha", "1.0.0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sorted");
        assertThatThrownBy(
                        () ->
                                new AssetPackLock(
                                        1,
                                        List.of(
                                                reference("core", "1.0.0"),
                                                reference("core", "2.0.0"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    private static AssetPackReference reference(String id, String version) {
        return new AssetPackReference(
                id,
                version,
                1,
                URI.create(
                        "https://assets.example.invalid/releases/"
                                + id
                                + '-'
                                + version
                                + ".zip"),
                4096,
                ARCHIVE_DIGEST,
                "manifest.json",
                MANIFEST_DIGEST);
    }
}

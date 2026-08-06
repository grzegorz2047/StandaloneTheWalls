package pl.grzegorz2047.standalonethewalls.mapformat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class MinimalPreparationBundleTest {
    private static final TwMapLoadPolicy POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    @Test
    void generatesTheSameCompleteVerifiedArchiveEveryTime()
            throws TwMapBundleException,
                    Glb2Exception,
                    PreparationSupportException,
                    PreparationObstacleException {
        byte[] first = MinimalPreparationBundle.createArchive();
        byte[] second = MinimalPreparationBundle.createArchive();

        assertThat(first).isEqualTo(second).hasSize(14_589);
        VerifiedMapBundle bundle = TwMapBundleLoader.load(first, POLICY);
        assertThat(bundle.archiveSha256().value())
                .isEqualTo(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        assertThat(bundle.manifest().id()).isEqualTo(MinimalPreparationBundle.MAP_ID);
        assertThat(bundle.manifest().version().toString())
                .isEqualTo(MinimalPreparationBundle.MAP_VERSION);
        assertThat(bundle.manifest().teamCount()).isEqualTo(4);
        assertThat(bundle.manifest().maximumPlayers()).isEqualTo(40);
        assertThat(bundle.memberNames())
                .containsExactlyInAnyOrder(
                        "collision.glb",
                        "gameplay.json",
                        "licenses.json",
                        "scene.glb",
                        "thumbnail.webp");

        Glb2Document scene =
                Glb2ContainerDecoder.decode(bundle.member("scene.glb"), bundle.manifest().limits());
        Glb2Document collision =
                Glb2ContainerDecoder.decode(
                        bundle.member("collision.glb"), bundle.manifest().limits());
        assertThat(scene.nodeCount()).isEqualTo(16);
        assertThat(scene.meshCount()).isEqualTo(6);
        assertThat(scene.materialCount()).isEqualTo(6);
        assertThat(scene.lightCount()).isEqualTo(1);
        assertThat(scene.jsonUtf8())
                .contains(
                        "Ground",
                        "GreenSupport",
                        "BlueSupport",
                        "RedSupport",
                        "YellowSupport",
                        "CentralWallX",
                        "CentralWallZ",
                        "KHR_lights_punctual");
        assertThat(collision.nodeCount()).isEqualTo(11);
        assertThat(collision.meshCount()).isEqualTo(1);
        assertThat(collision.materialCount()).isZero();
        assertThat(collision.lightCount()).isZero();
        assertThat(collision.jsonUtf8())
                .contains(
                        "GroundCollision",
                        "GreenSupportCollision",
                        "BlueSupportCollision",
                        "RedSupportCollision",
                        "YellowSupportCollision",
                        "CentralWallXCollision",
                        "CentralWallZCollision");
        assertThat(Glb2PreparationSupportDecoder.decode(collision).boxes()).hasSize(5);
        assertThat(Glb2PreparationObstacleDecoder.decode(collision).boxes()).hasSize(6);
    }

    @Test
    void suppliesTenExclusiveSpawnsPerTeam() throws TwMapBundleException {
        PreparationGameplay gameplay =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY).gameplay();

        assertThat(gameplay.regions()).hasSize(4);
        assertThat(gameplay.spawns()).hasSize(40);
        for (PreparationTeam team : PreparationTeam.values()) {
            assertThat(gameplay.spawns()).filteredOn(spawn -> spawn.team() == team).hasSize(10);
        }
        assertThat(gameplay.spawns())
                .extracting(PreparationMapSpawn::index)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, 40).boxed().toList());
        assertThat(gameplay.spawns())
                .allSatisfy(
                        spawn ->
                                assertThat(
                                                gameplay.regions().stream()
                                                        .filter(
                                                                region ->
                                                                        region.team()
                                                                                == spawn.team())
                                                        .findFirst()
                                                        .orElseThrow()
                                                        .contains(spawn.position()))
                                        .isTrue());
    }

    @Test
    void packagesOnlyStoredEntriesInCanonicalOrder() throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip =
                new ZipInputStream(
                        new ByteArrayInputStream(MinimalPreparationBundle.createArchive()),
                        StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                assertThat(entry.getMethod()).isEqualTo(ZipEntry.STORED);
                assertThat(entry.getTimeLocal().getYear()).isEqualTo(1980);
                zip.closeEntry();
            }
        }
        assertThat(names)
                .containsExactly(
                        "manifest.json",
                        "collision.glb",
                        "gameplay.json",
                        "licenses.json",
                        "scene.glb",
                        "thumbnail.webp");
    }

    @Test
    void embedsAProjectAuthoredWebpAndLicenseEvidence() throws TwMapBundleException {
        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY);
        byte[] thumbnail = bundle.member("thumbnail.webp");

        assertThat(thumbnail).hasSize(678);
        assertThat(new String(thumbnail, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(thumbnail, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        assertThat(new String(bundle.member("licenses.json"), StandardCharsets.UTF_8))
                .contains("CC0-1.0", "deterministic project generator", "project-authored");
    }
}

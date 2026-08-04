package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.domain.TeamId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantId;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyParticipantState;
import pl.grzegorz2047.standalonethewalls.domain.lobby.LobbyRosterState;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchPhase;
import pl.grzegorz2047.standalonethewalls.domain.match.MatchResult;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleException;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleLoader;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapLoadPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.VerifiedMapBundle;
import pl.grzegorz2047.standalonethewalls.server.lobby.LobbyMatchSnapshot;

class VerifiedPreparationMapAdapterTest {
    private static final TwMapLoadPolicy POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    @Test
    void adaptsTheGeneratedVerifiedBundleAndPlansAllFortyPlayers()
            throws TwMapBundleException, VerifiedPreparationMapException {
        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY);

        PreparationMapDefinition map = VerifiedPreparationMapAdapter.adapt(bundle);

        assertThat(map.mapId()).isEqualTo(MinimalPreparationBundle.MAP_ID);
        assertThat(map.mapSha256()).containsExactly(decodeHex(bundle.archiveSha256().value()));
        assertThat(map.spawnPoints()).hasSize(40);
        assertThat(map.spawnPoints())
                .extracting(PreparationSpawnPoint::index)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, 40).boxed().toList());
        assertThat(map.spawnPoints().getFirst())
                .isEqualTo(new PreparationSpawnPoint(0, TeamId.GREEN, -15.0d, 0.5d, -14.0d, 45.0d));
        assertThat(map.spawnPoints().getLast())
                .isEqualTo(
                        new PreparationSpawnPoint(
                                39, TeamId.YELLOW, 15.0d, 0.5d, 14.0d, -135.0d));

        LobbyRosterState roster = fullRoster();
        List<PreparationClientSpawn> plan =
                PreparationTransitionPlanner.plan(map, roster, preparation(roster));
        assertThat(plan).hasSize(40);
        assertThat(plan)
                .extracting(delivery -> delivery.assignment().spawnIndex())
                .doesNotHaveDuplicates();
        assertThat(plan)
                .allSatisfy(
                        delivery -> {
                            assertThat(delivery.assignment().mapId())
                                    .isEqualTo(MinimalPreparationBundle.MAP_ID);
                            assertThat(delivery.assignment().mapSha256())
                                    .containsExactly(map.mapSha256());
                            assertThat(delivery.assignment().rosterRevision())
                                    .isEqualTo(roster.revision());
                        });
    }

    @Test
    void rejectsHashVerifiedButStructurallyInvalidSceneAndCollision() throws Exception {
        byte[] invalidScene = replaceMember("scene.glb", new byte[] {1, 2, 3, 4});
        assertCode(
                TwMapBundleLoader.load(invalidScene, POLICY),
                VerifiedPreparationMapException.Code.INVALID_SCENE);

        byte[] invalidCollision = replaceMember("collision.glb", new byte[] {5, 6, 7, 8});
        assertCode(
                TwMapBundleLoader.load(invalidCollision, POLICY),
                VerifiedPreparationMapException.Code.INVALID_COLLISION);
    }

    @Test
    void rejectsHashVerifiedGameplayThatCannotCoverManifestCapacity() throws Exception {
        byte[] sparseGameplay = sparseGameplay().getBytes(StandardCharsets.UTF_8);
        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(replaceMember("gameplay.json", sparseGameplay), POLICY);

        assertCode(bundle, VerifiedPreparationMapException.Code.INSUFFICIENT_TEAM_SPAWNS);
    }

    @Test
    void rejectsMissingBundle() {
        assertThatThrownBy(() -> VerifiedPreparationMapAdapter.adapt(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static void assertCode(
            VerifiedMapBundle bundle, VerifiedPreparationMapException.Code expected) {
        assertThatThrownBy(() -> VerifiedPreparationMapAdapter.adapt(bundle))
                .isInstanceOfSatisfying(
                        VerifiedPreparationMapException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }

    private static LobbyRosterState fullRoster() {
        List<LobbyParticipantState> participants = new ArrayList<>(40);
        for (TeamId team : TeamId.values()) {
            for (int index = 0; index < 10; index++) {
                participants.add(
                        new LobbyParticipantState(
                                new LobbyParticipantId(
                                        team.name().toLowerCase(Locale.ROOT) + "-0" + index),
                                Optional.of(team),
                                true));
            }
        }
        return new LobbyRosterState(11L, participants);
    }

    private static LobbyMatchSnapshot preparation(LobbyRosterState roster) {
        return new LobbyMatchSnapshot(
                5L,
                roster.revision(),
                20L,
                MatchPhase.PREPARATION,
                200L,
                roster.participants().size(),
                2L,
                MatchResult.NONE,
                Optional.empty());
    }

    private static byte[] replaceMember(String path, byte[] replacement) throws IOException {
        Map<String, byte[]> entries = readEntries(MinimalPreparationBundle.createArchive());
        byte[] original = entries.put(path, replacement.clone());
        if (original == null) {
            throw new AssertionError("missing generated member: " + path);
        }
        String manifest = new String(entries.get("manifest.json"), StandardCharsets.UTF_8);
        manifest = manifest.replace(shaHex(original), shaHex(replacement));
        entries.put("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
        return writeEntries(entries);
    }

    private static Map<String, byte[]> readEntries(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static byte[] writeEntries(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] decodeHex(String hexadecimal) {
        byte[] bytes = new byte[hexadecimal.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int offset = index * 2;
            bytes[index] =
                    (byte)
                            ((Character.digit(hexadecimal.charAt(offset), 16) << 4)
                                    | Character.digit(hexadecimal.charAt(offset + 1), 16));
        }
        return bytes;
    }

    private static String shaHex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                value.append(Character.forDigit((current >>> 4) & 0x0F, 16));
                value.append(Character.forDigit(current & 0x0F, 16));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String sparseGameplay() {
        return """
                {"regions":[{"maximum":[-1,6,-1],"minimum":[-18,0,-18],"team":"GREEN"},{"maximum":[18,6,-1],"minimum":[1,0,-18],"team":"BLUE"},{"maximum":[-1,6,18],"minimum":[-18,0,1],"team":"RED"},{"maximum":[18,6,18],"minimum":[1,0,1],"team":"YELLOW"}],"schema":1,"spawns":[{"index":0,"position":[-10,0.5,-10],"team":"GREEN","yaw":45},{"index":1,"position":[10,0.5,-10],"team":"BLUE","yaw":135},{"index":2,"position":[-10,0.5,10],"team":"RED","yaw":-45},{"index":3,"position":[10,0.5,10],"team":"YELLOW","yaw":-135}]}
                """;
    }
}

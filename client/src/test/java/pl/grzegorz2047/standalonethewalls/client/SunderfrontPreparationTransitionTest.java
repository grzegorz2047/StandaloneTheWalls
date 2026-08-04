package pl.grzegorz2047.standalonethewalls.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationPlayerState;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoadException;
import pl.grzegorz2047.standalonethewalls.client.preparation.PreparationSceneLoader;
import pl.grzegorz2047.standalonethewalls.client.preparation.VerifiedPreparationScene;
import pl.grzegorz2047.standalonethewalls.mapformat.MapVector3;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class SunderfrontPreparationTransitionTest {
    private static final ClientMessages MESSAGES =
            ClientMessages.forLanguage(ClientLanguage.ENGLISH);

    @Test
    void leavesTheLobbyShellAtTheVerifiedAuthoritativeSpawn() throws PreparationSceneLoadException {
        SunderfrontClient client = new SunderfrontClient(MESSAGES, true);
        VerifiedPreparationScene scene = verifiedScene(LobbyTeam.GREEN, 0, -15.0d, -14.0d, 45.0d);

        client.exercisePreparationTransition(scene);

        assertThat(client.isPreparationActive()).isTrue();
        PreparationPlayerState player = client.currentPreparationPlayerState().orElseThrow();
        assertThat(player.scene()).isSameAs(scene);
        assertThat(player.position()).isEqualTo(new MapVector3(-15.0d, 0.5d, -14.0d));
        assertThat(player.yawDegrees()).isEqualTo(45.0d);
        assertThat(player.pitchDegrees()).isZero();
        assertThat(client.isPreparationInputCaptured()).isFalse();

        client.exercisePreparationInputCapture();
        assertThat(client.isPreparationInputCaptured()).isTrue();
        client.exercisePreparationInputRelease();
        assertThat(client.isPreparationInputCaptured()).isFalse();
    }

    @Test
    void laterFramesCannotReplaceTheEnteredScene() throws PreparationSceneLoadException {
        SunderfrontClient client = new SunderfrontClient(MESSAGES, true);
        VerifiedPreparationScene first = verifiedScene(LobbyTeam.GREEN, 0, -15.0d, -14.0d, 45.0d);
        VerifiedPreparationScene later = verifiedScene(LobbyTeam.BLUE, 10, 3.0d, -14.0d, 135.0d);

        client.exercisePreparationTransition(first);
        PreparationPlayerState entered = client.currentPreparationPlayerState().orElseThrow();
        client.exercisePreparationTransition(later);

        assertThat(client.currentPreparationPlayerState()).containsSame(entered);
        assertThat(client.currentPreparationPlayerState().orElseThrow().scene()).isSameAs(first);
    }

    private static VerifiedPreparationScene verifiedScene(
            LobbyTeam team, int spawnIndex, double x, double z, double yawDegrees)
            throws PreparationSceneLoadException {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        8L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        team,
                        spawnIndex,
                        x,
                        0.5d,
                        z,
                        yawDegrees);
        return PreparationSceneLoader.loadDefault(assignment);
    }
}

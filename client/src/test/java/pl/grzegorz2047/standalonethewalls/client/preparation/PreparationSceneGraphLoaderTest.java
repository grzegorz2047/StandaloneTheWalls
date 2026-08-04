package pl.grzegorz2047.standalonethewalls.client.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.protocol.lobby.LobbyTeam;
import pl.grzegorz2047.standalonethewalls.protocol.preparation.PreparationSpawnAssignment;

class PreparationSceneGraphLoaderTest {
    @Test
    void loadsTheVerifiedMinimalGlbWithoutARenderContext()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);
        VerifiedPreparationScene verified = verifiedGreenScene();

        Node graph = PreparationSceneGraphLoader.load(assetManager, verified);

        assertThat(graph.getName()).isEqualTo("verified-preparation-minimal_preparation");
        assertThat(graph.getQuantity()).isOne();
        assertThat(graph.getChild("Ground")).isNotNull();
        assertThat(graph.getChild("GreenRegion")).isNotNull();
        assertThat(graph.getChild("CentralWallX")).isNotNull();
        assertThat(graph.getChild("PreparationSun")).isNotNull();
        assertThat(graph.getParent()).isNull();
    }

    @Test
    void loadsTheVerifiedCollisionGlbAsASeparateDetachedGraph()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);
        VerifiedPreparationScene verified = verifiedGreenScene();

        Node graph = PreparationSceneGraphLoader.loadCollision(assetManager, verified);

        assertThat(graph.getName())
                .isEqualTo("verified-preparation-collision-minimal_preparation");
        assertThat(graph.getChild("GroundCollision")).isNotNull();
        assertThat(graph.getChild("CentralWallXCollision")).isNotNull();
        assertThat(graph.getChild("CentralWallZCollision")).isNotNull();
        assertThat(graph.getParent()).isNull();
    }

    @Test
    void streamLoadsAreDetachedAndNotSharedThroughTheAssetCache()
            throws PreparationSceneLoadException, PreparationSceneGraphException {
        DesktopAssetManager assetManager = new DesktopAssetManager(true);
        VerifiedPreparationScene verified = verifiedGreenScene();

        Node first = PreparationSceneGraphLoader.load(assetManager, verified);
        Node second = PreparationSceneGraphLoader.load(assetManager, verified);

        assertThat(second).isNotSameAs(first);
        assertThat(second.getChild(0)).isNotSameAs(first.getChild(0));
    }

    private static VerifiedPreparationScene verifiedGreenScene()
            throws PreparationSceneLoadException {
        byte[] digest = HexFormat.of().parseHex(MinimalPreparationBundle.EXPECTED_ARCHIVE_SHA256);
        PreparationSpawnAssignment assignment =
                new PreparationSpawnAssignment(
                        8L,
                        1L,
                        MinimalPreparationBundle.MAP_ID,
                        digest,
                        LobbyTeam.GREEN,
                        0,
                        -15.0d,
                        0.5d,
                        -14.0d,
                        45.0d);
        return PreparationSceneLoader.loadDefault(assignment);
    }
}

package pl.grzegorz2047.standalonethewalls.server.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.mapformat.MinimalPreparationBundle;
import pl.grzegorz2047.standalonethewalls.mapformat.PreparationObstacleBox;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleException;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapBundleLoader;
import pl.grzegorz2047.standalonethewalls.mapformat.TwMapLoadPolicy;
import pl.grzegorz2047.standalonethewalls.mapformat.VerifiedMapBundle;

class VerifiedPreparationObstacleMapAdapterTest {
    private static final TwMapLoadPolicy POLICY =
            new TwMapLoadPolicy(2 * 1024 * 1024, 4 * 1024 * 1024, 16, 100);

    @Test
    void carriesAllMinimalWallBoxesIntoTheServerDefinition()
            throws TwMapBundleException, VerifiedPreparationMapException {
        VerifiedMapBundle bundle =
                TwMapBundleLoader.load(MinimalPreparationBundle.createArchive(), POLICY);

        PreparationMapDefinition map = VerifiedPreparationMapAdapter.adapt(bundle);

        assertThat(map.obstacleMap().boxes())
                .extracting(PreparationObstacleBox::name)
                .containsExactly(
                        "CentralWallXCollision",
                        "CentralWallZCollision",
                        "EastWallCollision",
                        "NorthWallCollision",
                        "SouthWallCollision",
                        "WestWallCollision");
    }
}

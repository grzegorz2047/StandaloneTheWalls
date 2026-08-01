package pl.grzegorz2047.standalonethewalls.mapstudio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Process boundary for the future map authoring application. */
public final class MapStudioMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapStudioMain.class);

    private MapStudioMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        LOGGER.info(
                "{} Map Studio module initialized; editing features are not implemented yet.",
                BuildInfo.PRODUCT_NAME);
    }
}

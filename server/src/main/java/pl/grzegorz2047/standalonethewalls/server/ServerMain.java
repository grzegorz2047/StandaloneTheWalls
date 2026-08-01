package pl.grzegorz2047.standalonethewalls.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Temporary process entry point until the fixed-tick runtime is implemented in issue #25. */
public final class ServerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        LOGGER.info("{} dedicated server module initialized; gameplay runtime is not implemented yet.", BuildInfo.PRODUCT_NAME);
    }
}

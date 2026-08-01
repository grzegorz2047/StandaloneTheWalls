package pl.grzegorz2047.standalonethewalls.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Temporary process entry point until the first client screen is implemented in issue #26. */
public final class ClientMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientMain.class);

    private ClientMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        LOGGER.info("{} client module initialized; no playable client is claimed yet.", BuildInfo.PRODUCT_NAME);
    }
}

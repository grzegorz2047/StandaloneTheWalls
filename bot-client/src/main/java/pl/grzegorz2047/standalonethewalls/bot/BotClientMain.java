package pl.grzegorz2047.standalonethewalls.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzegorz2047.standalonethewalls.shared.BuildInfo;

/** Process boundary for deterministic integration and load-test clients. */
public final class BotClientMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotClientMain.class);

    private BotClientMain() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) {
        LOGGER.info("{} bot client module initialized; bot scenarios are not implemented yet.", BuildInfo.PRODUCT_NAME);
    }
}

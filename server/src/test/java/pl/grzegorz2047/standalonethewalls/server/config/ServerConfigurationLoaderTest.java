package pl.grzegorz2047.standalonethewalls.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

class ServerConfigurationLoaderTest {
    @Test
    void loadsOverridesAndKeepsUnspecifiedDefaults() throws Exception {
        ServerConfiguration configuration = ServerConfigurationLoader.load(new StringReader(
                "server.name=Private Arena\n"
                        + "server.tick-rate=30\n"
                        + "server.maximum-players=16\n"));

        assertEquals("Private Arena", configuration.name());
        assertEquals(30, configuration.tickRate());
        assertEquals(16, configuration.maximumPlayers());
        assertEquals(ServerConfiguration.defaults().reliablePort(), configuration.reliablePort());
    }

    @Test
    void rejectsUnknownKeysMalformedNumbersAndInvalidCrossFieldValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfigurationLoader.load(new StringReader("server.unknown=value\n")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfigurationLoader.load(new StringReader("server.tick-rate=fast\n")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerConfigurationLoader.load(new StringReader(
                        "server.reliable-port=27420\nserver.realtime-port=27420\n")));
    }
}

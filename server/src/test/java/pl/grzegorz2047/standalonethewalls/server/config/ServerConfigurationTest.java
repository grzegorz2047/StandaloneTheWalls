package pl.grzegorz2047.standalonethewalls.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ServerConfigurationTest {
    @Test
    void exposesDocumentedDefaultsAndNanosecondPeriod() {
        ServerConfiguration configuration = ServerConfiguration.defaults();

        assertEquals("Sunderfront Server", configuration.name());
        assertEquals(20, configuration.tickRate());
        assertEquals(50_000_000L, configuration.tickPeriodNanos());
        assertEquals(40, configuration.maximumPlayers());
    }

    @Test
    void validatesTickRatePortsNameAndCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfiguration("server", 9, 27420, 27421, 40));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfiguration("server", 20, 27420, 27420, 40));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfiguration("server", 20, 0, 27421, 40));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfiguration("server", 20, 27420, 27421, 41));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfiguration("\n", 20, 27420, 27421, 40));
    }
}

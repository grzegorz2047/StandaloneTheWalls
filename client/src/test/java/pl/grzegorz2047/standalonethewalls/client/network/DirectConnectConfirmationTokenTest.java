package pl.grzegorz2047.standalonethewalls.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DirectConnectConfirmationTokenTest {
    @Test
    void comparesExactBytesWithoutRenderingThem() {
        byte[] firstBytes = new byte[32];
        Arrays.fill(firstBytes, (byte) 7);
        DirectConnectConfirmationToken first = new DirectConnectConfirmationToken(firstBytes);
        DirectConnectConfirmationToken equal = new DirectConnectConfirmationToken(firstBytes);
        byte[] differentBytes = firstBytes.clone();
        differentBytes[differentBytes.length - 1] = 8;
        DirectConnectConfirmationToken different =
                new DirectConnectConfirmationToken(differentBytes);

        Arrays.fill(firstBytes, (byte) 0);

        assertTrue(first.securelyEquals(equal));
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertFalse(first.securelyEquals(different));
        assertNotEquals(first, different);
        assertEquals("DirectConnectConfirmationToken[redacted]", first.toString());
    }
}

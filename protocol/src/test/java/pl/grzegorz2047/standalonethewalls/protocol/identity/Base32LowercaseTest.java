package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Base32LowercaseTest {
    @Test
    void matchesRfc4648VectorsWithoutPadding() {
        assertEquals("", encode(""));
        assertEquals("my", encode("f"));
        assertEquals("mzxq", encode("fo"));
        assertEquals("mzxw6", encode("foo"));
        assertEquals("mzxw6yq", encode("foob"));
        assertEquals("mzxw6ytb", encode("fooba"));
        assertEquals("mzxw6ytboi", encode("foobar"));
    }

    private static String encode(String value) {
        return Base32Lowercase.encode(value.getBytes(StandardCharsets.US_ASCII));
    }
}

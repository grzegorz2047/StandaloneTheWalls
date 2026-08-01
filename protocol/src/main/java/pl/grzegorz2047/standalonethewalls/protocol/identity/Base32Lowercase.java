package pl.grzegorz2047.standalonethewalls.protocol.identity;

/** RFC 4648 Base32 using lowercase output and no padding. */
final class Base32Lowercase {
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();

    private Base32Lowercase() {
        throw new AssertionError("No instances");
    }

    static String encode(byte[] input) {
        StringBuilder output = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | Byte.toUnsignedInt(value);
            bits += 8;
            while (bits >= 5) {
                output.append(ALPHABET[(buffer >>> (bits - 5)) & 0x1F]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            output.append(ALPHABET[(buffer << (5 - bits)) & 0x1F]);
        }
        return output.toString();
    }
}

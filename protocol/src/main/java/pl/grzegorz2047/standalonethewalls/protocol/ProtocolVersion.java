package pl.grzegorz2047.standalonethewalls.protocol;

/** Semantic wire-protocol version encoded in every envelope. */
public record ProtocolVersion(int major, int minor) {
    public static final ProtocolVersion CURRENT = new ProtocolVersion(1, 0);

    public ProtocolVersion {
        if (major < 0 || major > 0xFFFF) {
            throw new IllegalArgumentException("major must fit an unsigned short");
        }
        if (minor < 0 || minor > 0xFFFF) {
            throw new IllegalArgumentException("minor must fit an unsigned short");
        }
    }

    public boolean isSupported() {
        return equals(CURRENT);
    }
}

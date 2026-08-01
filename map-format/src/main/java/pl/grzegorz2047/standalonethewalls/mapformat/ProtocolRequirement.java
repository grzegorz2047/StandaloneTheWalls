package pl.grzegorz2047.standalonethewalls.mapformat;

/** Minimum exact protocol contract requested by a map package. */
public record ProtocolRequirement(int major, int minor) {
    public ProtocolRequirement {
        if (major < 0 || major > 0xFFFF || minor < 0 || minor > 0xFFFF) {
            throw new IllegalArgumentException("protocol components must fit unsigned shorts");
        }
    }
}

package pl.grzegorz2047.standalonethewalls.mapformat;

/** One-way match policy controlling whether verified central barriers block movement. */
public enum PreparationBarrierPolicy {
    CLOSED,
    OPEN;

    public boolean blocksCentralBarriers() {
        return this == CLOSED;
    }
}

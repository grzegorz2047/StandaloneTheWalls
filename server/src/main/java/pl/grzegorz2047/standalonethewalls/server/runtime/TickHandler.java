package pl.grzegorz2047.standalonethewalls.server.runtime;

/** Work performed by the authoritative simulation for one sequential tick. */
@FunctionalInterface
public interface TickHandler {
    void onTick(long tickNumber);
}

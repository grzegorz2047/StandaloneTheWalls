package pl.grzegorz2047.standalonethewalls.client.performance;

import java.util.OptionalLong;

/** Supplies one ready optional GPU frame-time result without blocking. */
@FunctionalInterface
interface GpuFrameTimeSource {
    OptionalLong poll();
}

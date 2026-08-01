package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Untrusted manifest input before semantic validation.
 *
 * <p>Boxed and nullable fields intentionally preserve missing JSON values so the validator can
 * report precise paths instead of failing during object construction.
 */
public record MapManifestDraft(
        Integer schemaVersion,
        String id,
        String name,
        String author,
        String version,
        Integer minimumPlayers,
        Integer maximumPlayers,
        Integer teamCount,
        Integer playersPerTeam,
        Integer requiredProtocolMajor,
        Integer requiredProtocolMinor,
        String license,
        Map<String, String> files,
        MapLimitsDraft limits) {

    public MapManifestDraft {
        files = files == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }
}

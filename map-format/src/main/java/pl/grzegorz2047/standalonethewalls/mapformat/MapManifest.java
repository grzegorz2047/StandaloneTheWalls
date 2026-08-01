package pl.grzegorz2047.standalonethewalls.mapformat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable typed representation produced by successful manifest validation. */
public record MapManifest(
        int schemaVersion,
        String id,
        String name,
        String author,
        SemanticVersion version,
        int minimumPlayers,
        int maximumPlayers,
        int teamCount,
        int playersPerTeam,
        ProtocolRequirement requiredProtocol,
        String license,
        Map<String, Sha256Digest> files,
        MapLimits limits) {

    public MapManifest {
        if (schemaVersion != MapManifestValidator.SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schema version");
        }
        id = requireText(id, "id");
        name = requireText(name, "name");
        author = requireText(author, "author");
        Objects.requireNonNull(version, "version");
        if (minimumPlayers < 1
                || maximumPlayers < minimumPlayers
                || teamCount < 1
                || playersPerTeam < 1
                || maximumPlayers > teamCount * playersPerTeam) {
            throw new IllegalArgumentException("player limits are inconsistent");
        }
        Objects.requireNonNull(requiredProtocol, "requiredProtocol");
        license = requireText(license, "license");
        files = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(files, "files")));
        if (files.isEmpty()) {
            throw new IllegalArgumentException("files cannot be empty");
        }
        Objects.requireNonNull(limits, "limits");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}

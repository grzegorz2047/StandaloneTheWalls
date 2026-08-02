package pl.grzegorz2047.standalonethewalls.server.identity;

import java.nio.file.Path;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;

/** Validated local file and authorization configuration for identity runtime composition. */
public record LocalIdentityRuntimeConfiguration(
        Path sqliteDatabasePath,
        Path registryBundlePath,
        HandleAuthorizationMode authorizationMode) {
    public LocalIdentityRuntimeConfiguration {
        sqliteDatabasePath = normalizeFile(sqliteDatabasePath, "sqliteDatabasePath");
        registryBundlePath = normalizeFile(registryBundlePath, "registryBundlePath");
        authorizationMode = Objects.requireNonNull(authorizationMode, "authorizationMode");
        if (sqliteDatabasePath.equals(registryBundlePath)) {
            throw new IllegalArgumentException(
                    "SQLite database and registry bundle must use different paths");
        }
    }

    private static Path normalizeFile(Path path, String field) {
        Path normalized = Objects.requireNonNull(path, field).toAbsolutePath().normalize();
        if (normalized.getFileName() == null || normalized.getParent() == null) {
            throw new IllegalArgumentException(field + " must identify a file");
        }
        return normalized;
    }
}

package pl.grzegorz2047.standalonethewalls.server.identity;

import java.nio.file.Path;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.server.config.identity.RegistryRefreshConfiguration;

/** Validated local file, authorization and registry refresh configuration. */
public record LocalIdentityRuntimeConfiguration(
        Path sqliteDatabasePath,
        Path registryBundlePath,
        HandleAuthorizationMode authorizationMode,
        RegistryRefreshConfiguration registryRefreshConfiguration) {
    public LocalIdentityRuntimeConfiguration(
            Path sqliteDatabasePath,
            Path registryBundlePath,
            HandleAuthorizationMode authorizationMode) {
        this(
                sqliteDatabasePath,
                registryBundlePath,
                authorizationMode,
                new RegistryRefreshConfiguration.LocalBundle());
    }

    public LocalIdentityRuntimeConfiguration {
        sqliteDatabasePath = normalizeFile(sqliteDatabasePath, "sqliteDatabasePath");
        registryBundlePath = normalizeFile(registryBundlePath, "registryBundlePath");
        authorizationMode = Objects.requireNonNull(authorizationMode, "authorizationMode");
        registryRefreshConfiguration =
                Objects.requireNonNull(
                        registryRefreshConfiguration, "registryRefreshConfiguration");
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

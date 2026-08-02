package pl.grzegorz2047.standalonethewalls.server.config.identity;

import java.nio.file.Path;
import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntimeConfiguration;

/** Complete, validated process inputs required to open the local identity runtime. */
public record LocalIdentityProcessConfiguration(
        LocalIdentityRuntimeConfiguration runtimeConfiguration,
        Path trustRootsPath,
        RegistryTrustBundle trustBundle,
        RegistrySnapshotPolicy registryPolicy,
        RegistryRefreshConfiguration registryRefreshConfiguration) {
    public LocalIdentityProcessConfiguration(
            LocalIdentityRuntimeConfiguration runtimeConfiguration,
            Path trustRootsPath,
            RegistryTrustBundle trustBundle,
            RegistrySnapshotPolicy registryPolicy) {
        this(
                runtimeConfiguration,
                trustRootsPath,
                trustBundle,
                registryPolicy,
                new RegistryRefreshConfiguration.LocalBundle());
    }

    public LocalIdentityProcessConfiguration {
        runtimeConfiguration = Objects.requireNonNull(runtimeConfiguration, "runtimeConfiguration");
        trustRootsPath =
                Objects.requireNonNull(trustRootsPath, "trustRootsPath")
                        .toAbsolutePath()
                        .normalize();
        trustBundle = Objects.requireNonNull(trustBundle, "trustBundle");
        registryPolicy = Objects.requireNonNull(registryPolicy, "registryPolicy");
        registryRefreshConfiguration =
                Objects.requireNonNull(
                        registryRefreshConfiguration, "registryRefreshConfiguration");
        if (trustRootsPath.getFileName() == null || trustRootsPath.getParent() == null) {
            throw new IllegalArgumentException("trustRootsPath must identify a file");
        }
        if (trustRootsPath.equals(runtimeConfiguration.sqliteDatabasePath())
                || trustRootsPath.equals(runtimeConfiguration.registryBundlePath())) {
            throw new IllegalArgumentException(
                    "trust roots must use a different path than SQLite and registry bundle");
        }
    }
}

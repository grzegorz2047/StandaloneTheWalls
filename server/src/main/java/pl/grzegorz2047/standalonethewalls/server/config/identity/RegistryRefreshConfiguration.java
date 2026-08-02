package pl.grzegorz2047.standalonethewalls.server.config.identity;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsConfiguration;

/** Immutable process choice for administrative registry verification and reload. */
public sealed interface RegistryRefreshConfiguration
        permits RegistryRefreshConfiguration.LocalBundle, RegistryRefreshConfiguration.Https {
    Source source();

    enum Source {
        LOCAL_BUNDLE,
        HTTPS
    }

    record LocalBundle() implements RegistryRefreshConfiguration {
        @Override
        public Source source() {
            return Source.LOCAL_BUNDLE;
        }
    }

    record Https(RegistrySnapshotHttpsConfiguration configuration)
            implements RegistryRefreshConfiguration {
        public Https {
            configuration = Objects.requireNonNull(configuration, "configuration");
        }

        @Override
        public Source source() {
            return Source.HTTPS;
        }
    }
}

package pl.grzegorz2047.standalonethewalls.server.config.identity;

import java.util.Objects;
import pl.grzegorz2047.standalonethewalls.registry.http.RegistrySnapshotHttpsConfiguration;

/** Immutable process choice for administrative and optional automatic registry refresh. */
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

    record Https(
            RegistrySnapshotHttpsConfiguration configuration,
            RegistryRefreshScheduleConfiguration schedule)
            implements RegistryRefreshConfiguration {
        public Https(RegistrySnapshotHttpsConfiguration configuration) {
            this(configuration, RegistryRefreshScheduleConfiguration.DEFAULT);
        }

        public Https {
            configuration = Objects.requireNonNull(configuration, "configuration");
            schedule = Objects.requireNonNull(schedule, "schedule");
        }

        @Override
        public Source source() {
            return Source.HTTPS;
        }
    }
}

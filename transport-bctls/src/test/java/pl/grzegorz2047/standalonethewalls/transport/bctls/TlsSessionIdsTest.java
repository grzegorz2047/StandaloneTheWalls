package pl.grzegorz2047.standalonethewalls.transport.bctls;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TlsSessionIdsTest {
    @Test
    void createsNonZeroUniqueRfc4122VersionFourIdentifiers() {
        SecureRandom random = new SecureRandom();
        Set<UUID> generated = new HashSet<>();

        for (int index = 0; index < 128; index++) {
            UUID sessionId = TlsSessionIds.randomV4(random);
            assertThat(sessionId.version()).isEqualTo(4);
            assertThat(sessionId.variant()).isEqualTo(2);
            assertThat(sessionId).isNotEqualTo(new UUID(0L, 0L));
            assertThat(generated.add(sessionId)).isTrue();
        }
    }
}

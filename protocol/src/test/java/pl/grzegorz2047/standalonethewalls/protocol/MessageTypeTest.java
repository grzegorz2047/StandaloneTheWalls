package pl.grzegorz2047.standalonethewalls.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MessageTypeTest {
    @Test
    void wireIdsAreUniquePositiveAndResolvable() {
        Set<Integer> ids = new HashSet<>();

        for (MessageType type : MessageType.values()) {
            assertThat(type.wireId()).isPositive();
            assertThat(ids.add(type.wireId())).isTrue();
            assertThat(MessageType.fromWireId(type.wireId())).contains(type);
            assertThat(type.maximumPayloadBytes())
                    .isBetween(0, ProtocolCodec.MAXIMUM_PAYLOAD_BYTES);
        }
        assertThat(MessageType.fromWireId(0xFFFF)).isEmpty();
    }
}

package pl.grzegorz2047.standalonethewalls.client.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

class DirectConnectFailureTest {
    @Test
    void exposesAdmissionStatusOnlyForAdmissionRejection() {
        DirectConnectFailure rejection =
                DirectConnectFailure.admissionRejected(
                        PlayerSessionAdmissionStatus.LOCAL_BINDING_CONFLICT);

        assertEquals(DirectConnectFailureCode.ADMISSION_REJECTED, rejection.code());
        assertEquals(
                PlayerSessionAdmissionStatus.LOCAL_BINDING_CONFLICT,
                rejection.admissionStatus().orElseThrow());
        assertTrue(
                DirectConnectFailure.of(DirectConnectFailureCode.CANCELLED)
                        .admissionStatus()
                        .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DirectConnectFailure(
                                DirectConnectFailureCode.CANCELLED,
                                Optional.of(PlayerSessionAdmissionStatus.LOCAL_BINDING_CONFLICT)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DirectConnectFailure(
                                DirectConnectFailureCode.ADMISSION_REJECTED, Optional.empty()));
    }
}

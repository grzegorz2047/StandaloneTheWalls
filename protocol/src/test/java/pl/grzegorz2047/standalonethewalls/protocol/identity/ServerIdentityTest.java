package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class ServerIdentityTest {
    private static final byte[] PUBLIC_KEY_VECTOR =
            Base64.getDecoder()
                    .decode("MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=");

    @Test
    void derivesStableServerIdAndFingerprintFromCanonicalPublicKeyBytes() throws IdentityException {
        ServerId serverId = ServerId.fromPublicKey(PUBLIC_KEY_VECTOR);

        assertEquals("sfs1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua", serverId.value());
        assertEquals(
                "6935-ae6e-c114-b657-5fa4",
                ServerFingerprint.fromPublicKey(PUBLIC_KEY_VECTOR).value());
        assertNotEquals(PlayerId.fromPublicKey(PUBLIC_KEY_VECTOR).value(), serverId.value());
    }

    @Test
    void rejectsMalformedAndNoncanonicalPublicKeys() {
        IdentityException exception =
                assertThrows(
                        IdentityException.class,
                        () -> ServerId.fromPublicKey(new byte[] {1, 2, 3}));

        assertEquals(IdentityException.Code.INVALID_PUBLIC_KEY, exception.code());
    }
}

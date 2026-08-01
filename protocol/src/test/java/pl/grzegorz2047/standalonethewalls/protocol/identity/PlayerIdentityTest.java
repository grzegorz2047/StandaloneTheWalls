package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlayerIdentityTest {
    private static final byte[] PUBLIC_KEY_VECTOR =
            Base64.getDecoder()
                    .decode("MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=");

    @Test
    void derivesAStablePlayerIdAndFingerprintFromCanonicalPublicKeyBytes()
            throws IdentityException {
        assertEquals(
                "sf1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua",
                PlayerId.fromPublicKey(PUBLIC_KEY_VECTOR).value());
        assertEquals(
                "6935-ae6e-c114-b657-5fa4",
                PlayerFingerprint.fromPublicKey(PUBLIC_KEY_VECTOR).value());
    }

    @Test
    void generatesAnApplicationIdentityWithoutExposingKeyBytesInText() throws IdentityException {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        String publicKeyBase64 = Base64.getEncoder().encodeToString(identity.publicKeyEncoded());

        assertTrue(identity.playerId().value().matches("sf1_[a-z2-7]{52}"));
        assertTrue(identity.fingerprint().value().matches("[0-9a-f]{4}(?:-[0-9a-f]{4}){4}"));
        assertFalse(identity.toString().contains(publicKeyBase64));
        assertTrue(identity.toString().contains(identity.playerId().value()));
    }

    @Test
    void loadOrCreatePersistsOnceAndReturnsTheSameCryptographicIdentity() throws IdentityException {
        MemoryStore store = new MemoryStore();

        PlayerIdentity first = PlayerIdentity.loadOrCreate(store, new SecureRandom());
        PlayerIdentity second = PlayerIdentity.loadOrCreate(store, new SecureRandom());

        assertEquals(first.playerId(), second.playerId());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertArrayEquals(first.publicKeyEncoded(), second.publicKeyEncoded());
        assertEquals(1, store.saveCount);
    }

    @Test
    void rejectsMismatchedPublicAndPrivateKeysLoadedFromStorage() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair first = generator.generateKeyPair();
        KeyPair second = generator.generateKeyPair();
        MemoryStore store = new MemoryStore();
        store.keyPair = new KeyPair(first.getPublic(), second.getPrivate());

        IdentityException exception =
                assertThrows(
                        IdentityException.class,
                        () -> PlayerIdentity.loadOrCreate(store, new SecureRandom()));

        assertEquals(IdentityException.Code.INVALID_KEY_PAIR, exception.code());
    }

    @Test
    void separateGeneratedKeysProduceSeparatePlayerIds() throws IdentityException {
        PlayerIdentity first = PlayerIdentity.generate(new SecureRandom());
        PlayerIdentity second = PlayerIdentity.generate(new SecureRandom());

        assertNotEquals(first.playerId(), second.playerId());
    }

    private static final class MemoryStore implements PlayerIdentityStore {
        private KeyPair keyPair;
        private int saveCount;

        @Override
        public Optional<KeyPair> load() {
            return Optional.ofNullable(keyPair);
        }

        @Override
        public void save(KeyPair value) {
            keyPair = value;
            saveCount++;
        }
    }
}

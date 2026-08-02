package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolVersion;

class IdentityPayloadCodecTest {
    private static final String PUBLIC_KEY_BASE64 =
            "MCowBQYDK2VwAyEAoBGdJyYRGPquhsJXoEoTOOticDHR4bM2z/5DScGCHPU=";
    private static final String PLAYER_ID =
            "sf1_ne2243wbcs3fox5evlg23khripu53paxtss2ckqxnycbtqgks7ua";

    @Test
    void encodesAndDecodesCanonicalChallengeVector() throws IdentityPayloadException {
        byte[] nonce = new byte[IdentityChallenge.NONCE_BYTES];
        for (int index = 0; index < nonce.length; index++) {
            nonce[index] = (byte) index;
        }
        IdentityChallengePayload payload =
                new IdentityChallengePayload(nonce, Instant.ofEpochMilli(1_735_689_600_000L));

        byte[] encoded = IdentityPayloadCodec.encodeChallenge(payload);

        assertThat(encoded).hasSize(IdentityPayloadCodec.CHALLENGE_BYTES);
        assertThat(HexFormat.of().formatHex(encoded))
                .isEqualTo(
                        "0001000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f000001941f297c00");
        IdentityChallengePayload decoded = IdentityPayloadCodec.decodeChallenge(encoded);
        assertThat(decoded.nonce()).containsExactly(nonce);
        assertThat(decoded.expiresAt()).isEqualTo(payload.expiresAt());
        assertThat(decoded.toString()).doesNotContain(HexFormat.of().formatHex(nonce));
    }

    @Test
    void encodesAndDecodesCanonicalProofVector()
            throws IdentityPayloadException, NoSuchAlgorithmException {
        byte[] publicKey = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
        byte[] signature = new byte[64];
        IdentityProof proof =
                new IdentityProof(
                        ProtocolVersion.CURRENT,
                        new CanonicalHandle("player_one"),
                        new PlayerId(PLAYER_ID),
                        publicKey,
                        signature);

        byte[] encoded = IdentityPayloadCodec.encodeProof(proof);

        assertThat(encoded).hasSize(188);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)))
                .isEqualTo("3d4f5c6b82fa7f81e9a98e18b17aca339691735fc2945794156eda4b4f304a2a");
        IdentityProof decoded = IdentityPayloadCodec.decodeProof(encoded);
        assertThat(decoded.protocolVersion()).isEqualTo(ProtocolVersion.CURRENT);
        assertThat(decoded.handle()).isEqualTo(proof.handle());
        assertThat(decoded.playerId()).isEqualTo(proof.playerId());
        assertThat(decoded.publicKey()).containsExactly(publicKey);
        assertThat(decoded.signature()).containsExactly(signature);
    }

    @Test
    void encodesAndDecodesCanonicalAcceptedResultVector() throws IdentityPayloadException {
        IdentityResultPayload payload = new IdentityResultPayload(IdentityResultStatus.ACCEPTED);

        byte[] encoded = IdentityPayloadCodec.encodeResult(payload);

        assertThat(HexFormat.of().formatHex(encoded)).isEqualTo("0001000100086163636570746564");
        assertThat(IdentityPayloadCodec.decodeResult(encoded)).isEqualTo(payload);
    }

    @Test
    void rejectsUnsupportedSchemaTrailingBytesAndTruncation() {
        byte[] valid =
                IdentityPayloadCodec.encodeResult(
                        new IdentityResultPayload(IdentityResultStatus.ACCEPTED));
        byte[] wrongSchema = valid.clone();
        wrongSchema[1] = 2;
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);

        assertCode(wrongSchema, IdentityPayloadException.Code.UNSUPPORTED_SCHEMA);
        assertCode(trailing, IdentityPayloadException.Code.TRAILING_BYTES);
        assertCode(truncated, IdentityPayloadException.Code.INVALID_SIZE);
    }

    @Test
    void rejectsStatusCodeMismatchAndUnknownStatus() {
        byte[] mismatch =
                IdentityPayloadCodec.encodeResult(
                        new IdentityResultPayload(IdentityResultStatus.ACCEPTED));
        mismatch[mismatch.length - 1] = 'x';
        byte[] unknown = mismatch.clone();
        unknown[2] = 0x7f;
        unknown[3] = 0x7f;

        assertCode(mismatch, IdentityPayloadException.Code.STATUS_CODE_MISMATCH);
        assertCode(unknown, IdentityPayloadException.Code.INVALID_STATUS);
    }

    @Test
    void rejectsNonAsciiHandleAndInvalidPublicKeyBeforeVerification() {
        byte[] publicKey = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
        IdentityProof proof =
                new IdentityProof(
                        ProtocolVersion.CURRENT,
                        new CanonicalHandle("player_one"),
                        new PlayerId(PLAYER_ID),
                        publicKey,
                        new byte[64]);
        byte[] encoded = IdentityPayloadCodec.encodeProof(proof);
        byte[] nonAscii = encoded.clone();
        nonAscii[8] = (byte) 0x80;
        byte[] invalidKey = encoded.clone();
        int publicKeyOffset = 6 + 2 + 10 + 2 + 56 + 2;
        invalidKey[publicKeyOffset] ^= 1;

        assertProofCode(nonAscii, IdentityPayloadException.Code.INVALID_TEXT);
        assertProofCode(invalidKey, IdentityPayloadException.Code.INVALID_PUBLIC_KEY);
    }

    @Test
    void errorTextNeverContainsPayloadHex() {
        byte[] payload = new byte[] {0x12, 0x34, 0x56};
        assertThatThrownBy(() -> IdentityPayloadCodec.decodeProof(payload))
                .isInstanceOfSatisfying(
                        IdentityPayloadException.class,
                        failure ->
                                assertThat(failure.getMessage())
                                        .doesNotContain(HexFormat.of().formatHex(payload)));
    }

    private static void assertCode(byte[] encoded, IdentityPayloadException.Code expected) {
        assertThatThrownBy(() -> IdentityPayloadCodec.decodeResult(encoded))
                .isInstanceOfSatisfying(
                        IdentityPayloadException.class,
                        failure -> assertThat(failure.code()).isEqualTo(expected));
    }

    private static void assertProofCode(byte[] encoded, IdentityPayloadException.Code expected) {
        assertThatThrownBy(() -> IdentityPayloadCodec.decodeProof(encoded))
                .isInstanceOfSatisfying(
                        IdentityPayloadException.class,
                        failure -> assertThat(failure.code()).isEqualTo(expected));
    }
}

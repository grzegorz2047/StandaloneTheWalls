package pl.grzegorz2047.standalonethewalls.server.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.transport.bctls.BouncyCastleTlsCryptoFactory;

class ServerCredentialGeneratorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createsCompleteCredentialsAndRefusesOverwrite() throws Exception {
        Path output = temporaryDirectory.resolve("credentials");

        ServerCredentialGenerator.GeneratedCredentials generated =
                ServerCredentialGenerator.generate(output);

        Path privateKey = output.resolve(ServerCredentialGenerator.PRIVATE_KEY_FILE);
        Path certificate = output.resolve(ServerCredentialGenerator.CERTIFICATE_FILE);
        Path registryRoots = output.resolve(ServerCredentialGenerator.REGISTRY_ROOTS_FILE);
        Path fingerprint = output.resolve(ServerCredentialGenerator.FINGERPRINT_FILE);
        assertTrue(Files.size(privateKey) > 0);
        assertTrue(Files.size(certificate) > 0);
        assertTrue(Files.size(registryRoots) > 0);
        assertTrue(Files.size(fingerprint) > 0);

        X509Certificate parsedCertificate =
                (X509Certificate)
                        CertificateFactory.getInstance(
                                        "X.509", BouncyCastleTlsCryptoFactory.provider())
                                .generateCertificate(Files.newInputStream(certificate));
        assertEquals(
                generated.fingerprint(),
                ServerFingerprint.fromPublicKey(parsedCertificate.getPublicKey().getEncoded()));
        assertEquals(
                generated.fingerprint().value() + "\n",
                Files.readString(fingerprint, StandardCharsets.US_ASCII));

        String rootLine = Files.readString(registryRoots, StandardCharsets.US_ASCII);
        assertTrue(rootLine.endsWith("\n"));
        assertFalse(rootLine.substring(0, rootLine.length() - 1).isBlank());
        assertArrayEquals(
                HexFormat.of().parseHex(rootLine.substring(0, rootLine.length() - 1)),
                HexFormat.of().parseHex(rootLine.trim()));

        byte[] originalPrivateKey = Files.readAllBytes(privateKey);
        assertThrows(IOException.class, () -> ServerCredentialGenerator.generate(output));
        assertArrayEquals(originalPrivateKey, Files.readAllBytes(privateKey));
    }

    @Test
    void failsBeforeCreatingSiblingsWhenAnyTargetAlreadyExists() throws Exception {
        Path output = temporaryDirectory.resolve("collision");
        Files.createDirectories(output);
        Path existing = output.resolve(ServerCredentialGenerator.FINGERPRINT_FILE);
        Files.writeString(existing, "operator-owned\n", StandardCharsets.US_ASCII);

        assertThrows(IOException.class, () -> ServerCredentialGenerator.generate(output));

        assertEquals("operator-owned\n", Files.readString(existing, StandardCharsets.US_ASCII));
        assertFalse(Files.exists(output.resolve(ServerCredentialGenerator.PRIVATE_KEY_FILE)));
        assertFalse(Files.exists(output.resolve(ServerCredentialGenerator.CERTIFICATE_FILE)));
        assertFalse(Files.exists(output.resolve(ServerCredentialGenerator.REGISTRY_ROOTS_FILE)));
    }
}

package pl.grzegorz2047.standalonethewalls.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerLauncherHttpsIdentityValidationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validateOnlyAcceptsHttpsSourceWithoutOpeningRuntimeOrNetwork()
            throws IOException, NoSuchAlgorithmException {
        Path sqlite = temporaryDirectory.resolve("identity.sqlite");
        Path bundle = temporaryDirectory.resolve("registry.sfrb");
        Path roots = temporaryDirectory.resolve("roots.hex");
        Path configuration = temporaryDirectory.resolve("identity.properties");
        byte[] publicKey =
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
        Files.writeString(
                roots, HexFormat.of().formatHex(publicKey) + '\n', StandardCharsets.UTF_8);
        Files.writeString(
                configuration,
                "identity.sqlite-path=identity.sqlite\n"
                        + "identity.registry-bundle-path=registry.sfrb\n"
                        + "identity.authorization-mode=GLOBAL_ONLY\n"
                        + "identity.trust-roots-path=roots.hex\n"
                        + "identity.registry.refresh-source=HTTPS\n"
                        + "identity.registry.https.json-uri=https://unreachable.invalid/releases/v1/registry.json\n"
                        + "identity.registry.https.digest-uri=https://unreachable.invalid/releases/v1/registry.sha256\n"
                        + "identity.registry.https.signature-uri=https://unreachable.invalid/releases/v1/registry.sig\n",
                StandardCharsets.UTF_8);

        int exitCode =
                ServerLauncher.run(
                        new String[] {
                            "--identity-config", configuration.toString(), "--validate-config"
                        });

        assertThat(exitCode).isEqualTo(ServerLauncher.EXIT_OK);
        assertThat(sqlite).doesNotExist();
        assertThat(bundle).doesNotExist();
    }
}

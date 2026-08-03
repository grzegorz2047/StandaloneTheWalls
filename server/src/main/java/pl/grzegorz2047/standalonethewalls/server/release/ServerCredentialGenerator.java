package pl.grzegorz2047.standalonethewalls.server.release;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerFingerprint;
import pl.grzegorz2047.standalonethewalls.transport.bctls.BouncyCastleTlsCryptoFactory;

/** Creates one no-overwrite local credential set for a dedicated alpha server. */
public final class ServerCredentialGenerator {
    public static final String PRIVATE_KEY_FILE = "server-ed25519-key.pk8";
    public static final String CERTIFICATE_FILE = "server-ed25519-certificate.der";
    public static final String REGISTRY_ROOTS_FILE = "registry-trust-roots.hex";
    public static final String FINGERPRINT_FILE = "server-fingerprint.txt";

    private static final Duration NOT_BEFORE_SKEW = Duration.ofMinutes(5);
    private static final Duration VALIDITY = Duration.ofDays(365);
    private static final X500Name SUBJECT =
            new X500Name("CN=Sunderfront Direct Connect Alpha Server");
    private static final Set<PosixFilePermission> PRIVATE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> PUBLIC_PERMISSIONS =
            PosixFilePermissions.fromString("rw-r--r--");

    private ServerCredentialGenerator() {
        throw new AssertionError("No instances");
    }

    public static GeneratedCredentials generate(Path outputDirectory)
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IdentityException,
                    IOException {
        return generate(outputDirectory, Clock.systemUTC(), new SecureRandom());
    }

    static GeneratedCredentials generate(Path outputDirectory, Clock clock, SecureRandom random)
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IdentityException,
                    IOException {
        Path directory = Objects.requireNonNull(outputDirectory, "outputDirectory").normalize();
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(random, "random");
        prepareDirectory(directory);

        Provider provider = BouncyCastleTlsCryptoFactory.provider();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519", provider);
        KeyPair serverKeyPair = keyPairGenerator.generateKeyPair();
        byte[] certificate = createCertificate(serverKeyPair, clock.instant(), random, provider);
        KeyPair registryRoot = keyPairGenerator.generateKeyPair();
        ServerFingerprint fingerprint =
                ServerFingerprint.fromPublicKey(serverKeyPair.getPublic().getEncoded());

        List<Path> created = new ArrayList<>();
        try {
            writeNew(
                    directory.resolve(PRIVATE_KEY_FILE),
                    serverKeyPair.getPrivate().getEncoded(),
                    true,
                    created);
            writeNew(directory.resolve(CERTIFICATE_FILE), certificate, false, created);
            writeNew(
                    directory.resolve(REGISTRY_ROOTS_FILE),
                    (HexFormat.of().formatHex(registryRoot.getPublic().getEncoded()) + "\n")
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    false,
                    created);
            writeNew(
                    directory.resolve(FINGERPRINT_FILE),
                    (fingerprint.value() + "\n")
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    false,
                    created);
            return new GeneratedCredentials(directory.toAbsolutePath().normalize(), fingerprint);
        } catch (IOException failure) {
            for (int index = created.size() - 1; index >= 0; index--) {
                try {
                    Files.deleteIfExists(created.get(index));
                } catch (IOException ignored) {
                    // Preserve the original generation failure.
                }
            }
            throw failure;
        } finally {
            java.util.Arrays.fill(serverKeyPair.getPrivate().getEncoded(), (byte) 0);
        }
    }

    private static byte[] createCertificate(
            KeyPair keyPair, Instant now, SecureRandom random, Provider provider)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        byte[] serialBytes = new byte[20];
        random.nextBytes(serialBytes);
        serialBytes[0] &= 0x7f;
        BigInteger serial = new BigInteger(1, serialBytes).max(BigInteger.ONE);
        java.util.Arrays.fill(serialBytes, (byte) 0);

        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        SUBJECT,
                        serial,
                        Date.from(now.minus(NOT_BEFORE_SKEW)),
                        Date.from(now.plus(VALIDITY)),
                        SUBJECT,
                        keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(
                Extension.extendedKeyUsage,
                false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        ContentSigner signer =
                new JcaContentSignerBuilder("Ed25519")
                        .setProvider(provider)
                        .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate =
                new JcaX509CertificateConverter().setProvider(provider).getCertificate(holder);
        certificate.verify(keyPair.getPublic(), provider);
        return certificate.getEncoded();
    }

    private static void prepareDirectory(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("credential output must be a real directory");
            }
        } else {
            Files.createDirectories(directory);
        }
        for (String fileName :
                List.of(
                        PRIVATE_KEY_FILE,
                        CERTIFICATE_FILE,
                        REGISTRY_ROOTS_FILE,
                        FINGERPRINT_FILE)) {
            if (Files.exists(directory.resolve(fileName), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("credential output already exists");
            }
        }
    }

    private static void writeNew(
            Path target, byte[] content, boolean privateFile, List<Path> created)
            throws IOException {
        Objects.requireNonNull(content, "content");
        FileStore store = Files.getFileStore(target.getParent());
        if (store.supportsFileAttributeView("posix")) {
            Files.createFile(
                    target,
                    PosixFilePermissions.asFileAttribute(
                            privateFile ? PRIVATE_PERMISSIONS : PUBLIC_PERMISSIONS));
            created.add(target);
            Files.write(target, content, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            created.add(target);
        }
    }

    public record GeneratedCredentials(Path directory, ServerFingerprint fingerprint) {
        public GeneratedCredentials {
            directory = Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(fingerprint, "fingerprint");
        }
    }
}

package pl.grzegorz2047.standalonethewalls.server.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleAuthorizationMode;
import pl.grzegorz2047.standalonethewalls.identity.policy.HandleVerificationLevel;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.protocol.MessageType;
import pl.grzegorz2047.standalonethewalls.protocol.ProtocolEnvelope;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ChallengeLedger;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityChallengeService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.IdentityException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerIdentity;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionCodec;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionException;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerReference;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustRecord;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStore;
import pl.grzegorz2047.standalonethewalls.protocol.identity.ServerTrustStoreException;
import pl.grzegorz2047.standalonethewalls.registry.RegistryEntryStatus;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotArtifact;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotEntry;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotJsonCodec;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPayload;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotPolicy;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotProviderException;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotVerifier;
import pl.grzegorz2047.standalonethewalls.registry.RegistryTrustBundle;
import pl.grzegorz2047.standalonethewalls.registry.VerifiedRegistrySnapshot;
import pl.grzegorz2047.standalonethewalls.registry.file.RegistrySnapshotBundleFile;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationCommand;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPermission;
import pl.grzegorz2047.standalonethewalls.server.administration.identity.IdentityAdministrationPrincipal;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntime;
import pl.grzegorz2047.standalonethewalls.server.identity.LocalIdentityRuntimeConfiguration;
import pl.grzegorz2047.standalonethewalls.transport.bctls.AuthenticatedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.BootstrappedReliableSession;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchange;
import pl.grzegorz2047.standalonethewalls.transport.bctls.IdentityExchangeConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.PinnedServerTrustManager;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ClientConnector;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13Connection;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerCredentials;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerListener;
import pl.grzegorz2047.standalonethewalls.transport.bctls.Tls13ServerListenerConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrap;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrapConfig;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsSessionBootstrapException;
import pl.grzegorz2047.standalonethewalls.transport.bctls.TlsTransportException;

class TlsIdentityAdmissionGatewayIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Instant NOW = Instant.parse("2026-08-02T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Provider CRYPTO_PROVIDER = new BouncyCastleProvider();
    private static final ServerReference SERVER_REFERENCE = new ServerReference("localhost:27420");
    private static final TlsSessionBootstrapConfig BOOTSTRAP_CONFIG =
            new TlsSessionBootstrapConfig(Duration.ofSeconds(2));
    private static final IdentityExchangeConfig EXCHANGE_CONFIG =
            new IdentityExchangeConfig(
                    Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(2));

    @TempDir java.nio.file.Path temporaryDirectory;

    @Test
    void localTofuAdmitsFirstUseAndReturningIdentityButRejectsConflictingKey() throws Exception {
        PlayerIdentity identity = PlayerIdentity.generate(new SecureRandom());
        PlayerIdentity conflicting = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("player_one");
        LocalIdentityRuntime runtime =
                openRuntime("local", HandleAuthorizationMode.LOCAL_TOFU, List.of());

        try (Setup setup = setup(runtime, 2, 701L)) {
            ClientAdmission first = connectAndAuthenticate(setup, identity, handle);
            assertThat(first.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
            AuthorizedPlayerSession firstServer = takeAuthorized(setup.gateway());
            assertThat(firstServer.playerId()).isEqualTo(identity.playerId());
            assertThat(firstServer.handle()).isEqualTo(handle);
            assertThat(firstServer.verificationLevel())
                    .isEqualTo(HandleVerificationLevel.LOCAL_UNVERIFIED);
            close(first.client());
            close(firstServer);
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);

            ClientAdmission returning = connectAndAuthenticate(setup, identity, handle);
            assertThat(returning.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.LOCAL_RETURNING_ACCEPTED);
            AuthorizedPlayerSession returningServer = takeAuthorized(setup.gateway());
            close(returning.client());
            close(returningServer);
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);

            ClientAdmission rejected = connectAndAuthenticate(setup, conflicting, handle);
            assertThat(rejected.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.LOCAL_BINDING_CONFLICT);
            assertThat(setup.gateway().authorizedSessions().poll()).isEmpty();
            close(rejected.client());
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);
        }
    }

    @Test
    void globalOnlyAndHybridUseTheVerifiedRegistryWhileHybridStillAdmitsLocalGuests()
            throws Exception {
        PlayerIdentity globalIdentity = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle globalHandle = new CanonicalHandle("global_one");
        RegistrySnapshotEntry globalEntry =
                RegistrySnapshotEntry.create(
                        globalHandle,
                        globalIdentity.playerId(),
                        globalIdentity.publicKeyEncoded(),
                        RegistryEntryStatus.ACTIVE);

        LocalIdentityRuntime globalRuntime =
                openRuntime("global", HandleAuthorizationMode.GLOBAL_ONLY, List.of(globalEntry));
        try (Setup setup = setup(globalRuntime, 2, 702L)) {
            ClientAdmission accepted = connectAndAuthenticate(setup, globalIdentity, globalHandle);
            assertThat(accepted.status()).isEqualTo(PlayerSessionAdmissionStatus.GLOBAL_ACCEPTED);
            AuthorizedPlayerSession server = takeAuthorized(setup.gateway());
            assertThat(server.verificationLevel())
                    .isEqualTo(HandleVerificationLevel.GLOBAL_VERIFIED);
            close(accepted.client());
            close(server);
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);

            ClientAdmission unknown =
                    connectAndAuthenticate(
                            setup,
                            PlayerIdentity.generate(new SecureRandom()),
                            new CanonicalHandle("unknown_one"));
            assertThat(unknown.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.UNKNOWN_GLOBAL_HANDLE);
            close(unknown.client());
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);
        }

        LocalIdentityRuntime hybridRuntime =
                openRuntime("hybrid", HandleAuthorizationMode.HYBRID, List.of(globalEntry));
        try (Setup setup = setup(hybridRuntime, 3, 703L)) {
            ClientAdmission global = connectAndAuthenticate(setup, globalIdentity, globalHandle);
            assertThat(global.status()).isEqualTo(PlayerSessionAdmissionStatus.GLOBAL_ACCEPTED);
            AuthorizedPlayerSession globalServer = takeAuthorized(setup.gateway());
            close(global.client());
            close(globalServer);
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);

            PlayerIdentity localGuest = PlayerIdentity.generate(new SecureRandom());
            ClientAdmission local =
                    connectAndAuthenticate(setup, localGuest, new CanonicalHandle("local_guest"));
            assertThat(local.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
            AuthorizedPlayerSession localServer = takeAuthorized(setup.gateway());
            assertThat(localServer.verificationLevel())
                    .isEqualTo(HandleVerificationLevel.LOCAL_UNVERIFIED);
            close(local.client());
            close(localServer);
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);
        }
    }

    @Test
    void bannedFirstUseIsRejectedBeforeBindingAndListenerAdmissionIsReleased() throws Exception {
        PlayerIdentity banned = PlayerIdentity.generate(new SecureRandom());
        PlayerIdentity replacement = PlayerIdentity.generate(new SecureRandom());
        CanonicalHandle handle = new CanonicalHandle("player_one");
        LocalIdentityRuntime runtime =
                openRuntime("banned", HandleAuthorizationMode.LOCAL_TOFU, List.of());
        runtime.execute(
                new IdentityAdministrationCommand.BanPlayer(
                        banned.playerId(),
                        new LocalHandleAdministrationReason("Confirmed test ban")),
                new IdentityAdministrationPrincipal(
                        new LocalIdentityAdministratorId("console"),
                        Set.of(IdentityAdministrationPermission.MANAGE_PLAYER_BANS)));

        try (Setup setup = setup(runtime, 2, 704L)) {
            ClientAdmission rejected = connectAndAuthenticate(setup, banned, handle);
            assertThat(rejected.status()).isEqualTo(PlayerSessionAdmissionStatus.PLAYER_BANNED);
            close(rejected.client());
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);

            ClientAdmission accepted = connectAndAuthenticate(setup, replacement, handle);
            assertThat(accepted.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
            AuthorizedPlayerSession server = takeAuthorized(setup.gateway());
            assertThat(server.playerId()).isEqualTo(replacement.playerId());
            close(accepted.client());
            close(server);
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);
        }
    }

    @Test
    void fullPreLobbyQueueRejectsWithoutBlockingAndShutdownClosesQueuedLease() throws Exception {
        LocalIdentityRuntime runtime =
                openRuntime("capacity", HandleAuthorizationMode.LOCAL_TOFU, List.of());

        Setup setup = setup(runtime, 1, 705L);
        ClientAdmission first = null;
        ClientAdmission second = null;
        try {
            first =
                    connectAndAuthenticate(
                            setup,
                            PlayerIdentity.generate(new SecureRandom()),
                            new CanonicalHandle("player_one"));
            assertThat(first.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.LOCAL_FIRST_USE_ACCEPTED);
            waitUntil(() -> setup.gateway().authorizedSessions().size() == 1);

            second =
                    connectAndAuthenticate(
                            setup,
                            PlayerIdentity.generate(new SecureRandom()),
                            new CanonicalHandle("player_two"));
            assertThat(second.status())
                    .isEqualTo(PlayerSessionAdmissionStatus.SERVER_CAPACITY_EXCEEDED);
            close(second.client());
            second = null;
            waitUntil(() -> setup.listener().activeConnectionCount() == 1);

            setup.gateway().close();
            waitUntil(() -> setup.listener().activeConnectionCount() == 0);
            assertThat(setup.gateway().authorizedSessions().isClosed()).isTrue();
            assertThat(setup.gateway().authorizedSessions().size()).isZero();
        } finally {
            closeNullable(second);
            closeNullable(first);
            setup.close();
        }
    }

    private Setup setup(LocalIdentityRuntime runtime, int queueCapacity, long serial)
            throws GeneralSecurityException,
                    OperatorCreationException,
                    IOException,
                    IdentityException,
                    ServerTrustStoreException,
                    TlsTransportException {
        TestCertificateMaterial material = TestCertificateMaterial.create(CRYPTO_PROVIDER, serial);
        Tls13ServerCredentials credentials =
                Tls13ServerCredentials.create(
                        material.keyPair().getPrivate(), List.of(material.certificate()));
        InMemoryServerTrustStore trustStore = new InMemoryServerTrustStore();
        ServerTrustService trustService = new ServerTrustService(trustStore);
        trustService.confirmFirstUse(
                SERVER_REFERENCE,
                credentials.serverId(),
                Optional.empty(),
                "identity admission integration test");
        PinnedServerTrustManager trustManager =
                new PinnedServerTrustManager(trustService, SERVER_REFERENCE, Optional.empty());
        IdentityChallengeService challengeService =
                new IdentityChallengeService(
                        new ChallengeLedger(CLOCK, new SecureRandom(), Duration.ofSeconds(5), 32));
        TlsIdentityAdmissionGateway gateway =
                new TlsIdentityAdmissionGateway(
                        runtime,
                        challengeService,
                        BOOTSTRAP_CONFIG,
                        EXCHANGE_CONFIG,
                        queueCapacity,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3),
                        event -> {});
        Tls13ServerListener listener =
                new Tls13ServerListener(
                        new Tls13ServerListenerConfig(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                16,
                                4,
                                4,
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(2)),
                        credentials,
                        gateway,
                        event -> {});
        listener.start();
        return new Setup(trustManager, gateway, listener);
    }

    private ClientAdmission connectAndAuthenticate(
            Setup setup, PlayerIdentity identity, CanonicalHandle handle)
            throws IOException,
                    TlsTransportException,
                    TlsSessionBootstrapException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    PlayerSessionAdmissionException {
        Tls13Connection connection = connectTls(setup.listener(), setup.trustManager());
        BootstrappedReliableSession bootstrapped = null;
        AuthenticatedReliableSession authenticated = null;
        try {
            bootstrapped = TlsSessionBootstrap.connectClientSession(connection, BOOTSTRAP_CONFIG);
            authenticated =
                    IdentityExchange.authenticateClient(
                                    bootstrapped, identity, handle, CLOCK, EXCHANGE_CONFIG)
                            .toCompletableFuture()
                            .get(TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
            ProtocolEnvelope envelope =
                    authenticated
                            .reliableChannel()
                            .receive()
                            .toCompletableFuture()
                            .get(TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)
                            .orElseThrow();
            assertThat(envelope.messageType()).isEqualTo(MessageType.SESSION_ADMISSION_RESULT);
            PlayerSessionAdmissionStatus status =
                    PlayerSessionAdmissionCodec.decode(envelope.payload());
            return new ClientAdmission(authenticated, status);
        } catch (IOException
                | TlsSessionBootstrapException
                | InterruptedException
                | ExecutionException
                | TimeoutException
                | PlayerSessionAdmissionException
                | RuntimeException exception) {
            if (authenticated != null) {
                close(authenticated);
            } else if (bootstrapped != null) {
                close(bootstrapped);
            } else {
                connection.close();
            }
            throw exception;
        }
    }

    private static Tls13Connection connectTls(
            Tls13ServerListener listener, PinnedServerTrustManager trustManager)
            throws IOException, TlsTransportException {
        InetSocketAddress address = listener.localAddress();
        Socket socket = new Socket(address.getAddress(), address.getPort());
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        try {
            return Tls13ClientConnector.connect(socket, trustManager, new SecureRandom());
        } catch (IOException | TlsTransportException | RuntimeException exception) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private LocalIdentityRuntime openRuntime(
            String name, HandleAuthorizationMode mode, List<RegistrySnapshotEntry> registryEntries)
            throws GeneralSecurityException,
                    RegistrySnapshotException,
                    RegistrySnapshotProviderException {
        KeyPair registryRoot = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        LocalIdentityRuntimeConfiguration configuration =
                new LocalIdentityRuntimeConfiguration(
                        temporaryDirectory.resolve(name + ".sqlite"),
                        temporaryDirectory.resolve(name + ".sfrb"),
                        mode);
        RegistryTrustBundle trustBundle =
                RegistryTrustBundle.of(List.of(registryRoot.getPublic().getEncoded()));
        if (!registryEntries.isEmpty()) {
            storeRegistryBundle(configuration.registryBundlePath(), registryRoot, registryEntries);
        }
        return LocalIdentityRuntime.open(
                configuration, trustBundle, RegistrySnapshotPolicy.DEFAULT, CLOCK);
    }

    private static void storeRegistryBundle(
            java.nio.file.Path path, KeyPair root, List<RegistrySnapshotEntry> entries)
            throws RegistrySnapshotException,
                    GeneralSecurityException,
                    RegistrySnapshotProviderException {
        RegistrySnapshotPayload payload =
                new RegistrySnapshotPayload(
                        1L,
                        NOW,
                        RegistryRootId.fromPublicKey(root.getPublic().getEncoded()),
                        entries);
        byte[] canonicalJson = RegistrySnapshotJsonCodec.encode(payload);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(root.getPrivate());
        signer.update(canonicalJson);
        RegistrySnapshotArtifact artifact =
                new RegistrySnapshotArtifact(canonicalJson, digest, signer.sign());
        RegistryTrustBundle trustBundle =
                RegistryTrustBundle.of(List.of(root.getPublic().getEncoded()));
        VerifiedRegistrySnapshot verified =
                new RegistrySnapshotVerifier(CLOCK)
                        .verify(artifact, trustBundle, RegistrySnapshotPolicy.DEFAULT);
        new RegistrySnapshotBundleFile(path).storeVerified(artifact, verified);
    }

    private static AuthorizedPlayerSession takeAuthorized(TlsIdentityAdmissionGateway gateway)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<AuthorizedPlayerSession> session = gateway.authorizedSessions().poll();
            if (session.isPresent()) {
                return session.orElseThrow();
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("authorized player session handoff timed out");
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition timed out");
            }
            Thread.sleep(10L);
        }
    }

    private static void close(AuthenticatedReliableSession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void close(BootstrappedReliableSession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void close(AuthorizedPlayerSession session)
            throws InterruptedException, ExecutionException, TimeoutException {
        session.closeAsync().toCompletableFuture().get(TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static void closeNullable(ClientAdmission admission) {
        if (admission == null) {
            return;
        }
        try {
            close(admission.client());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
            // Cleanup must preserve the primary test failure.
        }
    }

    private record ClientAdmission(
            AuthenticatedReliableSession client, PlayerSessionAdmissionStatus status) {}

    private record Setup(
            PinnedServerTrustManager trustManager,
            TlsIdentityAdmissionGateway gateway,
            Tls13ServerListener listener)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                listener.close();
            } finally {
                gateway.close();
            }
        }
    }

    private static final class InMemoryServerTrustStore implements ServerTrustStore {
        private final Map<ServerReference, ServerTrustRecord> records = new ConcurrentHashMap<>();

        @Override
        public Optional<ServerTrustRecord> find(ServerReference reference) {
            return Optional.ofNullable(records.get(reference));
        }

        @Override
        public boolean saveIfAbsent(ServerTrustRecord record) {
            return records.putIfAbsent(record.reference(), record) == null;
        }

        @Override
        public boolean replace(ServerTrustRecord expected, ServerTrustRecord replacement) {
            return records.replace(expected.reference(), expected, replacement);
        }
    }

    private record TestCertificateMaterial(
            KeyPair keyPair, java.security.cert.X509Certificate certificate) {
        private static TestCertificateMaterial create(Provider provider, long serial)
                throws GeneralSecurityException, OperatorCreationException, IOException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519", provider);
            KeyPair keyPair = generator.generateKeyPair();
            X500Name subject = new X500Name("CN=Sunderfront Identity Admission Test");
            JcaX509v3CertificateBuilder builder =
                    new JcaX509v3CertificateBuilder(
                            subject,
                            BigInteger.valueOf(serial),
                            Date.from(NOW.minus(Duration.ofMinutes(1))),
                            Date.from(NOW.plus(Duration.ofDays(30))),
                            subject,
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
            java.security.cert.X509Certificate certificate =
                    new JcaX509CertificateConverter().setProvider(provider).getCertificate(holder);
            certificate.verify(keyPair.getPublic(), provider);
            return new TestCertificateMaterial(keyPair, certificate);
        }
    }
}

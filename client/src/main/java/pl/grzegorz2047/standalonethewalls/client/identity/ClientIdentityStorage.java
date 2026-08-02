package pl.grzegorz2047.standalonethewalls.client.identity;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit client data-directory composition for identity and server trust persistence. */
public final class ClientIdentityStorage {
    public static final String PLAYER_IDENTITY_FILE_NAME = "player-identity.sfki";
    public static final String SERVER_TRUST_FILE_NAME = "server-trust.sftr";

    private final Path dataDirectory;
    private final FilePlayerIdentityStore playerIdentityStore;
    private final FileServerTrustStore serverTrustStore;

    public ClientIdentityStorage(Path dataDirectory) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        if (this.dataDirectory.getParent() == null || this.dataDirectory.getFileName() == null) {
            throw new IllegalArgumentException("dataDirectory must name an explicit directory");
        }
        playerIdentityStore =
                new FilePlayerIdentityStore(
                        this.dataDirectory.resolve(PLAYER_IDENTITY_FILE_NAME));
        serverTrustStore =
                new FileServerTrustStore(this.dataDirectory.resolve(SERVER_TRUST_FILE_NAME));
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public FilePlayerIdentityStore playerIdentityStore() {
        return playerIdentityStore;
    }

    public FileServerTrustStore serverTrustStore() {
        return serverTrustStore;
    }
}

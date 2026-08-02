package pl.grzegorz2047.standalonethewalls.client.identity;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit client data-directory composition for identity and server trust persistence. */
public final class ClientIdentityStorage {
    public static final String PLAYER_IDENTITY_FILE_NAME = "player-identity.sfki";
    public static final String SERVER_TRUST_FILE_NAME = "server-trust.sftr";

    private final Path dataDirectory;

    public ClientIdentityStorage(Path dataDirectory) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        if (this.dataDirectory.getParent() == null || this.dataDirectory.getFileName() == null) {
            throw new IllegalArgumentException("dataDirectory must name an explicit directory");
        }
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public FilePlayerIdentityStore playerIdentityStore() {
        return new FilePlayerIdentityStore(dataDirectory.resolve(PLAYER_IDENTITY_FILE_NAME));
    }

    public FileServerTrustStore serverTrustStore() {
        return new FileServerTrustStore(dataDirectory.resolve(SERVER_TRUST_FILE_NAME));
    }
}

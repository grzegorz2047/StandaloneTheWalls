package pl.grzegorz2047.standalonethewalls.registry;

/** Source-specific load failure that carries no trust decision. */
public final class RegistrySnapshotProviderException extends Exception {
    private static final long serialVersionUID = 1L;

    public RegistrySnapshotProviderException(String message) {
        super(message);
    }

    public RegistrySnapshotProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

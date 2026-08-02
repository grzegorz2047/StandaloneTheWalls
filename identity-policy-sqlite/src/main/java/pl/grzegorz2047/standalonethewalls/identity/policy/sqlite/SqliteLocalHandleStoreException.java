package pl.grzegorz2047.standalonethewalls.identity.policy.sqlite;

/** Fail-closed persistence or schema failure in the SQLite local identity adapter. */
public final class SqliteLocalHandleStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SqliteLocalHandleStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqliteLocalHandleStoreException(String message) {
        super(message);
    }
}

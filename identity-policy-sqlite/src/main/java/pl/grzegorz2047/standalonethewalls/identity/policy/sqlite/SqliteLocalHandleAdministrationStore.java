package pl.grzegorz2047.standalonethewalls.identity.policy.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalHandleBindingStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAuditAction;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAuditEvent;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBinding;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBindingResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Transactional SQLite persistence for local bindings and administrative audit events. */
public final class SqliteLocalHandleAdministrationStore implements LocalHandleAdministrationStore {
    public static final int SCHEMA_VERSION = 1;
    public static final int DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000;
    public static final int MAXIMUM_BUSY_TIMEOUT_MILLIS = 60_000;

    private static final String SCHEMA_TABLE = "local_identity_schema";
    private static final String BINDINGS_TABLE = "local_handle_bindings";
    private static final String AUDIT_TABLE = "local_handle_audit";
    private static final String AUDIT_UPDATE_TRIGGER = "local_handle_audit_no_update";
    private static final String AUDIT_DELETE_TRIGGER = "local_handle_audit_no_delete";

    private final Path path;
    private final int maximumBindings;
    private final int maximumAuditEvents;
    private final int busyTimeoutMillis;

    public SqliteLocalHandleAdministrationStore(Path path) {
        this(
                path,
                InMemoryLocalHandleBindingStore.DEFAULT_MAXIMUM_BINDINGS,
                InMemoryLocalHandleBindingStore.DEFAULT_MAXIMUM_AUDIT_EVENTS,
                DEFAULT_BUSY_TIMEOUT_MILLIS);
    }

    public SqliteLocalHandleAdministrationStore(
            Path path, int maximumBindings, int maximumAuditEvents, int busyTimeoutMillis) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (this.path.getFileName() == null) {
            throw new IllegalArgumentException("path must identify a SQLite database file");
        }
        if (maximumBindings < 1
                || maximumBindings > InMemoryLocalHandleBindingStore.ABSOLUTE_MAXIMUM_BINDINGS) {
            throw new IllegalArgumentException("maximumBindings is outside the safe range");
        }
        if (maximumAuditEvents < 1
                || maximumAuditEvents
                        > InMemoryLocalHandleBindingStore.ABSOLUTE_MAXIMUM_AUDIT_EVENTS) {
            throw new IllegalArgumentException("maximumAuditEvents is outside the safe range");
        }
        if (busyTimeoutMillis < 1 || busyTimeoutMillis > MAXIMUM_BUSY_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("busyTimeoutMillis is outside the safe range");
        }
        this.maximumBindings = maximumBindings;
        this.maximumAuditEvents = maximumAuditEvents;
        this.busyTimeoutMillis = busyTimeoutMillis;
        preparePath();
        loadDriver();
        initializeSchema();
    }

    @Override
    public LocalHandleBindingResult bindOrVerify(CanonicalHandle handle, PlayerId playerId) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        return writeTransaction(
                connection -> {
                    Optional<PlayerId> existing = find(connection, canonicalHandle);
                    if (existing.isPresent()) {
                        return existing.orElseThrow().equals(identity)
                                ? LocalHandleBindingResult.MATCHED
                                : LocalHandleBindingResult.CONFLICT;
                    }
                    if (countBindings(connection) >= maximumBindings) {
                        return LocalHandleBindingResult.CAPACITY_EXCEEDED;
                    }
                    insertBinding(connection, canonicalHandle, identity);
                    return LocalHandleBindingResult.BOUND;
                });
    }

    @Override
    public LocalHandleAdministrationResult reserve(
            CanonicalHandle handle,
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        return writeTransaction(
                connection -> {
                    Optional<PlayerId> existing = find(connection, canonicalHandle);
                    if (existing.isPresent()) {
                        return existing.orElseThrow().equals(identity)
                                ? LocalHandleAdministrationResult.ALREADY_MATCHED
                                : LocalHandleAdministrationResult.CONFLICT;
                    }
                    if (!hasBindingAndAuditCapacity(connection)) {
                        return LocalHandleAdministrationResult.CAPACITY_EXCEEDED;
                    }
                    insertBinding(connection, canonicalHandle, identity);
                    insertAudit(
                            connection,
                            timestamp,
                            administrator,
                            LocalHandleAuditAction.RESERVE,
                            canonicalHandle,
                            Optional.empty(),
                            Optional.of(identity),
                            auditReason);
                    return LocalHandleAdministrationResult.RESERVED;
                });
    }

    @Override
    public LocalHandleAdministrationResult unbind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId expected = Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        return writeTransaction(
                connection -> {
                    Optional<PlayerId> existing = find(connection, canonicalHandle);
                    if (existing.isEmpty()) {
                        return LocalHandleAdministrationResult.NOT_FOUND;
                    }
                    PlayerId current = existing.orElseThrow();
                    if (!current.equals(expected)) {
                        return LocalHandleAdministrationResult.EXPECTATION_MISMATCH;
                    }
                    if (!hasAuditCapacity(connection)) {
                        return LocalHandleAdministrationResult.CAPACITY_EXCEEDED;
                    }
                    deleteBinding(connection, canonicalHandle, expected);
                    insertAudit(
                            connection,
                            timestamp,
                            administrator,
                            LocalHandleAuditAction.UNBIND,
                            canonicalHandle,
                            Optional.of(current),
                            Optional.empty(),
                            auditReason);
                    return LocalHandleAdministrationResult.UNBOUND;
                });
    }

    @Override
    public LocalHandleAdministrationResult rebind(
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            PlayerId replacementPlayerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        PlayerId expected = Objects.requireNonNull(expectedPlayerId, "expectedPlayerId");
        PlayerId replacement = Objects.requireNonNull(replacementPlayerId, "replacementPlayerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        return writeTransaction(
                connection -> {
                    Optional<PlayerId> existing = find(connection, canonicalHandle);
                    if (existing.isEmpty()) {
                        return LocalHandleAdministrationResult.NOT_FOUND;
                    }
                    PlayerId current = existing.orElseThrow();
                    if (!current.equals(expected)) {
                        return LocalHandleAdministrationResult.EXPECTATION_MISMATCH;
                    }
                    if (current.equals(replacement)) {
                        return LocalHandleAdministrationResult.SAME_PLAYER;
                    }
                    if (!hasAuditCapacity(connection)) {
                        return LocalHandleAdministrationResult.CAPACITY_EXCEEDED;
                    }
                    updateBinding(connection, canonicalHandle, expected, replacement);
                    insertAudit(
                            connection,
                            timestamp,
                            administrator,
                            LocalHandleAuditAction.REBIND,
                            canonicalHandle,
                            Optional.of(current),
                            Optional.of(replacement),
                            auditReason);
                    return LocalHandleAdministrationResult.REBOUND;
                });
    }

    @Override
    public Optional<PlayerId> find(CanonicalHandle handle) {
        CanonicalHandle canonicalHandle = Objects.requireNonNull(handle, "handle");
        return read(connection -> find(connection, canonicalHandle));
    }

    @Override
    public List<LocalHandleBinding> bindings() {
        return read(
                connection -> {
                    List<LocalHandleBinding> values = new ArrayList<>();
                    try (PreparedStatement statement =
                                    connection.prepareStatement(
                                            "SELECT handle, player_id FROM "
                                                    + BINDINGS_TABLE
                                                    + " ORDER BY handle");
                            ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            if (values.size() >= maximumBindings) {
                                throw new SQLException(
                                        "binding result exceeds configured capacity");
                            }
                            values.add(
                                    new LocalHandleBinding(
                                            new CanonicalHandle(result.getString(1)),
                                            new PlayerId(result.getString(2))));
                        }
                    }
                    return List.copyOf(values);
                });
    }

    @Override
    public List<LocalHandleAuditEvent> auditEvents() {
        return read(
                connection -> {
                    List<LocalHandleAuditEvent> values = new ArrayList<>();
                    long previousSequence = 0L;
                    try (PreparedStatement statement =
                                    connection.prepareStatement(
                                            "SELECT sequence, occurred_at, administrator_id, action, "
                                                    + "handle, previous_player_id, new_player_id, reason "
                                                    + "FROM "
                                                    + AUDIT_TABLE
                                                    + " ORDER BY sequence");
                            ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            if (values.size() >= maximumAuditEvents) {
                                throw new SQLException("audit result exceeds configured capacity");
                            }
                            long sequence = result.getLong(1);
                            if (sequence <= previousSequence) {
                                throw new SQLException("audit sequence is not strictly increasing");
                            }
                            previousSequence = sequence;
                            values.add(readAuditEvent(result, sequence));
                        }
                    }
                    return List.copyOf(values);
                });
    }

    public Path path() {
        return path;
    }

    private void preparePath() {
        Path parent = path.getParent();
        if (parent == null) {
            throw new SqliteLocalHandleStoreException(
                    "SQLite local identity path has no parent directory");
        }
        try {
            Files.createDirectories(parent);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(path)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new SqliteLocalHandleStoreException(
                            "SQLite local identity path must be a regular file");
                }
            }
        } catch (IOException | SecurityException exception) {
            throw new SqliteLocalHandleStoreException(
                    "SQLite local identity path could not be prepared", exception);
        }
    }

    private static void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SqliteLocalHandleStoreException(
                    "SQLite JDBC driver is unavailable", exception);
        }
    }

    private void initializeSchema() {
        writeTransaction(
                connection -> {
                    requireIntegrity(connection);
                    boolean schemaExists = objectExists(connection, "table", SCHEMA_TABLE);
                    boolean bindingsExist = objectExists(connection, "table", BINDINGS_TABLE);
                    boolean auditExists = objectExists(connection, "table", AUDIT_TABLE);
                    if (!schemaExists) {
                        if (bindingsExist || auditExists) {
                            throw new SQLException("identity tables exist without schema metadata");
                        }
                        createSchema(connection);
                    }
                    validateSchema(connection);
                    requireIntegrity(connection);
                    return null;
                });
    }

    private void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE "
                            + SCHEMA_TABLE
                            + " (singleton INTEGER PRIMARY KEY CHECK (singleton = 1), "
                            + "version INTEGER NOT NULL CHECK (version >= 1))");
            statement.executeUpdate(
                    "INSERT INTO "
                            + SCHEMA_TABLE
                            + " (singleton, version) VALUES (1, "
                            + SCHEMA_VERSION
                            + ")");
            statement.executeUpdate(
                    "CREATE TABLE "
                            + BINDINGS_TABLE
                            + " (handle TEXT PRIMARY KEY NOT NULL, player_id TEXT NOT NULL) "
                            + "WITHOUT ROWID");
            statement.executeUpdate(
                    "CREATE TABLE "
                            + AUDIT_TABLE
                            + " (sequence INTEGER PRIMARY KEY, occurred_at TEXT NOT NULL, "
                            + "administrator_id TEXT NOT NULL, action TEXT NOT NULL, "
                            + "handle TEXT NOT NULL, previous_player_id TEXT, new_player_id TEXT, "
                            + "reason TEXT NOT NULL, "
                            + "CHECK (action IN ('RESERVE', 'UNBIND', 'REBIND')), "
                            + "CHECK ((action = 'RESERVE' AND previous_player_id IS NULL "
                            + "AND new_player_id IS NOT NULL) OR "
                            + "(action = 'UNBIND' AND previous_player_id IS NOT NULL "
                            + "AND new_player_id IS NULL) OR "
                            + "(action = 'REBIND' AND previous_player_id IS NOT NULL "
                            + "AND new_player_id IS NOT NULL "
                            + "AND previous_player_id <> new_player_id)))");
            statement.executeUpdate(
                    "CREATE TRIGGER "
                            + AUDIT_UPDATE_TRIGGER
                            + " BEFORE UPDATE ON "
                            + AUDIT_TABLE
                            + " BEGIN SELECT RAISE(ABORT, 'local identity audit is append-only'); END");
            statement.executeUpdate(
                    "CREATE TRIGGER "
                            + AUDIT_DELETE_TRIGGER
                            + " BEFORE DELETE ON "
                            + AUDIT_TABLE
                            + " BEGIN SELECT RAISE(ABORT, 'local identity audit is append-only'); END");
        }
    }

    private void validateSchema(Connection connection) throws SQLException {
        if (!objectExists(connection, "table", SCHEMA_TABLE)
                || !objectExists(connection, "table", BINDINGS_TABLE)
                || !objectExists(connection, "table", AUDIT_TABLE)
                || !objectExists(connection, "trigger", AUDIT_UPDATE_TRIGGER)
                || !objectExists(connection, "trigger", AUDIT_DELETE_TRIGGER)) {
            throw new SQLException("SQLite local identity schema is incomplete");
        }
        int rowCount = 0;
        int version = 0;
        try (PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT singleton, version FROM " + SCHEMA_TABLE);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rowCount++;
                if (result.getInt(1) != 1) {
                    throw new SQLException("SQLite local identity schema metadata is invalid");
                }
                version = result.getInt(2);
            }
        }
        if (rowCount != 1) {
            throw new SQLException("SQLite local identity schema metadata is incomplete");
        }
        if (version > SCHEMA_VERSION) {
            throw new SQLException("SQLite local identity schema is newer than this server");
        }
        if (version != SCHEMA_VERSION) {
            throw new SQLException("SQLite local identity schema version is unsupported");
        }
        validateRequiredColumns(connection);
        if (countBindings(connection) > maximumBindings
                || countAuditEvents(connection) > maximumAuditEvents) {
            throw new SQLException("SQLite local identity data exceeds configured capacity");
        }
    }

    private static void validateRequiredColumns(Connection connection) throws SQLException {
        try (PreparedStatement bindings =
                        connection.prepareStatement(
                                "SELECT handle, player_id FROM " + BINDINGS_TABLE + " WHERE 0");
                PreparedStatement audit =
                        connection.prepareStatement(
                                "SELECT sequence, occurred_at, administrator_id, action, handle, "
                                        + "previous_player_id, new_player_id, reason FROM "
                                        + AUDIT_TABLE
                                        + " WHERE 0")) {
            bindings.executeQuery().close();
            audit.executeQuery().close();
        }
    }

    private static void requireIntegrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equals(result.getString(1)) || result.next()) {
                throw new SQLException("SQLite local identity integrity check failed");
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException("SQLite local identity foreign key check failed");
            }
        }
    }

    private static boolean objectExists(Connection connection, String type, String name)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT 1 FROM sqlite_master WHERE type = ? AND name = ?")) {
            statement.setString(1, type);
            statement.setString(2, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private Optional<PlayerId> find(Connection connection, CanonicalHandle handle)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT player_id FROM " + BINDINGS_TABLE + " WHERE handle = ?")) {
            statement.setString(1, handle.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                PlayerId playerId = new PlayerId(result.getString(1));
                if (result.next()) {
                    throw new SQLException("canonical handle has multiple local bindings");
                }
                return Optional.of(playerId);
            }
        }
    }

    private static void insertBinding(
            Connection connection, CanonicalHandle handle, PlayerId playerId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO " + BINDINGS_TABLE + " (handle, player_id) VALUES (?, ?)")) {
            statement.setString(1, handle.value());
            statement.setString(2, playerId.value());
            requireOneRow(statement.executeUpdate(), "local binding insert");
        }
    }

    private static void deleteBinding(
            Connection connection, CanonicalHandle handle, PlayerId expectedPlayerId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "DELETE FROM " + BINDINGS_TABLE + " WHERE handle = ? AND player_id = ?")) {
            statement.setString(1, handle.value());
            statement.setString(2, expectedPlayerId.value());
            requireOneRow(statement.executeUpdate(), "local binding delete");
        }
    }

    private static void updateBinding(
            Connection connection,
            CanonicalHandle handle,
            PlayerId expectedPlayerId,
            PlayerId replacementPlayerId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "UPDATE "
                                + BINDINGS_TABLE
                                + " SET player_id = ? WHERE handle = ? AND player_id = ?")) {
            statement.setString(1, replacementPlayerId.value());
            statement.setString(2, handle.value());
            statement.setString(3, expectedPlayerId.value());
            requireOneRow(statement.executeUpdate(), "local binding update");
        }
    }

    private void insertAudit(
            Connection connection,
            Instant occurredAt,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAuditAction action,
            CanonicalHandle handle,
            Optional<PlayerId> previousPlayerId,
            Optional<PlayerId> newPlayerId,
            LocalHandleAdministrationReason reason)
            throws SQLException {
        long sequence = nextAuditSequence(connection);
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO "
                                + AUDIT_TABLE
                                + " (sequence, occurred_at, administrator_id, action, handle, "
                                + "previous_player_id, new_player_id, reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, sequence);
            statement.setString(2, occurredAt.toString());
            statement.setString(3, administratorId.value());
            statement.setString(4, action.name());
            statement.setString(5, handle.value());
            setOptionalPlayerId(statement, 6, previousPlayerId);
            setOptionalPlayerId(statement, 7, newPlayerId);
            statement.setString(8, reason.value());
            requireOneRow(statement.executeUpdate(), "local audit insert");
        }
    }

    private static void setOptionalPlayerId(
            PreparedStatement statement, int index, Optional<PlayerId> playerId)
            throws SQLException {
        if (playerId.isPresent()) {
            statement.setString(index, playerId.orElseThrow().value());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static LocalHandleAuditEvent readAuditEvent(ResultSet result, long sequence)
            throws SQLException {
        String previous = result.getString(6);
        String replacement = result.getString(7);
        return new LocalHandleAuditEvent(
                sequence,
                Instant.parse(result.getString(2)),
                new LocalIdentityAdministratorId(result.getString(3)),
                LocalHandleAuditAction.valueOf(result.getString(4)),
                new CanonicalHandle(result.getString(5)),
                previous == null ? Optional.empty() : Optional.of(new PlayerId(previous)),
                replacement == null ? Optional.empty() : Optional.of(new PlayerId(replacement)),
                new LocalHandleAdministrationReason(result.getString(8)));
    }

    private boolean hasBindingAndAuditCapacity(Connection connection) throws SQLException {
        return countBindings(connection) < maximumBindings && hasAuditCapacity(connection);
    }

    private boolean hasAuditCapacity(Connection connection) throws SQLException {
        return countAuditEvents(connection) < maximumAuditEvents;
    }

    private static long countBindings(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery("SELECT COUNT(*) FROM local_handle_bindings")) {
            return readCount(result);
        }
    }

    private static long countAuditEvents(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery("SELECT COUNT(*) FROM local_handle_audit")) {
            return readCount(result);
        }
    }

    private static long readCount(ResultSet result) throws SQLException {
        if (!result.next()) {
            throw new SQLException("SQLite count query returned no row");
        }
        long count = result.getLong(1);
        if (count < 0L || result.next()) {
            throw new SQLException("SQLite count query returned an invalid result");
        }
        return count;
    }

    private static long nextAuditSequence(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT COALESCE(MAX(sequence), 0) FROM " + AUDIT_TABLE)) {
            if (!result.next()) {
                throw new SQLException("SQLite audit sequence query returned no row");
            }
            long current = result.getLong(1);
            if (current < 0L || current == Long.MAX_VALUE || result.next()) {
                throw new SQLException("SQLite audit sequence cannot advance safely");
            }
            return current + 1L;
        }
    }

    private static void requireOneRow(int changedRows, String operation) throws SQLException {
        if (changedRows != 1) {
            throw new SQLException(operation + " did not change exactly one row");
        }
    }

    private <T> T read(SqlOperation<T> operation) {
        try (Connection connection = openConnection()) {
            return operation.execute(connection);
        } catch (SQLException | IllegalArgumentException exception) {
            throw new SqliteLocalHandleStoreException(
                    "SQLite local identity read failed", exception);
        }
    }

    private <T> T writeTransaction(SqlOperation<T> operation) {
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
            }
            try {
                T result = operation.execute(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("COMMIT");
                }
                return result;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new SqliteLocalHandleStoreException(
                    "SQLite local identity transaction failed", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("busy_timeout", Integer.toString(busyTimeoutMillis));
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path, properties);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
        return connection;
    }

    private static void rollback(Connection connection, Throwable failure) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T execute(Connection connection) throws SQLException;
    }
}

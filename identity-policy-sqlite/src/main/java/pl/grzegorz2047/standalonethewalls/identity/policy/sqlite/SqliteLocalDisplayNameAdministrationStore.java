package pl.grzegorz2047.standalonethewalls.identity.policy.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalDisplayNameStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalPlayerBanStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayName;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAdministrationStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAssignment;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAuditAction;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAuditEvent;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameExpectation;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Transactional SQLite persistence for local presentation names and their audit trail. */
public final class SqliteLocalDisplayNameAdministrationStore
        implements LocalDisplayNameAdministrationStore {
    public static final int SCHEMA_VERSION = 3;

    private static final String SCHEMA_TABLE = "local_identity_schema";
    private static final String DISPLAY_NAMES_TABLE = "local_player_display_names";
    private static final String AUDIT_TABLE = "local_player_display_name_audit";
    private static final String AUDIT_UPDATE_TRIGGER = "local_player_display_name_audit_no_update";
    private static final String AUDIT_DELETE_TRIGGER = "local_player_display_name_audit_no_delete";

    private final Path path;
    private final int maximumDisplayNames;
    private final int maximumAuditEvents;
    private final int busyTimeoutMillis;

    public SqliteLocalDisplayNameAdministrationStore(Path path) {
        this(
                path,
                InMemoryLocalDisplayNameStore.DEFAULT_MAXIMUM_DISPLAY_NAMES,
                InMemoryLocalDisplayNameStore.DEFAULT_MAXIMUM_AUDIT_EVENTS,
                SqliteLocalHandleAdministrationStore.DEFAULT_BUSY_TIMEOUT_MILLIS);
    }

    public SqliteLocalDisplayNameAdministrationStore(
            Path path, int maximumDisplayNames, int maximumAuditEvents, int busyTimeoutMillis) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (this.path.getFileName() == null || this.path.getParent() == null) {
            throw new IllegalArgumentException("path must identify a SQLite database file");
        }
        if (maximumDisplayNames < 1
                || maximumDisplayNames
                        > InMemoryLocalDisplayNameStore.ABSOLUTE_MAXIMUM_DISPLAY_NAMES) {
            throw new IllegalArgumentException("maximumDisplayNames is outside the safe range");
        }
        if (maximumAuditEvents < 1
                || maximumAuditEvents
                        > InMemoryLocalDisplayNameStore.ABSOLUTE_MAXIMUM_AUDIT_EVENTS) {
            throw new IllegalArgumentException("maximumAuditEvents is outside the safe range");
        }
        if (busyTimeoutMillis < 1
                || busyTimeoutMillis
                        > SqliteLocalHandleAdministrationStore.MAXIMUM_BUSY_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("busyTimeoutMillis is outside the safe range");
        }
        this.maximumDisplayNames = maximumDisplayNames;
        this.maximumAuditEvents = maximumAuditEvents;
        this.busyTimeoutMillis = busyTimeoutMillis;
        new SqliteLocalPlayerBanAdministrationStore(
                this.path,
                InMemoryLocalPlayerBanStore.DEFAULT_MAXIMUM_BANS,
                InMemoryLocalPlayerBanStore.DEFAULT_MAXIMUM_AUDIT_EVENTS,
                busyTimeoutMillis);
        initializeSchema();
    }

    @Override
    public LocalDisplayNameAdministrationResult setDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            LocalDisplayName displayName,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalDisplayNameExpectation expected = Objects.requireNonNull(expectation, "expectation");
        LocalDisplayName replacement = Objects.requireNonNull(displayName, "displayName");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        return writeTransaction(
                connection -> {
                    Optional<LocalDisplayName> current = find(connection, identity);
                    if (!expected.matches(current)) {
                        return current.isEmpty()
                                ? LocalDisplayNameAdministrationResult.NOT_FOUND
                                : LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH;
                    }
                    if (current.filter(replacement::equals).isPresent()) {
                        return LocalDisplayNameAdministrationResult.UNCHANGED;
                    }
                    if ((current.isEmpty() && countDisplayNames(connection) >= maximumDisplayNames)
                            || countAuditEvents(connection) >= maximumAuditEvents) {
                        return LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED;
                    }
                    if (current.isEmpty()) {
                        insertDisplayName(connection, identity, replacement);
                    } else {
                        updateDisplayName(connection, identity, current.orElseThrow(), replacement);
                    }
                    insertAudit(
                            connection,
                            timestamp,
                            administrator,
                            LocalDisplayNameAuditAction.SET,
                            identity,
                            current,
                            Optional.of(replacement),
                            auditReason);
                    return LocalDisplayNameAdministrationResult.APPLIED;
                });
    }

    @Override
    public LocalDisplayNameAdministrationResult clearDisplayName(
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalDisplayNameExpectation expected = Objects.requireNonNull(expectation, "expectation");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        return writeTransaction(
                connection -> {
                    Optional<LocalDisplayName> current = find(connection, identity);
                    if (current.isEmpty()) {
                        return LocalDisplayNameAdministrationResult.NOT_FOUND;
                    }
                    if (!expected.matches(current)) {
                        return LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH;
                    }
                    if (countAuditEvents(connection) >= maximumAuditEvents) {
                        return LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED;
                    }
                    deleteDisplayName(connection, identity, current.orElseThrow());
                    insertAudit(
                            connection,
                            timestamp,
                            administrator,
                            LocalDisplayNameAuditAction.CLEAR,
                            identity,
                            current,
                            Optional.empty(),
                            auditReason);
                    return LocalDisplayNameAdministrationResult.APPLIED;
                });
    }

    @Override
    public Optional<LocalDisplayName> find(PlayerId playerId) {
        return read(connection -> find(connection, Objects.requireNonNull(playerId, "playerId")));
    }

    @Override
    public List<LocalDisplayNameAssignment> displayNames() {
        return read(
                connection -> {
                    List<LocalDisplayNameAssignment> values = new ArrayList<>();
                    try (PreparedStatement statement =
                                    connection.prepareStatement(
                                            "SELECT player_id, display_name FROM "
                                                    + DISPLAY_NAMES_TABLE
                                                    + " ORDER BY player_id");
                            ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            if (values.size() >= maximumDisplayNames) {
                                throw new SQLException(
                                        "display name result exceeds configured capacity");
                            }
                            values.add(
                                    new LocalDisplayNameAssignment(
                                            new PlayerId(result.getString(1)),
                                            new LocalDisplayName(result.getString(2))));
                        }
                    }
                    return List.copyOf(values);
                });
    }

    @Override
    public List<LocalDisplayNameAuditEvent> auditEvents() {
        return read(
                connection -> {
                    List<LocalDisplayNameAuditEvent> values = new ArrayList<>();
                    long previousSequence = 0L;
                    try (PreparedStatement statement =
                                    connection.prepareStatement(
                                            "SELECT sequence, occurred_at, administrator_id, action, "
                                                    + "player_id, previous_display_name, "
                                                    + "new_display_name, reason FROM "
                                                    + AUDIT_TABLE
                                                    + " ORDER BY sequence");
                            ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            if (values.size() >= maximumAuditEvents) {
                                throw new SQLException(
                                        "display name audit result exceeds configured capacity");
                            }
                            long sequence = result.getLong(1);
                            if (sequence <= previousSequence) {
                                throw new SQLException(
                                        "display name audit sequence is not strictly increasing");
                            }
                            previousSequence = sequence;
                            values.add(readAuditEvent(result, sequence));
                        }
                    }
                    return List.copyOf(values);
                });
    }

    private void initializeSchema() {
        writeTransaction(
                connection -> {
                    requireIntegrity(connection);
                    int version = readSchemaVersion(connection);
                    if (version > SCHEMA_VERSION) {
                        throw new SQLException(
                                "SQLite local identity schema is newer than this server");
                    }
                    if (version == 2) {
                        if (hasAnyDisplayNameSchemaObject(connection)) {
                            throw new SQLException(
                                    "SQLite display name objects exist before schema migration");
                        }
                        createSchema(connection);
                        updateSchemaVersion(connection, 2, SCHEMA_VERSION);
                    } else if (version != SCHEMA_VERSION) {
                        throw new SQLException(
                                "SQLite local identity schema version is unsupported");
                    }
                    validateSchema(connection);
                    requireIntegrity(connection);
                    return null;
                });
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE local_player_display_names ("
                            + "player_id TEXT PRIMARY KEY NOT NULL, "
                            + "display_name TEXT NOT NULL, "
                            + "CHECK (length(display_name) BETWEEN 1 AND 64), "
                            + "CHECK (length(CAST(display_name AS BLOB)) BETWEEN 1 AND 192)) "
                            + "WITHOUT ROWID");
            statement.executeUpdate(
                    "CREATE TABLE local_player_display_name_audit ("
                            + "sequence INTEGER PRIMARY KEY, occurred_at TEXT NOT NULL, "
                            + "administrator_id TEXT NOT NULL, action TEXT NOT NULL, "
                            + "player_id TEXT NOT NULL, previous_display_name TEXT, "
                            + "new_display_name TEXT, reason TEXT NOT NULL, "
                            + "CHECK (previous_display_name IS NULL OR "
                            + "(length(previous_display_name) BETWEEN 1 AND 64 AND "
                            + "length(CAST(previous_display_name AS BLOB)) BETWEEN 1 AND 192)), "
                            + "CHECK (new_display_name IS NULL OR "
                            + "(length(new_display_name) BETWEEN 1 AND 64 AND "
                            + "length(CAST(new_display_name AS BLOB)) BETWEEN 1 AND 192)), "
                            + "CHECK (action IN ('SET', 'CLEAR')), "
                            + "CHECK ((action = 'SET' AND new_display_name IS NOT NULL "
                            + "AND (previous_display_name IS NULL "
                            + "OR previous_display_name <> new_display_name)) OR "
                            + "(action = 'CLEAR' AND previous_display_name IS NOT NULL "
                            + "AND new_display_name IS NULL)))");
            statement.executeUpdate(
                    "CREATE TRIGGER local_player_display_name_audit_no_update "
                            + "BEFORE UPDATE ON local_player_display_name_audit BEGIN "
                            + "SELECT RAISE(ABORT, 'local display name audit is append-only'); END");
            statement.executeUpdate(
                    "CREATE TRIGGER local_player_display_name_audit_no_delete "
                            + "BEFORE DELETE ON local_player_display_name_audit BEGIN "
                            + "SELECT RAISE(ABORT, 'local display name audit is append-only'); END");
        }
    }

    private void validateSchema(Connection connection) throws SQLException {
        if (!objectExists(connection, "table", DISPLAY_NAMES_TABLE)
                || !objectExists(connection, "table", AUDIT_TABLE)
                || !objectExists(connection, "trigger", AUDIT_UPDATE_TRIGGER)
                || !objectExists(connection, "trigger", AUDIT_DELETE_TRIGGER)) {
            throw new SQLException("SQLite display name schema is incomplete");
        }
        if (readSchemaVersion(connection) != SCHEMA_VERSION) {
            throw new SQLException("SQLite local identity schema version is unsupported");
        }
        try (PreparedStatement names =
                        connection.prepareStatement(
                                "SELECT player_id, display_name FROM "
                                        + DISPLAY_NAMES_TABLE
                                        + " WHERE 0");
                PreparedStatement audit =
                        connection.prepareStatement(
                                "SELECT sequence, occurred_at, administrator_id, action, player_id, "
                                        + "previous_display_name, new_display_name, reason FROM "
                                        + AUDIT_TABLE
                                        + " WHERE 0")) {
            names.executeQuery().close();
            audit.executeQuery().close();
        }
        if (countDisplayNames(connection) > maximumDisplayNames
                || countAuditEvents(connection) > maximumAuditEvents) {
            throw new SQLException("SQLite display name data exceeds configured capacity");
        }
    }

    private static boolean hasAnyDisplayNameSchemaObject(Connection connection)
            throws SQLException {
        return objectExists(connection, "table", DISPLAY_NAMES_TABLE)
                || objectExists(connection, "table", AUDIT_TABLE)
                || objectExists(connection, "trigger", AUDIT_UPDATE_TRIGGER)
                || objectExists(connection, "trigger", AUDIT_DELETE_TRIGGER);
    }

    private static int readSchemaVersion(Connection connection) throws SQLException {
        int rows = 0;
        int version = 0;
        try (PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT singleton, version FROM " + SCHEMA_TABLE);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows++;
                if (result.getInt(1) != 1) {
                    throw new SQLException("SQLite local identity schema metadata is invalid");
                }
                version = result.getInt(2);
            }
        }
        if (rows != 1) {
            throw new SQLException("SQLite local identity schema metadata is incomplete");
        }
        return version;
    }

    private static void updateSchemaVersion(Connection connection, int expected, int replacement)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "UPDATE "
                                + SCHEMA_TABLE
                                + " SET version = ? WHERE singleton = 1 AND version = ?")) {
            statement.setInt(1, replacement);
            statement.setInt(2, expected);
            requireOneRow(statement.executeUpdate(), "local identity schema migration");
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

    private static Optional<LocalDisplayName> find(Connection connection, PlayerId playerId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT display_name FROM "
                                + DISPLAY_NAMES_TABLE
                                + " WHERE player_id = ?")) {
            statement.setString(1, playerId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                LocalDisplayName value = new LocalDisplayName(result.getString(1));
                if (result.next()) {
                    throw new SQLException("player ID has multiple local display names");
                }
                return Optional.of(value);
            }
        }
    }

    private static void insertDisplayName(
            Connection connection, PlayerId playerId, LocalDisplayName displayName)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO "
                                + DISPLAY_NAMES_TABLE
                                + " (player_id, display_name) VALUES (?, ?)")) {
            statement.setString(1, playerId.value());
            statement.setString(2, displayName.value());
            requireOneRow(statement.executeUpdate(), "local display name insert");
        }
    }

    private static void updateDisplayName(
            Connection connection,
            PlayerId playerId,
            LocalDisplayName expected,
            LocalDisplayName replacement)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "UPDATE "
                                + DISPLAY_NAMES_TABLE
                                + " SET display_name = ? WHERE player_id = ? AND display_name = ?")) {
            statement.setString(1, replacement.value());
            statement.setString(2, playerId.value());
            statement.setString(3, expected.value());
            requireOneRow(statement.executeUpdate(), "local display name update");
        }
    }

    private static void deleteDisplayName(
            Connection connection, PlayerId playerId, LocalDisplayName expected)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "DELETE FROM "
                                + DISPLAY_NAMES_TABLE
                                + " WHERE player_id = ? AND display_name = ?")) {
            statement.setString(1, playerId.value());
            statement.setString(2, expected.value());
            requireOneRow(statement.executeUpdate(), "local display name delete");
        }
    }

    private static void insertAudit(
            Connection connection,
            Instant occurredAt,
            LocalIdentityAdministratorId administratorId,
            LocalDisplayNameAuditAction action,
            PlayerId playerId,
            Optional<LocalDisplayName> previousDisplayName,
            Optional<LocalDisplayName> newDisplayName,
            LocalHandleAdministrationReason reason)
            throws SQLException {
        long sequence = nextAuditSequence(connection);
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO "
                                + AUDIT_TABLE
                                + " (sequence, occurred_at, administrator_id, action, player_id, "
                                + "previous_display_name, new_display_name, reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, sequence);
            statement.setString(2, occurredAt.toString());
            statement.setString(3, administratorId.value());
            statement.setString(4, action.name());
            statement.setString(5, playerId.value());
            setOptionalDisplayName(statement, 6, previousDisplayName);
            setOptionalDisplayName(statement, 7, newDisplayName);
            statement.setString(8, reason.value());
            requireOneRow(statement.executeUpdate(), "local display name audit insert");
        }
    }

    private static void setOptionalDisplayName(
            PreparedStatement statement, int index, Optional<LocalDisplayName> displayName)
            throws SQLException {
        if (displayName.isPresent()) {
            statement.setString(index, displayName.orElseThrow().value());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static LocalDisplayNameAuditEvent readAuditEvent(ResultSet result, long sequence)
            throws SQLException {
        String previous = result.getString(6);
        String replacement = result.getString(7);
        return new LocalDisplayNameAuditEvent(
                sequence,
                Instant.parse(result.getString(2)),
                new LocalIdentityAdministratorId(result.getString(3)),
                LocalDisplayNameAuditAction.valueOf(result.getString(4)),
                new PlayerId(result.getString(5)),
                previous == null ? Optional.empty() : Optional.of(new LocalDisplayName(previous)),
                replacement == null
                        ? Optional.empty()
                        : Optional.of(new LocalDisplayName(replacement)),
                new LocalHandleAdministrationReason(result.getString(8)));
    }

    private static long countDisplayNames(Connection connection) throws SQLException {
        return count(connection, "SELECT COUNT(*) FROM " + DISPLAY_NAMES_TABLE);
    }

    private static long countAuditEvents(Connection connection) throws SQLException {
        return count(connection, "SELECT COUNT(*) FROM " + AUDIT_TABLE);
    }

    private static long count(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) {
                throw new SQLException("SQLite count query returned no row");
            }
            long value = result.getLong(1);
            if (value < 0L || result.next()) {
                throw new SQLException("SQLite count query returned an invalid result");
            }
            return value;
        }
    }

    private static long nextAuditSequence(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT COALESCE(MAX(sequence), 0) FROM " + AUDIT_TABLE)) {
            if (!result.next()) {
                throw new SQLException("SQLite display name audit sequence query returned no row");
            }
            long current = result.getLong(1);
            if (current < 0L || current == Long.MAX_VALUE || result.next()) {
                throw new SQLException("SQLite display name audit sequence cannot advance safely");
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
            throw new SqliteLocalHandleStoreException("SQLite display name read failed", exception);
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
                    "SQLite display name transaction failed", exception);
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

    private static void requireIntegrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equals(result.getString(1)) || result.next()) {
                throw new SQLException("SQLite display name integrity check failed");
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException("SQLite display name foreign key check failed");
            }
        }
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

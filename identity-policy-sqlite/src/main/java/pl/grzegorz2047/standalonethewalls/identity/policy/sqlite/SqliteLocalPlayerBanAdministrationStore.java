package pl.grzegorz2047.standalonethewalls.identity.policy.sqlite;

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
import pl.grzegorz2047.standalonethewalls.identity.policy.InMemoryLocalPlayerBanStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBan;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationStore;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAuditAction;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAuditEvent;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

/** Transactional SQLite persistence for local player bans and their audit trail. */
public final class SqliteLocalPlayerBanAdministrationStore
        implements LocalPlayerBanAdministrationStore {
    public static final int SCHEMA_VERSION = 2;
    private static final int LATEST_SUPPORTED_SCHEMA_VERSION = 3;

    private static final String SCHEMA_TABLE = "local_identity_schema";
    private static final String BANS_TABLE = "local_player_bans";
    private static final String AUDIT_TABLE = "local_player_ban_audit";
    private static final String AUDIT_UPDATE_TRIGGER = "local_player_ban_audit_no_update";
    private static final String AUDIT_DELETE_TRIGGER = "local_player_ban_audit_no_delete";
    private static final String DISPLAY_NAMES_TABLE = "local_player_display_names";
    private static final String DISPLAY_NAME_AUDIT_TABLE = "local_player_display_name_audit";
    private static final String DISPLAY_NAME_AUDIT_UPDATE_TRIGGER =
            "local_player_display_name_audit_no_update";
    private static final String DISPLAY_NAME_AUDIT_DELETE_TRIGGER =
            "local_player_display_name_audit_no_delete";
    private static final String COUNT_BANS = "SELECT COUNT(*) FROM local_player_bans";
    private static final String COUNT_AUDIT = "SELECT COUNT(*) FROM local_player_ban_audit";
    private static final String NEXT_AUDIT_SEQUENCE =
            "SELECT COALESCE(MAX(sequence), 0) FROM local_player_ban_audit";

    private final Path path;
    private final int maximumBans;
    private final int maximumAuditEvents;
    private final int busyTimeoutMillis;

    public SqliteLocalPlayerBanAdministrationStore(Path path) {
        this(
                path,
                InMemoryLocalPlayerBanStore.DEFAULT_MAXIMUM_BANS,
                InMemoryLocalPlayerBanStore.DEFAULT_MAXIMUM_AUDIT_EVENTS,
                SqliteLocalHandleAdministrationStore.DEFAULT_BUSY_TIMEOUT_MILLIS);
    }

    public SqliteLocalPlayerBanAdministrationStore(
            Path path, int maximumBans, int maximumAuditEvents, int busyTimeoutMillis) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (this.path.getFileName() == null || this.path.getParent() == null) {
            throw new IllegalArgumentException("path must identify a SQLite database file");
        }
        if (maximumBans < 1 || maximumBans > InMemoryLocalPlayerBanStore.ABSOLUTE_MAXIMUM_BANS) {
            throw new IllegalArgumentException("maximumBans is outside the safe range");
        }
        if (maximumAuditEvents < 1
                || maximumAuditEvents > InMemoryLocalPlayerBanStore.ABSOLUTE_MAXIMUM_AUDIT_EVENTS) {
            throw new IllegalArgumentException("maximumAuditEvents is outside the safe range");
        }
        if (busyTimeoutMillis < 1
                || busyTimeoutMillis
                        > SqliteLocalHandleAdministrationStore.MAXIMUM_BUSY_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("busyTimeoutMillis is outside the safe range");
        }
        this.maximumBans = maximumBans;
        this.maximumAuditEvents = maximumAuditEvents;
        this.busyTimeoutMillis = busyTimeoutMillis;
        new SqliteLocalHandleAdministrationStore(
                this.path,
                InMemoryLocalHandleBindingStore.DEFAULT_MAXIMUM_BINDINGS,
                InMemoryLocalHandleBindingStore.DEFAULT_MAXIMUM_AUDIT_EVENTS,
                busyTimeoutMillis);
        initializeSchema();
    }

    @Override
    public LocalPlayerBanAdministrationResult ban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        return writeTransaction(
                connection -> {
                    if (findBan(connection, identity).isPresent()) {
                        return LocalPlayerBanAdministrationResult.ALREADY_BANNED;
                    }
                    if (countBans(connection) >= maximumBans
                            || countAuditEvents(connection) >= maximumAuditEvents) {
                        return LocalPlayerBanAdministrationResult.CAPACITY_EXCEEDED;
                    }
                    insertBan(connection, identity, timestamp, administrator, auditReason);
                    insertAudit(
                            connection,
                            timestamp,
                            administrator,
                            LocalPlayerBanAuditAction.BAN,
                            identity,
                            auditReason);
                    return LocalPlayerBanAdministrationResult.BANNED;
                });
    }

    @Override
    public LocalPlayerBanAdministrationResult unban(
            PlayerId playerId,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason,
            Instant occurredAt) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        LocalIdentityAdministratorId administrator =
                Objects.requireNonNull(administratorId, "administratorId");
        LocalHandleAdministrationReason auditReason = Objects.requireNonNull(reason, "reason");
        Instant timestamp = Objects.requireNonNull(occurredAt, "occurredAt");
        return writeTransaction(
                connection -> {
                    if (findBan(connection, identity).isEmpty()) {
                        return LocalPlayerBanAdministrationResult.NOT_BANNED;
                    }
                    if (countAuditEvents(connection) >= maximumAuditEvents) {
                        return LocalPlayerBanAdministrationResult.CAPACITY_EXCEEDED;
                    }
                    deleteBan(connection, identity);
                    insertAudit(
                            connection,
                            timestamp,
                            administrator,
                            LocalPlayerBanAuditAction.UNBAN,
                            identity,
                            auditReason);
                    return LocalPlayerBanAdministrationResult.UNBANNED;
                });
    }

    @Override
    public Optional<LocalPlayerBan> findBan(PlayerId playerId) {
        PlayerId identity = Objects.requireNonNull(playerId, "playerId");
        return read(connection -> findBan(connection, identity));
    }

    @Override
    public List<LocalPlayerBan> bans() {
        return read(
                connection -> {
                    List<LocalPlayerBan> values = new ArrayList<>();
                    try (PreparedStatement statement =
                                    connection.prepareStatement(
                                            "SELECT player_id, banned_at, administrator_id, reason "
                                                    + "FROM local_player_bans ORDER BY player_id");
                            ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            if (values.size() >= maximumBans) {
                                throw new SQLException(
                                        "player ban result exceeds configured capacity");
                            }
                            values.add(readBan(result));
                        }
                    }
                    return List.copyOf(values);
                });
    }

    @Override
    public List<LocalPlayerBanAuditEvent> banAuditEvents() {
        return read(
                connection -> {
                    List<LocalPlayerBanAuditEvent> values = new ArrayList<>();
                    long previousSequence = 0L;
                    try (PreparedStatement statement =
                                    connection.prepareStatement(
                                            "SELECT sequence, occurred_at, administrator_id, action, "
                                                    + "player_id, reason FROM local_player_ban_audit "
                                                    + "ORDER BY sequence");
                            ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            if (values.size() >= maximumAuditEvents) {
                                throw new SQLException(
                                        "player ban audit result exceeds configured capacity");
                            }
                            long sequence = result.getLong(1);
                            if (sequence <= previousSequence) {
                                throw new SQLException(
                                        "player ban audit sequence is not strictly increasing");
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
                    if (version > LATEST_SUPPORTED_SCHEMA_VERSION) {
                        throw new SQLException(
                                "SQLite local identity schema is newer than this server");
                    }
                    if (version == 1) {
                        if (hasAnyBanSchemaObject(connection)) {
                            throw new SQLException(
                                    "SQLite player ban objects exist before schema migration");
                        }
                        createSchema(connection);
                        updateSchemaVersion(connection, 1, SCHEMA_VERSION);
                        version = SCHEMA_VERSION;
                    }
                    if (version < SCHEMA_VERSION) {
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
                    "CREATE TABLE local_player_bans ("
                            + "player_id TEXT PRIMARY KEY NOT NULL, "
                            + "banned_at TEXT NOT NULL, administrator_id TEXT NOT NULL, "
                            + "reason TEXT NOT NULL) WITHOUT ROWID");
            statement.executeUpdate(
                    "CREATE TABLE local_player_ban_audit ("
                            + "sequence INTEGER PRIMARY KEY, occurred_at TEXT NOT NULL, "
                            + "administrator_id TEXT NOT NULL, action TEXT NOT NULL, "
                            + "player_id TEXT NOT NULL, reason TEXT NOT NULL, "
                            + "CHECK (action IN ('BAN', 'UNBAN')))");
            statement.executeUpdate(
                    "CREATE TRIGGER local_player_ban_audit_no_update "
                            + "BEFORE UPDATE ON local_player_ban_audit BEGIN "
                            + "SELECT RAISE(ABORT, 'local player ban audit is append-only'); END");
            statement.executeUpdate(
                    "CREATE TRIGGER local_player_ban_audit_no_delete "
                            + "BEFORE DELETE ON local_player_ban_audit BEGIN "
                            + "SELECT RAISE(ABORT, 'local player ban audit is append-only'); END");
        }
    }

    private void validateSchema(Connection connection) throws SQLException {
        if (!objectExists(connection, "table", BANS_TABLE)
                || !objectExists(connection, "table", AUDIT_TABLE)
                || !objectExists(connection, "trigger", AUDIT_UPDATE_TRIGGER)
                || !objectExists(connection, "trigger", AUDIT_DELETE_TRIGGER)) {
            throw new SQLException("SQLite player ban schema is incomplete");
        }
        int version = readSchemaVersion(connection);
        if (version < SCHEMA_VERSION || version > LATEST_SUPPORTED_SCHEMA_VERSION) {
            throw new SQLException("SQLite local identity schema version is unsupported");
        }
        if (version == LATEST_SUPPORTED_SCHEMA_VERSION
                && (!objectExists(connection, "table", DISPLAY_NAMES_TABLE)
                        || !objectExists(connection, "table", DISPLAY_NAME_AUDIT_TABLE)
                        || !objectExists(
                                connection, "trigger", DISPLAY_NAME_AUDIT_UPDATE_TRIGGER)
                        || !objectExists(
                                connection, "trigger", DISPLAY_NAME_AUDIT_DELETE_TRIGGER))) {
            throw new SQLException("SQLite schema v3 display name objects are incomplete");
        }
        try (PreparedStatement bans =
                        connection.prepareStatement(
                                "SELECT player_id, banned_at, administrator_id, reason "
                                        + "FROM local_player_bans WHERE 0");
                PreparedStatement audit =
                        connection.prepareStatement(
                                "SELECT sequence, occurred_at, administrator_id, action, "
                                        + "player_id, reason FROM local_player_ban_audit WHERE 0")) {
            bans.executeQuery().close();
            audit.executeQuery().close();
        }
        if (countBans(connection) > maximumBans
                || countAuditEvents(connection) > maximumAuditEvents) {
            throw new SQLException("SQLite player ban data exceeds configured capacity");
        }
    }

    private static boolean hasAnyBanSchemaObject(Connection connection) throws SQLException {
        return objectExists(connection, "table", BANS_TABLE)
                || objectExists(connection, "table", AUDIT_TABLE)
                || objectExists(connection, "trigger", AUDIT_UPDATE_TRIGGER)
                || objectExists(connection, "trigger", AUDIT_DELETE_TRIGGER);
    }

    private static int readSchemaVersion(Connection connection) throws SQLException {
        int rows = 0;
        int version = 0;
        try (PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT singleton, version FROM local_identity_schema");
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
                        "UPDATE local_identity_schema SET version = ? "
                                + "WHERE singleton = 1 AND version = ?")) {
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

    private static Optional<LocalPlayerBan> findBan(Connection connection, PlayerId playerId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT player_id, banned_at, administrator_id, reason "
                                + "FROM local_player_bans WHERE player_id = ?")) {
            statement.setString(1, playerId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                LocalPlayerBan ban = readBan(result);
                if (result.next()) {
                    throw new SQLException("player ID has multiple local bans");
                }
                return Optional.of(ban);
            }
        }
    }

    private static LocalPlayerBan readBan(ResultSet result) throws SQLException {
        return new LocalPlayerBan(
                new PlayerId(result.getString(1)),
                Instant.parse(result.getString(2)),
                new LocalIdentityAdministratorId(result.getString(3)),
                new LocalHandleAdministrationReason(result.getString(4)));
    }

    private static void insertBan(
            Connection connection,
            PlayerId playerId,
            Instant bannedAt,
            LocalIdentityAdministratorId administratorId,
            LocalHandleAdministrationReason reason)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO local_player_bans "
                                + "(player_id, banned_at, administrator_id, reason) "
                                + "VALUES (?, ?, ?, ?)")) {
            statement.setString(1, playerId.value());
            statement.setString(2, bannedAt.toString());
            statement.setString(3, administratorId.value());
            statement.setString(4, reason.value());
            requireOneRow(statement.executeUpdate(), "local player ban insert");
        }
    }

    private static void deleteBan(Connection connection, PlayerId playerId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM local_player_bans WHERE player_id = ?")) {
            statement.setString(1, playerId.value());
            requireOneRow(statement.executeUpdate(), "local player ban delete");
        }
    }

    private static void insertAudit(
            Connection connection,
            Instant occurredAt,
            LocalIdentityAdministratorId administratorId,
            LocalPlayerBanAuditAction action,
            PlayerId playerId,
            LocalHandleAdministrationReason reason)
            throws SQLException {
        long sequence = nextAuditSequence(connection);
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO local_player_ban_audit "
                                + "(sequence, occurred_at, administrator_id, action, player_id, reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, sequence);
            statement.setString(2, occurredAt.toString());
            statement.setString(3, administratorId.value());
            statement.setString(4, action.name());
            statement.setString(5, playerId.value());
            statement.setString(6, reason.value());
            requireOneRow(statement.executeUpdate(), "local player ban audit insert");
        }
    }

    private static LocalPlayerBanAuditEvent readAuditEvent(ResultSet result, long sequence)
            throws SQLException {
        return new LocalPlayerBanAuditEvent(
                sequence,
                Instant.parse(result.getString(2)),
                new LocalIdentityAdministratorId(result.getString(3)),
                LocalPlayerBanAuditAction.valueOf(result.getString(4)),
                new PlayerId(result.getString(5)),
                new LocalHandleAdministrationReason(result.getString(6)));
    }

    private static long countBans(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(COUNT_BANS)) {
            return readCount(result);
        }
    }

    private static long countAuditEvents(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(COUNT_AUDIT)) {
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
                ResultSet result = statement.executeQuery(NEXT_AUDIT_SEQUENCE)) {
            if (!result.next()) {
                throw new SQLException("SQLite player ban audit sequence query returned no row");
            }
            long current = result.getLong(1);
            if (current < 0L || current == Long.MAX_VALUE || result.next()) {
                throw new SQLException("SQLite player ban audit sequence cannot advance safely");
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
            throw new SqliteLocalHandleStoreException("SQLite player ban read failed", exception);
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
                    "SQLite player ban transaction failed", exception);
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
                throw new SQLException("SQLite player ban integrity check failed");
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException("SQLite player ban foreign key check failed");
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

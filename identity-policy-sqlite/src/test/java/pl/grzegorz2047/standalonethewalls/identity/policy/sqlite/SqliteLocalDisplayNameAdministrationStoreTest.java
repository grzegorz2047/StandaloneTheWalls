package pl.grzegorz2047.standalonethewalls.identity.policy.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayName;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameAssignment;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalDisplayNameExpectation;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class SqliteLocalDisplayNameAdministrationStoreTest {
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final CanonicalHandle HANDLE = new CanonicalHandle("local_player");
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Local presentation preference");
    private static final Instant NOW = Instant.parse("2026-08-02T16:30:00Z");

    @TempDir Path temporaryDirectory;

    @Test
    void migratesVersionTwoPreservesExistingStreamsAndRestarts() throws SQLException {
        Path database = temporaryDirectory.resolve("migration.sqlite");
        SqliteLocalHandleAdministrationStore handles =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        assertThat(handles.reserve(HANDLE, FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);
        SqliteLocalPlayerBanAdministrationStore bans =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000);
        assertThat(bans.ban(SECOND, ADMINISTRATOR, REASON, NOW.plusSeconds(1)))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);
        assertThat(schemaVersion(database)).isEqualTo(2);

        SqliteLocalDisplayNameAdministrationStore names = store(database, 10, 10);
        assertThat(schemaVersion(database)).isEqualTo(3);
        assertThat(set(names, FIRST, LocalDisplayNameExpectation.absent(), "Shared", 2))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(set(names, SECOND, LocalDisplayNameExpectation.absent(), "Shared", 3))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);

        SqliteLocalDisplayNameAdministrationStore reopened = store(database, 10, 10);
        assertThat(reopened.displayNames())
                .containsExactly(
                        new LocalDisplayNameAssignment(FIRST, new LocalDisplayName("Shared")),
                        new LocalDisplayNameAssignment(SECOND, new LocalDisplayName("Shared")));
        assertThat(reopened.auditEvents())
                .extracting(event -> event.sequence())
                .containsExactly(1L, 2L);
        assertThat(new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000).bindings())
                .singleElement()
                .satisfies(binding -> assertThat(binding.playerId()).isEqualTo(FIRST));
        assertThat(new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000).auditEvents())
                .extracting(event -> event.sequence())
                .containsExactly(1L);
        assertThat(new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000).bans())
                .singleElement()
                .satisfies(ban -> assertThat(ban.playerId()).isEqualTo(SECOND));
        assertThat(
                        new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000)
                                .banAuditEvents())
                .extracting(event -> event.sequence())
                .containsExactly(1L);
    }

    @Test
    void casNoOpsFailuresAndAuditFailureNeverCreatePartialState() throws SQLException {
        Path database = temporaryDirectory.resolve("atomic.sqlite");
        SqliteLocalDisplayNameAdministrationStore store = store(database, 10, 10);
        assertThat(set(store, FIRST, LocalDisplayNameExpectation.absent(), "Initial", 0))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(
                        set(
                                store,
                                FIRST,
                                LocalDisplayNameExpectation.exact(name("Initial")),
                                "Initial",
                                1))
                .isEqualTo(LocalDisplayNameAdministrationResult.UNCHANGED);
        assertThat(set(store, FIRST, LocalDisplayNameExpectation.absent(), "Other", 2))
                .isEqualTo(LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH);
        assertThat(
                        store.clearDisplayName(
                                SECOND,
                                LocalDisplayNameExpectation.present(),
                                ADMINISTRATOR,
                                REASON,
                                NOW.plusSeconds(3)))
                .isEqualTo(LocalDisplayNameAdministrationResult.NOT_FOUND);
        assertThat(store.auditEvents()).hasSize(1);

        execute(
                database,
                "CREATE TRIGGER fail_display_audit BEFORE INSERT ON "
                        + "local_player_display_name_audit BEGIN SELECT RAISE(ABORT, 'forced'); END");
        assertThatThrownBy(
                        () ->
                                set(
                                        store,
                                        FIRST,
                                        LocalDisplayNameExpectation.exact(name("Initial")),
                                        "Changed",
                                        4))
                .isInstanceOf(SqliteLocalHandleStoreException.class);
        assertThat(store.find(FIRST)).contains(name("Initial"));
        assertThat(store.auditEvents()).hasSize(1);
    }

    @Test
    void stateAndAuditCapacityBlockMutations() {
        Path database = temporaryDirectory.resolve("capacity.sqlite");
        SqliteLocalDisplayNameAdministrationStore store = store(database, 1, 1);
        assertThat(set(store, FIRST, LocalDisplayNameExpectation.absent(), "First", 0))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(set(store, SECOND, LocalDisplayNameExpectation.absent(), "Second", 1))
                .isEqualTo(LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(
                        store.clearDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.present(),
                                ADMINISTRATOR,
                                REASON,
                                NOW.plusSeconds(2)))
                .isEqualTo(LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(store.find(FIRST)).contains(name("First"));
        assertThat(store.auditEvents()).hasSize(1);
    }

    @Test
    void twoStoreInstancesAllowOnlyOneExactConcurrentUpdate() {
        Path database = temporaryDirectory.resolve("concurrent.sqlite");
        SqliteLocalDisplayNameAdministrationStore firstStore = store(database, 10, 10);
        SqliteLocalDisplayNameAdministrationStore secondStore = store(database, 10, 10);
        assertThat(set(firstStore, FIRST, LocalDisplayNameExpectation.absent(), "Initial", 0))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalDisplayNameAdministrationResult> first =
                    executor.submit(() -> raceSet(firstStore, "One", ready, start));
            Future<LocalDisplayNameAdministrationResult> second =
                    executor.submit(() -> raceSet(secondStore, "Two", ready, start));
            assertThat(await(ready, 5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(await(first), await(second)))
                    .containsExactlyInAnyOrder(
                            LocalDisplayNameAdministrationResult.APPLIED,
                            LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH);
        }
        assertThat(firstStore.auditEvents()).hasSize(2);
    }

    @Test
    void migrationRollsBackAndNewerSchemaOrAuditMutationIsRejected() throws SQLException {
        Path rollback = temporaryDirectory.resolve("rollback.sqlite");
        new SqliteLocalPlayerBanAdministrationStore(rollback, 10, 10, 5_000);
        execute(
                rollback,
                "CREATE TRIGGER fail_v3 BEFORE UPDATE OF version ON local_identity_schema "
                        + "WHEN NEW.version = 3 BEGIN SELECT RAISE(ABORT, 'forced'); END");
        assertThatThrownBy(() -> store(rollback, 10, 10))
                .isInstanceOf(SqliteLocalHandleStoreException.class);
        assertThat(schemaVersion(rollback)).isEqualTo(2);
        assertThat(objectExists(rollback, "table", "local_player_display_names")).isFalse();
        assertThat(objectExists(rollback, "table", "local_player_display_name_audit")).isFalse();

        Path future = temporaryDirectory.resolve("future.sqlite");
        SqliteLocalDisplayNameAdministrationStore store = store(future, 10, 10);
        assertThat(set(store, FIRST, LocalDisplayNameExpectation.absent(), "Name", 0))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThatThrownBy(
                        () ->
                                execute(
                                        future,
                                        "UPDATE local_player_display_name_audit SET reason = 'x'"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(future, "DELETE FROM local_player_display_name_audit"))
                .isInstanceOf(SQLException.class);
        execute(future, "UPDATE local_identity_schema SET version = 4 WHERE singleton = 1");
        assertThatThrownBy(() -> store(future, 10, 10))
                .isInstanceOf(SqliteLocalHandleStoreException.class);
    }

    private static LocalDisplayNameAdministrationResult await(
            Future<LocalDisplayNameAdministrationResult> result) {
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("concurrent display-name update was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("concurrent display-name update failed", exception);
        }
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "concurrent display-name coordination was interrupted", exception);
        }
    }

    private static LocalDisplayNameAdministrationResult raceSet(
            SqliteLocalDisplayNameAdministrationStore store,
            String replacement,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        if (!await(start, 5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent display-name test did not start");
        }
        return set(
                store, FIRST, LocalDisplayNameExpectation.exact(name("Initial")), replacement, 1);
    }

    private static LocalDisplayNameAdministrationResult set(
            SqliteLocalDisplayNameAdministrationStore store,
            PlayerId playerId,
            LocalDisplayNameExpectation expectation,
            String value,
            long secondOffset) {
        return store.setDisplayName(
                playerId,
                expectation,
                name(value),
                ADMINISTRATOR,
                REASON,
                NOW.plusSeconds(secondOffset));
    }

    private static LocalDisplayName name(String value) {
        return new LocalDisplayName(value);
    }

    private static SqliteLocalDisplayNameAdministrationStore store(
            Path database, int maximumNames, int maximumAuditEvents) {
        return new SqliteLocalDisplayNameAdministrationStore(
                database, maximumNames, maximumAuditEvents, 10_000);
    }

    private static Connection connect(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().normalize());
    }

    private static void execute(Path database, String sql) throws SQLException {
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int schemaVersion(Path database) throws SQLException {
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT version FROM local_identity_schema WHERE singleton = 1")) {
            assertThat(result.next()).isTrue();
            int version = result.getInt(1);
            assertThat(result.next()).isFalse();
            return version;
        }
    }

    private static boolean objectExists(Path database, String type, String name)
            throws SQLException {
        try (Connection connection = connect(database);
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT 1 FROM sqlite_master WHERE type = ? AND name = ?")) {
            statement.setString(1, type);
            statement.setString(2, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}

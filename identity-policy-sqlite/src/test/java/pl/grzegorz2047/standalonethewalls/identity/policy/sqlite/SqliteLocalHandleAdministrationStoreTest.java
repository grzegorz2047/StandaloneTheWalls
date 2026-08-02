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
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAuditAction;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBinding;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBindingResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class SqliteLocalHandleAdministrationStoreTest {
    private static final CanonicalHandle ALPHA = new CanonicalHandle("alpha_player");
    private static final CanonicalHandle BETA = new CanonicalHandle("beta_player");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final PlayerId THIRD = new PlayerId("sf1_" + "c".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Manual identity correction");
    private static final Instant NOW = Instant.parse("2026-08-02T08:30:00Z");

    @TempDir Path temporaryDirectory;

    @Test
    void migratesEmptyDatabaseAndPersistsBindingsAndAuditAcrossReopen() throws SQLException {
        Path database = temporaryDirectory.resolve("identity.sqlite");
        SqliteLocalHandleAdministrationStore first =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);

        assertThat(first.bindOrVerify(BETA, FIRST)).isEqualTo(LocalHandleBindingResult.BOUND);
        assertThat(first.reserve(ALPHA, SECOND, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);
        assertThat(first.rebind(ALPHA, SECOND, THIRD, ADMINISTRATOR, REASON, NOW.plusSeconds(1)))
                .isEqualTo(LocalHandleAdministrationResult.REBOUND);

        SqliteLocalHandleAdministrationStore reopened =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        assertThat(reopened.find(ALPHA)).contains(THIRD);
        assertThat(reopened.find(BETA)).contains(FIRST);
        assertThat(reopened.bindings())
                .containsExactly(
                        new LocalHandleBinding(ALPHA, THIRD), new LocalHandleBinding(BETA, FIRST));
        assertThat(reopened.auditEvents())
                .extracting(event -> event.action())
                .containsExactly(LocalHandleAuditAction.RESERVE, LocalHandleAuditAction.REBIND);
        assertThat(reopened.auditEvents())
                .extracting(event -> event.sequence())
                .containsExactly(1L, 2L);

        try (Connection connection = connect(database)) {
            assertThat(schemaVersion(connection)).isEqualTo(1);
            assertThat(objectExists(connection, "trigger", "local_handle_audit_no_update"))
                    .isTrue();
            assertThat(objectExists(connection, "trigger", "local_handle_audit_no_delete"))
                    .isTrue();
        }
    }

    @Test
    void twoStoreInstancesHaveExactlyOneConcurrentFirstUseWinner()
            throws InterruptedException, ExecutionException, TimeoutException {
        Path database = temporaryDirectory.resolve("first-use.sqlite");
        SqliteLocalHandleAdministrationStore firstStore =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 10_000);
        SqliteLocalHandleAdministrationStore secondStore =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 10_000);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalHandleBindingResult> first =
                    executor.submit(() -> raceBind(firstStore, FIRST, ready, start));
            Future<LocalHandleBindingResult> second =
                    executor.submit(() -> raceBind(secondStore, SECOND, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            LocalHandleBindingResult.BOUND, LocalHandleBindingResult.CONFLICT);
        }

        assertThat(firstStore.find(ALPHA))
                .hasValueSatisfying(playerId -> assertThat(playerId).isIn(FIRST, SECOND));
        assertThat(firstStore.bindings()).hasSize(1);
    }

    @Test
    void twoStoreInstancesAllowOnlyOneExpectedPlayerRebind()
            throws InterruptedException, ExecutionException, TimeoutException {
        Path database = temporaryDirectory.resolve("rebind.sqlite");
        SqliteLocalHandleAdministrationStore firstStore =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 10_000);
        SqliteLocalHandleAdministrationStore secondStore =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 10_000);
        assertThat(firstStore.bindOrVerify(ALPHA, FIRST)).isEqualTo(LocalHandleBindingResult.BOUND);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalHandleAdministrationResult> second =
                    executor.submit(() -> raceRebind(firstStore, SECOND, ready, start));
            Future<LocalHandleAdministrationResult> third =
                    executor.submit(() -> raceRebind(secondStore, THIRD, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(second.get(10, TimeUnit.SECONDS), third.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            LocalHandleAdministrationResult.REBOUND,
                            LocalHandleAdministrationResult.EXPECTATION_MISMATCH);
        }

        assertThat(firstStore.find(ALPHA))
                .hasValueSatisfying(playerId -> assertThat(playerId).isIn(SECOND, THIRD));
        assertThat(firstStore.auditEvents()).hasSize(1);
        assertThat(firstStore.auditEvents().getFirst().action())
                .isEqualTo(LocalHandleAuditAction.REBIND);
    }

    @Test
    void auditInsertFailureRollsBackTheBindingMutation() throws SQLException {
        Path database = temporaryDirectory.resolve("rollback.sqlite");
        SqliteLocalHandleAdministrationStore store =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        assertThat(store.bindOrVerify(ALPHA, FIRST)).isEqualTo(LocalHandleBindingResult.BOUND);
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TRIGGER fail_rebind_audit BEFORE INSERT ON local_handle_audit "
                            + "WHEN NEW.action = 'REBIND' BEGIN "
                            + "SELECT RAISE(ABORT, 'forced audit failure'); END");
        }

        assertThatThrownBy(() -> store.rebind(ALPHA, FIRST, SECOND, ADMINISTRATOR, REASON, NOW))
                .isInstanceOf(SqliteLocalHandleStoreException.class);
        assertThat(store.find(ALPHA)).contains(FIRST);
        assertThat(store.auditEvents()).isEmpty();
    }

    @Test
    void capacityFailureDoesNotPartiallyChangePersistentState() {
        Path database = temporaryDirectory.resolve("capacity.sqlite");
        SqliteLocalHandleAdministrationStore store =
                new SqliteLocalHandleAdministrationStore(database, 2, 1, 5_000);
        assertThat(store.reserve(ALPHA, FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);
        assertThat(store.bindOrVerify(BETA, SECOND)).isEqualTo(LocalHandleBindingResult.BOUND);

        assertThat(store.rebind(ALPHA, FIRST, THIRD, ADMINISTRATOR, REASON, NOW.plusSeconds(1)))
                .isEqualTo(LocalHandleAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(store.unbind(BETA, SECOND, ADMINISTRATOR, REASON, NOW.plusSeconds(2)))
                .isEqualTo(LocalHandleAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(store.find(ALPHA)).contains(FIRST);
        assertThat(store.find(BETA)).contains(SECOND);
        assertThat(store.auditEvents()).hasSize(1);
    }

    @Test
    void newerSchemaVersionIsRejectedWithoutModification() throws SQLException {
        Path database = temporaryDirectory.resolve("future.sqlite");
        new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        try (Connection connection = connect(database);
                PreparedStatement statement =
                        connection.prepareStatement(
                                "UPDATE local_identity_schema SET version = ? WHERE singleton = 1")) {
            statement.setInt(1, SqliteLocalHandleAdministrationStore.SCHEMA_VERSION + 1);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        assertThatThrownBy(() -> new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000))
                .isInstanceOf(SqliteLocalHandleStoreException.class)
                .hasMessage("SQLite local identity transaction failed");
        try (Connection connection = connect(database)) {
            assertThat(schemaVersion(connection))
                    .isEqualTo(SqliteLocalHandleAdministrationStore.SCHEMA_VERSION + 1);
        }
    }

    @Test
    void auditTableRejectsUpdateAndDelete() throws SQLException {
        Path database = temporaryDirectory.resolve("append-only.sqlite");
        SqliteLocalHandleAdministrationStore store =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        assertThat(store.reserve(ALPHA, FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);

        try (Connection connection = connect(database);
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.executeUpdate(
                                            "UPDATE local_handle_audit SET reason = 'changed'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM local_handle_audit"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(store.auditEvents()).hasSize(1);
    }

    private static LocalHandleBindingResult raceBind(
            SqliteLocalHandleAdministrationStore store,
            PlayerId playerId,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent SQLite binding test did not start");
        }
        return store.bindOrVerify(ALPHA, playerId);
    }

    private static LocalHandleAdministrationResult raceRebind(
            SqliteLocalHandleAdministrationStore store,
            PlayerId replacement,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent SQLite rebind test did not start");
        }
        return store.rebind(ALPHA, FIRST, replacement, ADMINISTRATOR, REASON, NOW);
    }

    private static Connection connect(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().normalize());
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT version FROM local_identity_schema WHERE singleton = 1")) {
            assertThat(result.next()).isTrue();
            int version = result.getInt(1);
            assertThat(result.next()).isFalse();
            return version;
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
}

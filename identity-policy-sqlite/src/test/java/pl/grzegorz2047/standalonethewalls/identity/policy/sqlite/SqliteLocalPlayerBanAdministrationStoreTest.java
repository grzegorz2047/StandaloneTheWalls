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
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBan;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAuditAction;
import pl.grzegorz2047.standalonethewalls.identity.policy.PlayerBanAdmissionDecision;
import pl.grzegorz2047.standalonethewalls.identity.policy.PlayerBanAdmissionService;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class SqliteLocalPlayerBanAdministrationStoreTest {
    private static final CanonicalHandle HANDLE = new CanonicalHandle("local_player");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Confirmed local abuse");
    private static final Instant NOW = Instant.parse("2026-08-02T09:30:00Z");

    @TempDir Path temporaryDirectory;

    @Test
    void migratesVersionOneWithoutLosingBindingsOrHandleAudit() throws SQLException {
        Path database = temporaryDirectory.resolve("migration.sqlite");
        SqliteLocalHandleAdministrationStore handles =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        assertThat(handles.reserve(HANDLE, FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);
        assertThat(schemaVersion(database)).isEqualTo(1);

        SqliteLocalPlayerBanAdministrationStore bans =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000);

        assertThat(schemaVersion(database)).isEqualTo(2);
        SqliteLocalHandleAdministrationStore reopenedHandles =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        assertThat(reopenedHandles.bindings())
                .containsExactly(new LocalHandleBinding(HANDLE, FIRST));
        assertThat(reopenedHandles.auditEvents()).hasSize(1);
        assertThat(reopenedHandles.auditEvents().getFirst().action())
                .isEqualTo(LocalHandleAuditAction.RESERVE);
        assertThat(bans.bans()).isEmpty();
        assertThat(bans.banAuditEvents()).isEmpty();
    }

    @Test
    void banAndUnbanPersistAcrossReopenWithoutChangingHandleBinding() {
        Path database = temporaryDirectory.resolve("persistence.sqlite");
        SqliteLocalHandleAdministrationStore handles =
                new SqliteLocalHandleAdministrationStore(database, 10, 10, 5_000);
        assertThat(handles.bindOrVerify(HANDLE, FIRST)).isEqualTo(LocalHandleBindingResult.BOUND);
        SqliteLocalPlayerBanAdministrationStore bans =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000);

        assertThat(bans.ban(FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);
        assertThat(bans.ban(FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalPlayerBanAdministrationResult.ALREADY_BANNED);
        assertThat(new PlayerBanAdmissionService(bans).evaluate(FIRST))
                .isEqualTo(PlayerBanAdmissionDecision.PLAYER_BANNED);

        SqliteLocalPlayerBanAdministrationStore reopenedBans =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000);
        assertThat(reopenedBans.findBan(FIRST))
                .contains(new LocalPlayerBan(FIRST, NOW, ADMINISTRATOR, REASON));
        assertThat(handles.find(HANDLE)).contains(FIRST);
        assertThat(reopenedBans.unban(FIRST, ADMINISTRATOR, REASON, NOW.plusSeconds(1)))
                .isEqualTo(LocalPlayerBanAdministrationResult.UNBANNED);
        assertThat(reopenedBans.unban(FIRST, ADMINISTRATOR, REASON, NOW.plusSeconds(2)))
                .isEqualTo(LocalPlayerBanAdministrationResult.NOT_BANNED);
        assertThat(handles.find(HANDLE)).contains(FIRST);
        assertThat(reopenedBans.banAuditEvents())
                .extracting(event -> event.action())
                .containsExactly(LocalPlayerBanAuditAction.BAN, LocalPlayerBanAuditAction.UNBAN);
    }

    @Test
    void twoStoreInstancesCreateExactlyOneConcurrentBan()
            throws InterruptedException, ExecutionException, TimeoutException {
        Path database = temporaryDirectory.resolve("concurrent.sqlite");
        SqliteLocalPlayerBanAdministrationStore firstStore =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 10_000);
        SqliteLocalPlayerBanAdministrationStore secondStore =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 10_000);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalPlayerBanAdministrationResult> first =
                    executor.submit(() -> raceBan(firstStore, ready, start));
            Future<LocalPlayerBanAdministrationResult> second =
                    executor.submit(() -> raceBan(secondStore, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            LocalPlayerBanAdministrationResult.BANNED,
                            LocalPlayerBanAdministrationResult.ALREADY_BANNED);
        }

        assertThat(firstStore.bans()).hasSize(1);
        assertThat(firstStore.banAuditEvents()).hasSize(1);
    }

    @Test
    void failedAuditInsertRollsBackTheBan() throws SQLException {
        Path database = temporaryDirectory.resolve("rollback.sqlite");
        SqliteLocalPlayerBanAdministrationStore store =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000);
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TRIGGER fail_player_ban_audit BEFORE INSERT "
                            + "ON local_player_ban_audit BEGIN "
                            + "SELECT RAISE(ABORT, 'forced ban audit failure'); END");
        }

        assertThatThrownBy(() -> store.ban(FIRST, ADMINISTRATOR, REASON, NOW))
                .isInstanceOf(SqliteLocalHandleStoreException.class);
        assertThat(store.findBan(FIRST)).isEmpty();
        assertThat(store.banAuditEvents()).isEmpty();
    }

    @Test
    void auditCapacityPreventsPersistentUnban() {
        Path database = temporaryDirectory.resolve("capacity.sqlite");
        SqliteLocalPlayerBanAdministrationStore store =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 1, 5_000);
        assertThat(store.ban(FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);

        assertThat(store.unban(FIRST, ADMINISTRATOR, REASON, NOW.plusSeconds(1)))
                .isEqualTo(LocalPlayerBanAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(store.findBan(FIRST)).isPresent();
        assertThat(store.banAuditEvents()).hasSize(1);
    }

    @Test
    void futureSchemaIsRejectedWithoutBeingRewritten() throws SQLException {
        Path database = temporaryDirectory.resolve("future.sqlite");
        new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000);
        try (Connection connection = connect(database);
                PreparedStatement statement =
                        connection.prepareStatement(
                                "UPDATE local_identity_schema SET version = 3 WHERE singleton = 1")) {
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        assertThatThrownBy(
                        () ->
                                new SqliteLocalPlayerBanAdministrationStore(
                                        database, 10, 10, 5_000))
                .isInstanceOf(SqliteLocalHandleStoreException.class);
        assertThat(schemaVersion(database)).isEqualTo(3);
    }

    @Test
    void banAuditIsAppendOnly() throws SQLException {
        Path database = temporaryDirectory.resolve("append-only.sqlite");
        SqliteLocalPlayerBanAdministrationStore store =
                new SqliteLocalPlayerBanAdministrationStore(database, 10, 10, 5_000);
        assertThat(store.ban(FIRST, ADMINISTRATOR, REASON, NOW))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);

        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.executeUpdate(
                                            "UPDATE local_player_ban_audit SET reason = 'changed'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(
                            () -> statement.executeUpdate("DELETE FROM local_player_ban_audit"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(store.banAuditEvents()).hasSize(1);
    }

    private static LocalPlayerBanAdministrationResult raceBan(
            SqliteLocalPlayerBanAdministrationStore store,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent SQLite ban test did not start");
        }
        return store.ban(FIRST, ADMINISTRATOR, REASON, NOW);
    }

    private static Connection connect(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath().normalize());
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
}

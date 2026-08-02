package pl.grzegorz2047.standalonethewalls.identity.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class LocalPlayerBanAdministrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Confirmed local abuse");

    @Test
    void banAndUnbanAreAuditedWithoutIdempotentDuplicates() {
        InMemoryLocalPlayerBanStore store = new InMemoryLocalPlayerBanStore(10, 10);
        LocalPlayerBanAdministrationService service = service(store);
        PlayerBanAdmissionService admission = new PlayerBanAdmissionService(store);

        assertThat(admission.evaluate(FIRST)).isEqualTo(PlayerBanAdmissionDecision.ALLOWED);
        assertThat(service.ban(FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);
        assertThat(service.ban(FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.ALREADY_BANNED);
        assertThat(admission.evaluate(FIRST))
                .isEqualTo(PlayerBanAdmissionDecision.PLAYER_BANNED);
        assertThat(service.inspect(FIRST)).contains(new LocalPlayerBan(FIRST, NOW, ADMINISTRATOR, REASON));

        assertThat(service.unban(FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.UNBANNED);
        assertThat(service.unban(FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.NOT_BANNED);
        assertThat(admission.evaluate(FIRST)).isEqualTo(PlayerBanAdmissionDecision.ALLOWED);
        assertThat(service.auditEvents()).extracting(LocalPlayerBanAuditEvent::sequence)
                .containsExactly(1L, 2L);
        assertThat(service.auditEvents()).extracting(LocalPlayerBanAuditEvent::action)
                .containsExactly(LocalPlayerBanAuditAction.BAN, LocalPlayerBanAuditAction.UNBAN);
    }

    @Test
    void auditCapacityPreventsAnUnauditedUnban() {
        InMemoryLocalPlayerBanStore store = new InMemoryLocalPlayerBanStore(10, 1);
        LocalPlayerBanAdministrationService service = service(store);
        assertThat(service.ban(FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);

        assertThat(service.unban(FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(service.inspect(FIRST)).isPresent();
        assertThat(service.auditEvents()).hasSize(1);
    }

    @Test
    void exactlyOneConcurrentBanCreatesOneStateAndOneEvent()
            throws InterruptedException, ExecutionException, TimeoutException {
        InMemoryLocalPlayerBanStore store = new InMemoryLocalPlayerBanStore(10, 10);
        LocalPlayerBanAdministrationService service = service(store);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalPlayerBanAdministrationResult> first =
                    executor.submit(() -> raceBan(service, ready, start));
            Future<LocalPlayerBanAdministrationResult> second =
                    executor.submit(() -> raceBan(service, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            LocalPlayerBanAdministrationResult.BANNED,
                            LocalPlayerBanAdministrationResult.ALREADY_BANNED);
        }

        assertThat(service.bans()).hasSize(1);
        assertThat(service.auditEvents()).hasSize(1);
    }

    @Test
    void banCapacityRejectsASecondPlayerWithoutAudit() {
        InMemoryLocalPlayerBanStore store = new InMemoryLocalPlayerBanStore(1, 10);
        LocalPlayerBanAdministrationService service = service(store);
        assertThat(service.ban(FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.BANNED);

        assertThat(service.ban(SECOND, ADMINISTRATOR, REASON))
                .isEqualTo(LocalPlayerBanAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(service.inspect(SECOND)).isEmpty();
        assertThat(service.auditEvents()).hasSize(1);
    }

    private static LocalPlayerBanAdministrationResult raceBan(
            LocalPlayerBanAdministrationService service,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent ban test did not start in time");
        }
        return service.ban(FIRST, ADMINISTRATOR, REASON);
    }

    private static LocalPlayerBanAdministrationService service(InMemoryLocalPlayerBanStore store) {
        return new LocalPlayerBanAdministrationService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

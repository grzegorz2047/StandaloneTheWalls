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
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;

class LocalHandleAdministrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T08:00:00Z");
    private static final CanonicalHandle ALPHA = new CanonicalHandle("alpha_player");
    private static final CanonicalHandle BETA = new CanonicalHandle("beta_player");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final PlayerId THIRD = new PlayerId("sf1_" + "c".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Manual identity correction");

    @Test
    void successfulMutationsAreAuditedAndViewsAreDeterministic() {
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore(10, 10);
        LocalHandleAdministrationService service = service(store);

        assertThat(service.reserve(BETA, FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);
        assertThat(service.reserve(BETA, FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.ALREADY_MATCHED);
        assertThat(service.reserve(BETA, SECOND, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.CONFLICT);
        assertThat(service.reserve(ALPHA, SECOND, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);
        assertThat(service.unbind(BETA, SECOND, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.EXPECTATION_MISMATCH);
        assertThat(service.rebind(BETA, FIRST, THIRD, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.REBOUND);
        assertThat(service.unbind(ALPHA, SECOND, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.UNBOUND);

        assertThat(service.bindings())
                .containsExactly(new LocalHandleBinding(BETA, THIRD));
        assertThat(service.inspect(ALPHA)).isEmpty();
        assertThat(service.inspect(BETA)).contains(THIRD);

        List<LocalHandleAuditEvent> events = service.auditEvents();
        assertThat(events).extracting(LocalHandleAuditEvent::sequence)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(events).extracting(LocalHandleAuditEvent::action)
                .containsExactly(
                        LocalHandleAuditAction.RESERVE,
                        LocalHandleAuditAction.RESERVE,
                        LocalHandleAuditAction.REBIND,
                        LocalHandleAuditAction.UNBIND);
        assertThat(events).extracting(LocalHandleAuditEvent::occurredAt)
                .containsOnly(NOW);
        assertThat(events.get(0).previousPlayerId()).isEmpty();
        assertThat(events.get(0).newPlayerId()).contains(FIRST);
        assertThat(events.get(2).previousPlayerId()).contains(FIRST);
        assertThat(events.get(2).newPlayerId()).contains(THIRD);
        assertThat(events.get(3).previousPlayerId()).contains(SECOND);
        assertThat(events.get(3).newPlayerId()).isEmpty();
    }

    @Test
    void auditCapacityPreventsAnUnauditedAdministrativeMutation() {
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore(2, 1);
        LocalHandleAdministrationService service = service(store);
        assertThat(service.reserve(ALPHA, FIRST, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.RESERVED);
        assertThat(store.bindOrVerify(BETA, SECOND)).isEqualTo(LocalHandleBindingResult.BOUND);

        assertThat(service.rebind(ALPHA, FIRST, THIRD, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(service.unbind(BETA, SECOND, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(service.inspect(ALPHA)).contains(FIRST);
        assertThat(service.inspect(BETA)).contains(SECOND);
        assertThat(service.auditEvents()).hasSize(1);
    }

    @Test
    void bindingCapacityFailsClosedForTofuAndReserve() {
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore(1, 10);
        LocalHandleAdministrationService service = service(store);
        assertThat(store.bindOrVerify(ALPHA, FIRST)).isEqualTo(LocalHandleBindingResult.BOUND);

        assertThat(store.bindOrVerify(BETA, SECOND))
                .isEqualTo(LocalHandleBindingResult.CAPACITY_EXCEEDED);
        assertThat(service.reserve(BETA, SECOND, ADMINISTRATOR, REASON))
                .isEqualTo(LocalHandleAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(service.inspect(BETA)).isEmpty();
        assertThat(service.auditEvents()).isEmpty();
    }

    @Test
    void exactlyOneConcurrentRebindWinsTheExpectedPlayerCompareAndSet()
            throws InterruptedException, ExecutionException, TimeoutException {
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore(10, 10);
        assertThat(store.bindOrVerify(ALPHA, FIRST)).isEqualTo(LocalHandleBindingResult.BOUND);
        LocalHandleAdministrationService service = service(store);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalHandleAdministrationResult> second =
                    executor.submit(() -> raceRebind(service, SECOND, ready, start));
            Future<LocalHandleAdministrationResult> third =
                    executor.submit(() -> raceRebind(service, THIRD, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(
                            List.of(
                                    second.get(5, TimeUnit.SECONDS),
                                    third.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            LocalHandleAdministrationResult.REBOUND,
                            LocalHandleAdministrationResult.EXPECTATION_MISMATCH);
        }

        assertThat(service.inspect(ALPHA)).hasValueSatisfying(value -> assertThat(value).isIn(SECOND, THIRD));
        assertThat(service.auditEvents()).hasSize(1);
        assertThat(service.auditEvents().getFirst().action())
                .isEqualTo(LocalHandleAuditAction.REBIND);
    }

    private static LocalHandleAdministrationResult raceRebind(
            LocalHandleAdministrationService service,
            PlayerId replacement,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent administration test did not start in time");
        }
        return service.rebind(ALPHA, FIRST, replacement, ADMINISTRATOR, REASON);
    }

    private static LocalHandleAdministrationService service(
            InMemoryLocalHandleBindingStore store) {
        return new LocalHandleAdministrationService(
                store, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

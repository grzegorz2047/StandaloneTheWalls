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

class LocalDisplayNameAdministrationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T16:00:00Z");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("console");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Local presentation preference");

    @Test
    void setChangeAndClearUseExplicitExpectationsAndOneEventPerAppliedMutation() {
        InMemoryLocalDisplayNameStore store = new InMemoryLocalDisplayNameStore(10, 10);
        LocalDisplayNameAdministrationService service = service(store);

        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.absent(),
                                "  Grzegorz  ",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(service.inspect(FIRST)).contains(new LocalDisplayName("Grzegorz"));
        assertThat(store.auditEvents()).hasSize(1);

        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.exact(new LocalDisplayName("Grzegorz")),
                                "Grzegorz",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.UNCHANGED);
        assertThat(store.auditEvents()).hasSize(1);

        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.exact(new LocalDisplayName("Grzegorz")),
                                "Gżegorz",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(store.auditEvents()).hasSize(2);
        assertThat(store.auditEvents().getLast().previousDisplayName())
                .contains(new LocalDisplayName("Grzegorz"));
        assertThat(store.auditEvents().getLast().newDisplayName())
                .contains(new LocalDisplayName("Gżegorz"));

        assertThat(
                        service.clearDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.present(),
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(service.inspect(FIRST)).isEmpty();
        assertThat(store.auditEvents()).hasSize(3);
        assertThat(store.auditEvents().getLast().action())
                .isEqualTo(LocalDisplayNameAuditAction.CLEAR);
    }

    @Test
    void failuresAndNoOpsDoNotCreateEvents() {
        InMemoryLocalDisplayNameStore store = new InMemoryLocalDisplayNameStore(10, 10);
        LocalDisplayNameAdministrationService service = service(store);

        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.present(),
                                "Name",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.NOT_FOUND);
        assertThat(
                        service.clearDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.present(),
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.NOT_FOUND);
        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.absent(),
                                "bad\u202evalue",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.INVALID_VALUE);
        assertThat(store.auditEvents()).isEmpty();

        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.absent(),
                                "Name",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.absent(),
                                "Other",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH);
        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.exact(new LocalDisplayName("Wrong")),
                                "Other",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH);
        assertThat(store.auditEvents()).hasSize(1);
    }

    @Test
    void deterministicListAllowsDuplicateNamesAcrossPlayerIds() {
        InMemoryLocalDisplayNameStore store = new InMemoryLocalDisplayNameStore(10, 10);
        LocalDisplayNameAdministrationService service = service(store);
        assertThat(
                        service.setDisplayName(
                                SECOND,
                                LocalDisplayNameExpectation.absent(),
                                "Shared",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.absent(),
                                "Shared",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);

        assertThat(service.displayNames())
                .containsExactly(
                        new LocalDisplayNameAssignment(FIRST, new LocalDisplayName("Shared")),
                        new LocalDisplayNameAssignment(SECOND, new LocalDisplayName("Shared")));
    }

    @Test
    void capacityBlocksMutationWhenStateOrAuditCannotBeRetained() {
        InMemoryLocalDisplayNameStore store = new InMemoryLocalDisplayNameStore(1, 1);
        LocalDisplayNameAdministrationService service = service(store);
        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.absent(),
                                "First",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);

        assertThat(
                        service.setDisplayName(
                                SECOND,
                                LocalDisplayNameExpectation.absent(),
                                "Second",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(
                        service.clearDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.present(),
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.CAPACITY_EXCEEDED);
        assertThat(service.inspect(FIRST)).contains(new LocalDisplayName("First"));
        assertThat(store.auditEvents()).hasSize(1);
    }

    @Test
    void twoConcurrentExactUpdatesHaveOneWinner()
            throws InterruptedException, ExecutionException, TimeoutException {
        InMemoryLocalDisplayNameStore store = new InMemoryLocalDisplayNameStore(10, 10);
        LocalDisplayNameAdministrationService service = service(store);
        assertThat(
                        service.setDisplayName(
                                FIRST,
                                LocalDisplayNameExpectation.absent(),
                                "Initial",
                                ADMINISTRATOR,
                                REASON))
                .isEqualTo(LocalDisplayNameAdministrationResult.APPLIED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalDisplayNameAdministrationResult> first =
                    executor.submit(() -> raceSet(service, "One", ready, start));
            Future<LocalDisplayNameAdministrationResult> second =
                    executor.submit(() -> raceSet(service, "Two", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            LocalDisplayNameAdministrationResult.APPLIED,
                            LocalDisplayNameAdministrationResult.EXPECTATION_MISMATCH);
        }
        assertThat(store.auditEvents()).hasSize(2);
    }

    private static LocalDisplayNameAdministrationResult raceSet(
            LocalDisplayNameAdministrationService service,
            String replacement,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent display-name test did not start");
        }
        return service.setDisplayName(
                FIRST,
                LocalDisplayNameExpectation.exact(new LocalDisplayName("Initial")),
                replacement,
                ADMINISTRATOR,
                REASON);
    }

    private static LocalDisplayNameAdministrationService service(
            InMemoryLocalDisplayNameStore store) {
        return new LocalDisplayNameAdministrationService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

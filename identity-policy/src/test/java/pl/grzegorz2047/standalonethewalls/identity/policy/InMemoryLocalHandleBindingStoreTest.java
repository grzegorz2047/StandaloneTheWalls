package pl.grzegorz2047.standalonethewalls.identity.policy;

import static org.assertj.core.api.Assertions.assertThat;

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

class InMemoryLocalHandleBindingStoreTest {
    private static final CanonicalHandle HANDLE = new CanonicalHandle("local_player");
    private static final PlayerId FIRST = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND = new PlayerId("sf1_" + "b".repeat(52));

    @Test
    void bindsFirstUseAndRejectsAConflictingIdentity() {
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore();

        assertThat(store.bindOrVerify(HANDLE, FIRST)).isEqualTo(LocalHandleBindingResult.BOUND);
        assertThat(store.bindOrVerify(HANDLE, FIRST)).isEqualTo(LocalHandleBindingResult.MATCHED);
        assertThat(store.bindOrVerify(HANDLE, SECOND))
                .isEqualTo(LocalHandleBindingResult.CONFLICT);
        assertThat(store.find(HANDLE)).contains(FIRST);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void exactlyOneDifferentIdentityWinsConcurrentFirstUse()
            throws InterruptedException, ExecutionException, TimeoutException {
        InMemoryLocalHandleBindingStore store = new InMemoryLocalHandleBindingStore();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<LocalHandleBindingResult> first =
                    executor.submit(() -> race(store, FIRST, ready, start));
            Future<LocalHandleBindingResult> second =
                    executor.submit(() -> race(store, SECOND, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(
                            List.of(
                                    first.get(5, TimeUnit.SECONDS),
                                    second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            LocalHandleBindingResult.BOUND, LocalHandleBindingResult.CONFLICT);
            assertThat(store.size()).isEqualTo(1);
        }
    }

    private static LocalHandleBindingResult race(
            InMemoryLocalHandleBindingStore store,
            PlayerId playerId,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent binding test did not start in time");
        }
        return store.bindOrVerify(HANDLE, playerId);
    }
}

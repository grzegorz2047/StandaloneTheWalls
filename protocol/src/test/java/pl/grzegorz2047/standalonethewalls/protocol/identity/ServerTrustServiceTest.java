package pl.grzegorz2047.standalonethewalls.protocol.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerTrustServiceTest {
    private static final ServerReference REFERENCE =
            new ServerReference("example.org:27420");
    private static final ServerId FIRST = new ServerId("sfs1_" + "a".repeat(52));
    private static final ServerId SECOND = new ServerId("sfs1_" + "b".repeat(52));

    @Test
    void firstUseInspectionDoesNotWriteUntilExplicitConfirmation()
            throws ServerTrustStoreException {
        MemoryStore store = new MemoryStore();
        ServerTrustService service = new ServerTrustService(store);

        ServerTrustDecision inspection = service.inspect(REFERENCE, FIRST, Optional.empty());

        assertEquals(
                ServerTrustDecision.Status.FIRST_USE_REQUIRES_CONFIRMATION,
                inspection.status());
        assertTrue(store.records.isEmpty());

        ServerTrustRecord record =
                service.confirmFirstUse(
                        REFERENCE, FIRST, Optional.empty(), "user confirmed fingerprint");

        assertEquals(ServerTrustRecord.Source.TOFU, record.source());
        assertTrue(service.inspect(REFERENCE, FIRST, Optional.empty()).isTrusted());
    }

    @Test
    void changedIdentityDoesNotOverwriteExistingTrust() throws ServerTrustStoreException {
        MemoryStore store = new MemoryStore();
        ServerTrustService service = new ServerTrustService(store);
        ServerTrustRecord original =
                service.confirmFirstUse(
                        REFERENCE, FIRST, Optional.empty(), "user confirmed fingerprint");

        ServerTrustDecision changed = service.inspect(REFERENCE, SECOND, Optional.empty());

        assertEquals(ServerTrustDecision.Status.CHANGED_IDENTITY, changed.status());
        assertEquals(original, store.records.get(REFERENCE));
    }

    @Test
    void expectedPinTakesPrecedenceAndCannotBeStoredAsTofu()
            throws ServerTrustStoreException {
        MemoryStore store = new MemoryStore();
        ServerTrustService service = new ServerTrustService(store);

        assertTrue(service.inspect(REFERENCE, FIRST, Optional.of(FIRST)).isTrusted());
        assertEquals(
                ServerTrustDecision.Status.EXPECTED_PIN_MISMATCH,
                service.inspect(REFERENCE, SECOND, Optional.of(FIRST)).status());
        assertThrows(
                IllegalStateException.class,
                () ->
                        service.confirmFirstUse(
                                REFERENCE, FIRST, Optional.of(FIRST), "invalid tofu attempt"));
        assertTrue(store.records.isEmpty());
    }

    @Test
    void replacementIsExplicitAuditableAndCompareAndReplaceProtected()
            throws ServerTrustStoreException {
        MemoryStore store = new MemoryStore();
        ServerTrustService service = new ServerTrustService(store);
        ServerTrustRecord original =
                service.confirmFirstUse(
                        REFERENCE, FIRST, Optional.empty(), "initial confirmation");

        ServerTrustRecord replacement =
                service.replace(original, SECOND, "administrator approved planned rotation");

        assertEquals(ServerTrustRecord.Source.EXPLICIT_REPLACEMENT, replacement.source());
        assertTrue(service.inspect(REFERENCE, SECOND, Optional.empty()).isTrusted());
        assertThrows(
                IllegalStateException.class,
                () -> service.replace(original, FIRST, "stale replacement attempt"));
        assertEquals(replacement, store.records.get(REFERENCE));
    }

    @Test
    void rejectsUnsafeAuditReasonsAndReferences() {
        assertThrows(IllegalArgumentException.class, () -> new ServerReference("UPPERCASE"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ServerTrustRecord(
                                REFERENCE, FIRST, ServerTrustRecord.Source.TOFU, "\u202Ehidden"));
    }

    private static final class MemoryStore implements ServerTrustStore {
        private final Map<ServerReference, ServerTrustRecord> records = new HashMap<>();

        @Override
        public Optional<ServerTrustRecord> find(ServerReference reference) {
            return Optional.ofNullable(records.get(reference));
        }

        @Override
        public boolean saveIfAbsent(ServerTrustRecord record) {
            return records.putIfAbsent(record.reference(), record) == null;
        }

        @Override
        public boolean replace(ServerTrustRecord expected, ServerTrustRecord replacement) {
            return records.replace(expected.reference(), expected, replacement);
        }
    }
}

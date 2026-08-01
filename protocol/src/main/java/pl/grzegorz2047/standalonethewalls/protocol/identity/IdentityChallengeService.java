package pl.grzegorz2047.standalonethewalls.protocol.identity;

import java.util.Objects;
import java.util.UUID;

/** Safe server-facing API that consumes each challenge before signature verification. */
public final class IdentityChallengeService {
    private final ChallengeLedger ledger;

    public IdentityChallengeService(ChallengeLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public IdentityChallenge issue(
            ServerId serverId, UUID sessionId, SecureChannelBinding channelBinding) {
        return ledger.issue(serverId, sessionId, channelBinding);
    }

    public IdentityVerification verify(UUID sessionId, IdentityProof proof) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(proof, "proof");
        ChallengeConsumption consumption = ledger.consume(sessionId);
        return switch (consumption.status()) {
            case MISSING ->
                    IdentityVerification.rejected(IdentityVerification.Status.MISSING_CHALLENGE);
            case EXPIRED ->
                    IdentityVerification.rejected(IdentityVerification.Status.EXPIRED_CHALLENGE);
            case AVAILABLE ->
                    IdentityAuthenticator.verify(consumption.challenge().orElseThrow(), proof);
        };
    }

    public int outstandingCount() {
        return ledger.outstandingCount();
    }
}

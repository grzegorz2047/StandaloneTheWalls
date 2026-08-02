package pl.grzegorz2047.standalonethewalls.server.administration.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationReason;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleAdministrationResult;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalHandleBinding;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalIdentityAdministratorId;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBan;
import pl.grzegorz2047.standalonethewalls.identity.policy.LocalPlayerBanAdministrationResult;
import pl.grzegorz2047.standalonethewalls.protocol.identity.CanonicalHandle;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerId;
import pl.grzegorz2047.standalonethewalls.registry.RegistryRootId;
import pl.grzegorz2047.standalonethewalls.registry.RegistrySnapshotException;

class IdentityAdministrationCliRendererTest {
    private static final CanonicalHandle FIRST_HANDLE = new CanonicalHandle("alpha");
    private static final CanonicalHandle SECOND_HANDLE = new CanonicalHandle("beta");
    private static final PlayerId FIRST_PLAYER = new PlayerId("sf1_" + "a".repeat(52));
    private static final PlayerId SECOND_PLAYER = new PlayerId("sf1_" + "b".repeat(52));
    private static final Instant NOW = Instant.parse("2026-08-02T13:45:00Z");
    private static final LocalIdentityAdministratorId ADMINISTRATOR =
            new LocalIdentityAdministratorId("local-cli");
    private static final LocalHandleAdministrationReason REASON =
            new LocalHandleAdministrationReason("Manual local review");

    @Test
    void rendersHandleBindingsInProvidedDeterministicOrder() {
        IdentityAdministrationCliOutput output =
                IdentityAdministrationCliRenderer.render(
                        new IdentityAdministrationResponse.Handles(
                                List.of(
                                        new LocalHandleBinding(FIRST_HANDLE, FIRST_PLAYER),
                                        new LocalHandleBinding(SECOND_HANDLE, SECOND_PLAYER))));

        assertThat(output.successful()).isTrue();
        assertThat(output.lines())
                .containsExactly(
                        "response=HANDLES_LISTED",
                        "count=2",
                        "binding handle=alpha playerId=" + FIRST_PLAYER.value(),
                        "binding handle=beta playerId=" + SECOND_PLAYER.value());
    }

    @Test
    void rendersBanInspectionThroughExplicitFields() {
        LocalPlayerBan ban = new LocalPlayerBan(FIRST_PLAYER, NOW, ADMINISTRATOR, REASON);

        IdentityAdministrationCliOutput output =
                IdentityAdministrationCliRenderer.render(
                        new IdentityAdministrationResponse.BanInspection(
                                FIRST_PLAYER, Optional.of(ban)));

        assertThat(output.successful()).isTrue();
        assertThat(output.lines())
                .containsExactly(
                        "response=BAN_INSPECTED",
                        "playerId=" + FIRST_PLAYER.value(),
                        "found=true",
                        "ban playerId="
                                + FIRST_PLAYER.value()
                                + " bannedAt=2026-08-02T13:45:00Z administratorId=local-cli reason=Manual local review");
    }

    @Test
    void mapsMutationAndPermissionResultsToSemanticSuccess() {
        IdentityAdministrationCliOutput conflict =
                IdentityAdministrationCliRenderer.render(
                        new IdentityAdministrationResponse.HandleMutation(
                                LocalHandleAdministrationResult.CONFLICT));
        IdentityAdministrationCliOutput idempotentUnban =
                IdentityAdministrationCliRenderer.render(
                        new IdentityAdministrationResponse.BanMutation(
                                LocalPlayerBanAdministrationResult.NOT_BANNED));
        IdentityAdministrationCliOutput denied =
                IdentityAdministrationCliRenderer.render(
                        new IdentityAdministrationResponse.PermissionDenied(
                                IdentityAdministrationPermission.MANAGE_REGISTRY));

        assertThat(conflict.successful()).isFalse();
        assertThat(conflict.lines())
                .containsExactly("response=HANDLE_MUTATION_COMPLETED", "result=CONFLICT");
        assertThat(idempotentUnban.successful()).isTrue();
        assertThat(idempotentUnban.lines())
                .containsExactly("response=BAN_MUTATION_COMPLETED", "result=NOT_BANNED");
        assertThat(denied.successful()).isFalse();
        assertThat(denied.lines())
                .containsExactly(
                        "response=PERMISSION_DENIED", "requiredPermission=MANAGE_REGISTRY");
    }

    @Test
    void rendersSafeRegistrySummaryAndRejection() {
        RegistrySnapshotSummary summary =
                new RegistrySnapshotSummary(
                        12L, NOW, new RegistryRootId("sfr1_" + "c".repeat(52)), "d".repeat(64), 42);
        IdentityAdministrationCliOutput activated =
                IdentityAdministrationCliRenderer.render(
                        new IdentityAdministrationResponse.RegistryOperation(
                                RegistryAdministrationResult.activated(summary)));
        IdentityAdministrationCliOutput rejected =
                IdentityAdministrationCliRenderer.render(
                        new IdentityAdministrationResponse.RegistryOperation(
                                RegistryAdministrationResult.snapshotRejected(
                                        RegistrySnapshotException.Code.INVALID_SIGNATURE)));

        assertThat(activated.successful()).isTrue();
        assertThat(activated.lines())
                .containsExactly(
                        "response=REGISTRY_OPERATION_COMPLETED",
                        "result=ACTIVATED",
                        "sequence=12",
                        "generatedAt=2026-08-02T13:45:00Z",
                        "rootKeyId=sfr1_" + "c".repeat(52),
                        "sha256=" + "d".repeat(64),
                        "entries=42");
        assertThat(rejected.successful()).isFalse();
        assertThat(rejected.lines())
                .containsExactly(
                        "response=REGISTRY_OPERATION_COMPLETED",
                        "result=SNAPSHOT_REJECTED",
                        "rejection=INVALID_SIGNATURE");
    }
}

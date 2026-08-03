package pl.grzegorz2047.standalonethewalls.client.ui.directconnect;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientLanguage;
import pl.grzegorz2047.standalonethewalls.client.i18n.ClientMessages;
import pl.grzegorz2047.standalonethewalls.client.network.DirectConnectFailureCode;
import pl.grzegorz2047.standalonethewalls.protocol.identity.PlayerSessionAdmissionStatus;

class DirectConnectLocalizationTest {
    @Test
    void localizesEveryPublicFailureAdmissionAndPresentationPhaseInEnglishAndPolish() {
        for (ClientLanguage language : ClientLanguage.values()) {
            ClientMessages messages = ClientMessages.forLanguage(language);
            for (DirectConnectFailureCode code : DirectConnectFailureCode.values()) {
                assertPresent(messages, "direct.failure." + code.name().toLowerCase(Locale.ROOT));
            }
            for (PlayerSessionAdmissionStatus status : PlayerSessionAdmissionStatus.values()) {
                assertPresent(
                        messages, "direct.admission." + status.name().toLowerCase(Locale.ROOT));
            }
            for (DirectConnectUiPhase phase : DirectConnectUiPhase.values()) {
                if (phase != DirectConnectUiPhase.FORM) {
                    assertPresent(
                            messages, "direct.status." + phase.name().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private static void assertPresent(ClientMessages messages, String key) {
        assertFalse(messages.text(key).isBlank(), () -> "missing localization key: " + key);
    }
}

package pl.grzegorz2047.standalonethewalls.identity.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class LocalDisplayNameTest {
    @Test
    void normalizesToNfcAndTrimsUnicodeWhitespace() {
        LocalDisplayName value = new LocalDisplayName(" \u00a0Cafe\u0301\u00a0 ");

        assertThat(value.value()).isEqualTo("Café");
        assertThat(Normalizer.isNormalized(value.value(), Normalizer.Form.NFC)).isTrue();
        assertThat(Character.isWhitespace(value.value().codePointAt(0))).isFalse();
        assertThat(Character.isWhitespace(value.value().codePointBefore(value.value().length())))
                .isFalse();
    }

    @Test
    void acceptsExactCodePointBoundaryAndRejectsTheNextPoint() {
        assertThat(new LocalDisplayName("a".repeat(LocalDisplayName.MAXIMUM_CODE_POINTS)).value())
                .hasSize(LocalDisplayName.MAXIMUM_CODE_POINTS);
        assertThatThrownBy(
                        () ->
                                new LocalDisplayName(
                                        "a".repeat(LocalDisplayName.MAXIMUM_CODE_POINTS + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("display name exceeds the code point limit");
    }

    @Test
    void acceptsExactUtf8BoundaryAndRejectsTheNextCharacter() {
        String exact = "😀".repeat(LocalDisplayName.MAXIMUM_UTF8_BYTES / 4);
        assertThat(exact.getBytes(StandardCharsets.UTF_8))
                .hasSize(LocalDisplayName.MAXIMUM_UTF8_BYTES);
        assertThat(new LocalDisplayName(exact).value()).isEqualTo(exact);

        assertThatThrownBy(() -> new LocalDisplayName(exact + "😀"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("display name exceeds the UTF-8 byte limit");
    }

    @Test
    void rejectsInputAboveThePreNormalizationValidationBound() {
        int oversizedInput = LocalDisplayName.MAXIMUM_INPUT_UTF16_CODE_UNITS + 1;

        assertThatThrownBy(() -> new LocalDisplayName("a".repeat(oversizedInput)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("display name input exceeds the validation limit");
    }

    @Test
    void rejectsEmptyAndWhitespaceOnlyValues() {
        assertThatThrownBy(() -> new LocalDisplayName(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalDisplayName(" \u00a0 "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsControlsNulBidiAndDangerousFormatting() {
        assertRejected("name\t");
        assertRejected("name\u0000");
        assertRejected("name\u202e");
        assertRejected("name\u2066");
        assertRejected("name\u200d");
    }

    @Test
    void rejectsMalformedSurrogatesAndUnassignedCodePoints() {
        assertRejected("name\ud800");
        assertRejected("name\udc00");
        assertRejected("name\u0378");
    }

    @Test
    void validationErrorsAreBoundedAndDoNotEchoTheValue() {
        String input = "secret-value\u202e";

        assertThatThrownBy(() -> new LocalDisplayName(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-value");
    }

    private static void assertRejected(String value) {
        assertThatThrownBy(() -> new LocalDisplayName(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

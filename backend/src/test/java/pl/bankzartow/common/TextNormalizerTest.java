package pl.bankzartow.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void returnsEmptyStringForNull() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void returnsEmptyStringForWhitespaceOnly() {
        assertThat(TextNormalizer.normalize(" \t\n  \n")).isEmpty();
    }

    @Test
    void collapsesRepeatedSpacesWithinLine() {
        assertThat(TextNormalizer.normalize("Kot   zrzucił \t słownik")).isEqualTo("Kot zrzucił słownik");
    }

    @Test
    void treatsNonBreakingSpaceAsSpace() {
        assertThat(TextNormalizer.normalize("z półki")).isEqualTo("z półki");
    }

    @Test
    void preservesLineBreaksBetweenSetupAndPunchline() {
        assertThat(TextNormalizer.normalize("Sąsiad pożyczył drabinę.  \n  Patrzy na mnie z góry."))
                .isEqualTo("Sąsiad pożyczył drabinę.\nPatrzy na mnie z góry.");
    }

    @Test
    void convertsWindowsLineEndings() {
        assertThat(TextNormalizer.normalize("raz\r\ndwa")).isEqualTo("raz\ndwa");
    }

    @Test
    void stripsLeadingAndTrailingBlankLines() {
        assertThat(TextNormalizer.normalize("\n\n żart \n\n")).isEqualTo("żart");
    }
}

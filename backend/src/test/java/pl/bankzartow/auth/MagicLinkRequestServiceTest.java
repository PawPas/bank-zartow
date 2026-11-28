package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the pure parts of the magic-link request (task T-021):
 * address normalization and confirm-link building. No Spring, no database.
 */
class MagicLinkRequestServiceTest {

    @Test
    void normalizesEmailByTrimmingAndLowercasing() {
        assertThat(MagicLinkRequestService.normalizeEmail("  Owner@EXAMPLE.Test  "))
                .isEqualTo("owner@example.test");
    }

    @Test
    void normalizationLeavesAnAlreadyNormalAddressUntouched() {
        assertThat(MagicLinkRequestService.normalizeEmail("owner@example.test"))
                .isEqualTo("owner@example.test");
    }

    @Test
    void buildsConfirmLinkWithTokenInFragmentOnFixedPath() {
        assertThat(MagicLinkRequestService.confirmLink("https://bank.example.test", "abc123"))
                .isEqualTo("https://bank.example.test/login/confirm#token=abc123");
    }

    @Test
    void stripsTrailingSlashesFromThePublicUrl() {
        assertThat(MagicLinkRequestService.confirmLink("https://bank.example.test/", "t0k"))
                .isEqualTo("https://bank.example.test/login/confirm#token=t0k");
    }

    @Test
    void linkCarriesTokenOnlyInTheFragmentNeverInTheQuery() {
        String link = MagicLinkRequestService.confirmLink("https://bank.example.test", "sekret");

        assertThat(link).contains("#token=sekret");
        assertThat(link)
                .as("the token must travel in the URL fragment, never in a query parameter")
                .doesNotContain("?");
    }

    @Test
    void buildsConfirmLinkWhenPublicUrlContainsPath() {
        // APP_PUBLIC_URL with a path component is valid (gate allows it); the token
        // path is appended to whatever path the base URL already has.
        assertThat(MagicLinkRequestService.confirmLink("https://bank.example.test/app", "abc"))
                .isEqualTo("https://bank.example.test/app/login/confirm#token=abc");
    }
}

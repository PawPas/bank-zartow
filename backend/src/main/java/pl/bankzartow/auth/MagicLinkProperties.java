package pl.bankzartow.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Magic-link configuration (task T-020). All values are optional: a missing
 * address or public URL merely keeps {@link MagicLinkGate} closed and never
 * prevents application startup — unlike the password credentials, whose
 * fail-closed startup check ({@link AuthProperties}) is independent of this
 * feature and stays untouched.
 *
 * <p>Bound from the environment via {@link MagicLinkConfig}: {@code APP_AUTH_EMAIL}
 * (the only allowed address, normalized to trimmed lower case),
 * {@code APP_PUBLIC_URL} (base of the absolute link in the mail),
 * {@code APP_MAGIC_LINK_TTL_MINUTES} (default 15 — reversible proposal D-ML4) and
 * {@code APP_MAGIC_LINK_MAX_ACTIVE_TOKENS} (default 3 — reversible proposal D-ML6).
 */
public record MagicLinkProperties(
        Optional<String> email,
        Optional<String> publicUrl,
        Duration tokenTtl,
        int maxActiveTokens) {

    static final long DEFAULT_TTL_MINUTES = 15;
    static final int DEFAULT_MAX_ACTIVE_TOKENS = 3;

    public MagicLinkProperties {
        if (email == null || publicUrl == null) {
            throw new IllegalArgumentException("email and publicUrl must be Optionals, never null");
        }
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "app.magic-link.ttl-minutes must be positive, was " + tokenTtl);
        }
        if (maxActiveTokens <= 0) {
            throw new IllegalArgumentException(
                    "app.magic-link.max-active-tokens must be positive, was " + maxActiveTokens);
        }
    }

    /**
     * Builds the properties from raw environment values: blank or missing strings
     * become empty, the e-mail is normalized (trim + lower case), and missing
     * numbers fall back to the reversible defaults.
     */
    static MagicLinkProperties from(String email, String publicUrl, Long ttlMinutes,
            Integer maxActiveTokens) {
        return new MagicLinkProperties(
                presentOrEmpty(email).map(value -> value.toLowerCase(Locale.ROOT)),
                presentOrEmpty(publicUrl),
                Duration.ofMinutes(ttlMinutes == null ? DEFAULT_TTL_MINUTES : ttlMinutes),
                maxActiveTokens == null ? DEFAULT_MAX_ACTIVE_TOKENS : maxActiveTokens);
    }

    /**
     * Returns {@code true} when {@code url} is an absolute HTTP or HTTPS URL
     * with no query string, no fragment, and no userinfo (credentials). An
     * optional path component is allowed: {@code https://host/app} is valid and
     * the confirm link will be {@code https://host/app/login/confirm#token=...}.
     * Any URL that fails this check keeps {@link MagicLinkGate} closed (fail-closed).
     */
    static boolean isValidPublicUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url.trim());
            return uri.isAbsolute()
                    && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static Optional<String> presentOrEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.trim());
    }
}

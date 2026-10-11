package pl.bankzartow.common;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cleans up text typed on a phone without changing its line structure,
 * because line breaks carry meaning in jokes (limericks, setup and punchline).
 */
public final class TextNormalizer {

    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.lines()
                .map(line -> HORIZONTAL_WHITESPACE.matcher(line).replaceAll(" ").strip())
                .collect(Collectors.joining("\n"))
                .strip();
    }
}

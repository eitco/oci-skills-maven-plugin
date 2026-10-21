package de.eitco.cicd.ociskills.core.files;

import java.util.regex.Pattern;

/**
 * An ant style path pattern matched against {@code /} separated relative paths:
 * <ul>
 *     <li>{@code ?} matches one character except {@code /}</li>
 *     <li>{@code *} matches zero or more characters except {@code /}</li>
 *     <li>{@code **} matches zero or more path segments</li>
 *     <li>a pattern ending with {@code /} is treated as ending with {@code /**}</li>
 * </ul>
 * A pattern ending with {@code /**} also matches the directory itself, e.g. {@code .git/**} matches {@code .git}.
 */
public final class AntPattern {

    private final String pattern;
    private final Pattern regex;

    private AntPattern(String pattern, Pattern regex) {
        this.pattern = pattern;
        this.regex = regex;
    }

    public static AntPattern compile(String pattern) {
        String normalized = pattern.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized + "**";
        }
        return new AntPattern(pattern, Pattern.compile(toRegex(normalized)));
    }

    private static String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        int index = 0;
        while (index < pattern.length()) {
            if (pattern.startsWith("**/", index)) {
                regex.append("(?:.*/)?");
                index += 3;
            } else if (pattern.startsWith("/**", index) && index + 3 == pattern.length()) {
                regex.append("(?:/.*)?");
                index += 3;
            } else if (pattern.startsWith("**", index)) {
                regex.append(".*");
                index += 2;
            } else {
                char character = pattern.charAt(index);
                if (character == '*') {
                    regex.append("[^/]*");
                } else if (character == '?') {
                    regex.append("[^/]");
                } else {
                    regex.append(Pattern.quote(String.valueOf(character)));
                }
                index++;
            }
        }
        return regex.toString();
    }

    public boolean matches(String path) {
        return regex.matcher(path).matches();
    }

    @Override
    public String toString() {
        return pattern;
    }
}

package de.eitco.cicd.ociskills.core.oci;

/**
 * Counts the words of a prompt file exactly like {@code skillctl} does: YAML frontmatter is stripped, the remaining
 * text is split at white space as defined by go's {@code unicode.IsSpace} and the non-empty fields are counted.
 */
public final class WordCounter {

    private static final String FRONTMATTER_DELIMITER = "---";
    private static final String FRONTMATTER_END = "\n---";

    private WordCounter() {
    }

    public static int countWords(String content) {
        String body = stripFrontmatter(content);
        int words = 0;
        boolean inWord = false;
        for (int index = 0; index < body.length(); ) {
            int codePoint = body.codePointAt(index);
            if (isGoSpace(codePoint)) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                words++;
            }
            index += Character.charCount(codePoint);
        }
        return words;
    }

    /**
     * Mirrors skillctl's {@code stripFrontmatter}: if the content starts with {@code ---}, everything up to and
     * including the next {@code \n---} is removed.
     */
    private static String stripFrontmatter(String content) {
        if (!content.startsWith(FRONTMATTER_DELIMITER)) {
            return content;
        }
        int end = content.indexOf(FRONTMATTER_END, FRONTMATTER_DELIMITER.length());
        if (end < 0) {
            return content;
        }
        return content.substring(end + FRONTMATTER_END.length());
    }

    /**
     * Mirrors go's {@code unicode.IsSpace}: '\t', '\n', '\v', '\f', '\r', ' ', U+0085 (NEL), U+00A0 (NBSP) and all
     * characters with the unicode White_Space property - which differs from {@link Character#isWhitespace(int)}.
     */
    private static boolean isGoSpace(int codePoint) {
        return switch (codePoint) {
            case '\t', '\n', 0x0B, '\f', '\r', ' ', 0x85, 0xA0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true;
            default -> codePoint >= 0x2000 && codePoint <= 0x200A;
        };
    }
}

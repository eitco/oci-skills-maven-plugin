package de.eitco.cicd.ociskills.core.oci;

import de.eitco.cicd.ociskills.core.GoldenMaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The expected values follow go's {@code len(strings.Fields(stripFrontmatter(content)))} as implemented in
 * skillctl 0.7.2 ({@code pkg/oci/build.go}) and were verified by running that go code.
 */
class WordCounterTest {

    @Test
    void countsGoldenMasterLikeSkillctl() throws Exception {
        String content = Files.readString(GoldenMaster.SOURCE.resolve("SKILL.md"));

        assertThat(WordCounter.countWords(content)).isEqualTo(11);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> cases() {
        return Stream.of(
                arguments("", 0),
                arguments("   \n\t ", 0),
                arguments("one", 1),
                arguments("one two", 2),
                arguments("  one   two  \n three\r\n", 3),
                arguments("# Heading\n\n- item *bold*", 5),
                arguments("```java\nint x = 1;\n```", 6),
                arguments("Gr\u00fc\u00dfe aus K\u00f6ln", 3),
                // vertical tab, form feed, NEL and NBSP are spaces in go
                arguments("a\u000bb\u000cc\u0085d\u00a0e", 5),
                // unicode Zs category, line and paragraph separators
                arguments("a\u1680b\u2000c\u200ad\u2028e\u2029f\u202fg\u3000h", 8),
                // zero width space is not a space in go
                arguments("a\u200bb", 1),
                // go does not treat information separators (java whitespace) and U+180E as space
                arguments("a\u205fb\u001cc\u001fd\u180ee", 2),
                // frontmatter is stripped
                arguments("---\nname: x\ndescription: a b c\n---\nbody text", 2),
                arguments("---\nname: x\n---", 0),
                // unclosed frontmatter is not stripped
                arguments("---\nname: x\nbody", 4),
                // only a leading --- starts frontmatter
                arguments("text\n---\nname: x\n---\nbody", 6),
                // skillctl matches "---" as prefix, so "----" also starts frontmatter
                arguments("----\nx\n---\ny", 1),
                // the closing delimiter is matched as "\n---" prefix of a line
                arguments("---\na\n----more\nb", 2),
                // the closing delimiter is searched starting right after the opening "---"
                arguments("---\n---\nbody", 1),
                arguments("---", 1));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void countsLikeSkillctl(String content, int expected) {
        assertThat(WordCounter.countWords(content)).isEqualTo(expected);
    }
}

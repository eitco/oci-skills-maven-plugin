package de.eitco.cicd.ociskills.core.files;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AntPatternTest {

    @ParameterizedTest(name = "{0} matches {1}: {2}")
    @CsvSource({
            "**/*,          SKILL.md,                  true",
            "**/*,          references/a/b.md,         true",
            "**,            references/a/b.md,         true",
            "*.md,          SKILL.md,                  true",
            "*.md,          references/a.md,           false",
            "**/*.md,       SKILL.md,                  true",
            "**/*.md,       references/a/b.md,         true",
            "**/*.md,       references/a/b.txt,        false",
            ".git/**,       .git,                      true",
            ".git/**,       .git/config,               true",
            ".git/**,       .git/refs/heads/main,      true",
            ".git/**,       .gitignore,                false",
            ".git/**,       sub/.git/config,           false",
            "**/.git/**,    sub/.git/config,           true",
            "target/**,     target/classes/x,          true",
            "target/**,     targets/x,                 false",
            "scripts/*.sh,  scripts/run.sh,            true",
            "scripts/*.sh,  scripts/sub/run.sh,        false",
            "scripts/**,    scripts/sub/run.sh,        true",
            "scripts/,      scripts/sub/run.sh,        true",
            "s?ill.md,      skill.md,                  true",
            "s?ill.md,      s/ill.md,                  false",
            "a.b+c(d),      a.b+c(d),                  true",
            "a.b+c(d),      aXb+c(d),                  false",
            "/SKILL.md,     SKILL.md,                  true",
            "SKILL.md,      SKILL.md,                  true",
            "SKILL.md,      skill.md,                  false",
    })
    void matches(String pattern, String path, boolean expected) {
        assertThat(AntPattern.compile(pattern).matches(path)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "scripts\\*.sh, scripts/run.sh",
    })
    void normalizesBackslashes(String pattern, String path) {
        assertThat(AntPattern.compile(pattern).matches(path)).isTrue();
    }
}

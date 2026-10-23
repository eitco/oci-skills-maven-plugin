package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.TestSkills;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecReaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readsScriptTests() throws Exception {
        Path file = TestSkills.write(tempDirectory, "tests.yaml", """
                tests:
                  - name: hello greets
                    command: [scripts/hello.sh, world]
                    stdin: input
                    env: { GREETING: Hello }
                    timeout: 30s
                    stdout: { contains: [Hello], matches: ["^Hello, world$"] }
                    stderr: { empty: true }
                  - name: fails without argument
                    command: [scripts/hello.sh]
                    exitCode: 2
                """);

        ScriptTests tests = SpecReader.read(file, ScriptTests.class);

        assertThat(tests.tests()).hasSize(2);
        ScriptTests.ScriptTest first = tests.tests().get(0);
        assertThat(first.name()).isEqualTo("hello greets");
        assertThat(first.command()).containsExactly("scripts/hello.sh", "world");
        assertThat(first.stdin()).isEqualTo("input");
        assertThat(first.env()).isEqualTo(Map.of("GREETING", "Hello"));
        assertThat(first.timeout()).isEqualTo("30s");
        assertThat(first.exitCode()).isZero();
        assertThat(first.stdout().contains()).containsExactly("Hello");
        assertThat(first.stderr().empty()).isTrue();
        assertThat(tests.tests().get(1).exitCode()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownProperties() {
        Path file = TestSkills.write(tempDirectory, "tests.yaml", """
                tests:
                  - name: typo
                    command: [ls]
                    exitcode: 1
                """);

        assertThatThrownBy(() -> SpecReader.read(file, ScriptTests.class))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("tests.yaml")
                .hasMessageContaining("exitcode");
    }

    @Test
    void rejectsMissingFiles() {
        assertThatThrownBy(() -> SpecReader.read(tempDirectory.resolve("tests.yaml"), ScriptTests.class))
                .isInstanceOf(SkillValidationException.class);
    }
}

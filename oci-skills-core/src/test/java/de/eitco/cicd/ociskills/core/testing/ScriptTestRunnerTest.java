package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.TestSkills;
import de.eitco.cicd.ociskills.core.report.TestCase;
import de.eitco.cicd.ociskills.core.report.TestStatus;
import de.eitco.cicd.ociskills.core.report.TestSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptTestRunnerTest {

    @TempDir
    Path skill;

    private final ScriptTestRunner runner = new ScriptTestRunner();

    @BeforeEach
    void createScript() throws Exception {
        Path script = TestSkills.write(skill, "scripts/hello.sh", """
                #!/bin/sh
                if [ -z "$1" ]; then echo "usage: hello.sh <name>" >&2; exit 2; fi
                read -r suffix || true
                echo "${GREETING:-Hello}, $1$suffix"
                echo "skill dir $SKILL_DIR"
                echo "cwd $(pwd)"
                """);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private static ScriptTests.ScriptTest test(List<String> command, String stdin, Map<String, String> env,
                                               String timeout, Integer exitCode, OutputExpectation stdout,
                                               OutputExpectation stderr) {
        return new ScriptTests.ScriptTest("test", command, stdin, env, timeout, exitCode, stdout, stderr);
    }

    private TestCase run(ScriptTests.ScriptTest test) throws Exception {
        TestSuite suite = runner.run(skill, new ScriptTests(List.of(test)), "skill.nexus-test.scripts");
        assertThat(suite.name()).isEqualTo("skill.nexus-test.scripts");
        assertThat(suite.cases()).hasSize(1);
        return suite.cases().get(0);
    }

    @Test
    void passesIfAllExpectationsHold() throws Exception {
        TestCase result = run(test(List.of("scripts/hello.sh", "world"), "!\n", Map.of("GREETING", "Ahoy"), null,
                null, new OutputExpectation(List.of("Ahoy"), List.of("^Ahoy, world!$",
                        "^skill dir " + skill.toRealPath() + "$", "^cwd " + skill.toRealPath() + "$"), null, null),
                new OutputExpectation(null, null, null, true)));

        assertThat(result.status()).as(result.message()).isEqualTo(TestStatus.PASSED);
        assertThat(result.name()).isEqualTo("test");
        assertThat(result.output()).contains("Ahoy, world!");
    }

    @Test
    void commandsWithoutSlashAreLookedUpInThePath() throws Exception {
        TestCase result = run(test(List.of("sh", "scripts/hello.sh", "you"), null, null, null, null,
                new OutputExpectation(List.of("Hello, you"), null, null, null), null));

        assertThat(result.status()).as(result.message()).isEqualTo(TestStatus.PASSED);
    }

    @Test
    void failsOnUnexpectedExitCode() throws Exception {
        TestCase result = run(test(List.of("scripts/hello.sh"), null, null, null, null, null, null));

        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
        assertThat(result.message()).isEqualTo("exit code is 2, expected 0");
        assertThat(result.output()).contains("usage: hello.sh <name>");
    }

    @Test
    void passesOnExpectedNonZeroExitCode() throws Exception {
        TestCase result = run(test(List.of("scripts/hello.sh"), null, null, null, 2, null,
                new OutputExpectation(List.of("usage"), null, null, null)));

        assertThat(result.status()).as(result.message()).isEqualTo(TestStatus.PASSED);
    }

    @Test
    void failsOnViolatedOutputExpectations() throws Exception {
        TestCase result = run(test(List.of("scripts/hello.sh", "world"), null, null, null, null,
                new OutputExpectation(List.of("Bye"), null, null, null),
                new OutputExpectation(null, null, null, false)));

        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
        assertThat(result.message()).isEqualTo("stdout does not contain 'Bye'\nstderr is empty");
    }

    @Test
    void failsOnTimeout() throws Exception {
        TestCase result = run(test(List.of("sleep", "10"), null, null, "200ms", null, null, null));

        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
        assertThat(result.message()).isEqualTo("timed out after 200ms");
    }

    @Test
    void failsIfTheCommandCannotBeStarted() throws Exception {
        TestCase result = run(test(List.of("scripts/missing.sh"), null, null, null, null, null, null));

        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
        assertThat(result.message()).startsWith("cannot run scripts/missing.sh");
    }

    @Test
    void failsWithoutCommand() throws Exception {
        TestCase result = run(test(List.of(), null, null, null, null, null, null));

        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
        assertThat(result.message()).isEqualTo("no command configured");
    }

    @Test
    void failsOnInvalidTimeout() throws Exception {
        TestCase result = run(test(List.of("true"), null, null, "soon", null, null, null));

        assertThat(result.status()).isEqualTo(TestStatus.FAILED);
        assertThat(result.message()).contains("'soon'");
    }
}

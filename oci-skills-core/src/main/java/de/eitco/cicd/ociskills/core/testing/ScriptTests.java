package de.eitco.cicd.ociskills.core.testing;

import java.util.List;
import java.util.Map;

/**
 * The content of {@code tests.yaml}: tests running the scripts of a skill.
 *
 * @param tests the tests
 */
public record ScriptTests(List<ScriptTest> tests) {

    public static final String FILE_NAME = "tests.yaml";

    public ScriptTests {
        tests = tests == null ? List.of() : List.copyOf(tests);
    }

    /**
     * A single script test.
     *
     * @param name     the name of the test
     * @param command  the command - a first element containing {@code /} is resolved against the skill directory
     * @param stdin    the input of the command - none if {@code null}
     * @param env      additional environment variables
     * @param timeout  the maximum duration, e.g. {@code 30s} - defaults to {@code 60s}
     * @param exitCode the expected exit code - defaults to {@code 0}
     * @param stdout   assertions on stdout
     * @param stderr   assertions on stderr
     */
    public record ScriptTest(String name, List<String> command, String stdin, Map<String, String> env, String timeout,
                             Integer exitCode, OutputExpectation stdout, OutputExpectation stderr) {

        public ScriptTest {
            command = command == null ? List.of() : List.copyOf(command);
            env = env == null ? Map.of() : Map.copyOf(env);
            exitCode = exitCode == null ? 0 : exitCode;
        }
    }
}

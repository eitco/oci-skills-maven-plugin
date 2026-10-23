package de.eitco.cicd.ociskills.core.testing;

import de.eitco.cicd.ociskills.core.SkillTestException;
import de.eitco.cicd.ociskills.core.report.TestCase;
import de.eitco.cicd.ociskills.core.report.TestSuite;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Runs the {@link ScriptTests} of a skill against its staged files. The working directory of every command is the
 * staged skill directory, which is also available as environment variable {@value #SKILL_DIR}.
 */
public class ScriptTestRunner {

    public static final String SKILL_DIR = "SKILL_DIR";

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /**
     * @param stagedSkill the staged skill directory, see {@link SkillStager}
     * @param tests       the tests to run
     * @param suiteName   the name of the resulting suite
     */
    public TestSuite run(Path stagedSkill, ScriptTests tests, String suiteName) throws SkillTestException {
        Path directory;
        try {
            directory = stagedSkill.toRealPath();
        } catch (IOException e) {
            throw new SkillTestException("staged skill " + stagedSkill + " cannot be read: " + e, e);
        }
        List<TestCase> cases = new ArrayList<>();
        for (ScriptTests.ScriptTest test : tests.tests()) {
            cases.add(run(directory, test));
        }
        return new TestSuite(suiteName, cases);
    }

    private static TestCase run(Path directory, ScriptTests.ScriptTest test) throws SkillTestException {
        String name = test.name() == null ? String.join(" ", test.command()) : test.name();
        if (test.command().isEmpty()) {
            return TestCase.failed(name, Duration.ZERO, "no command configured", null);
        }
        Duration timeout;
        try {
            timeout = Durations.parse(test.timeout(), DEFAULT_TIMEOUT);
        } catch (IllegalArgumentException e) {
            return TestCase.failed(name, Duration.ZERO, e.getMessage(), null);
        }

        List<String> command = new ArrayList<>(test.command());
        if (command.get(0).contains("/") && !Path.of(command.get(0)).isAbsolute()) {
            command.set(0, directory.resolve(command.get(0)).toString());
        }
        Path stdout = null;
        Path stderr = null;
        long start = System.nanoTime();
        try {
            stdout = Files.createTempFile("skill-test-", ".out");
            stderr = Files.createTempFile("skill-test-", ".err");
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile());
            builder.environment().putAll(test.env());
            builder.environment().put(SKILL_DIR, directory.toString());
            builder.environment().put("PWD", directory.toString());

            Process process;
            try {
                process = builder.start();
            } catch (IOException e) {
                return TestCase.failed(name, Duration.ZERO, "cannot run " + test.command().get(0) + ": "
                        + e.getMessage(), null);
            }
            try (OutputStream stdin = process.getOutputStream()) {
                if (test.stdin() != null) {
                    stdin.write(test.stdin().getBytes(UTF_8));
                }
            } catch (IOException e) {
                // the process does not read its input - that is fine
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            if (!finished) {
                destroy(process);
                return TestCase.failed(name, duration, "timed out after " + Durations.format(timeout),
                        output(test, null, stdout, stderr));
            }

            int exitCode = process.exitValue();
            String out = Files.readString(stdout, UTF_8);
            String err = Files.readString(stderr, UTF_8);
            List<String> violations = new ArrayList<>();
            if (exitCode != test.exitCode()) {
                violations.add("exit code is " + exitCode + ", expected " + test.exitCode());
            }
            if (test.stdout() != null) {
                violations.addAll(test.stdout().violations("stdout", out));
            }
            if (test.stderr() != null) {
                violations.addAll(test.stderr().violations("stderr", err));
            }
            String output = output(test, exitCode, stdout, stderr);
            return violations.isEmpty() ? TestCase.passed(name, duration, output)
                    : TestCase.failed(name, duration, String.join("\n", violations), output);
        } catch (IOException e) {
            throw new SkillTestException("running test '" + name + "' failed: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SkillTestException("running test '" + name + "' was interrupted", e);
        } finally {
            deleteQuietly(stdout);
            deleteQuietly(stderr);
        }
    }

    private static String output(ScriptTests.ScriptTest test, Integer exitCode, Path stdout, Path stderr)
            throws IOException {
        return "$ " + String.join(" ", test.command()) + "\n"
                + (exitCode == null ? "" : "exit code: " + exitCode + "\n")
                + "--- stdout ---\n" + Files.readString(stdout, UTF_8)
                + "--- stderr ---\n" + Files.readString(stderr, UTF_8);
    }

    /**
     * Kills the process and all its descendants and waits a moment for them to end.
     */
    private static void destroy(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // a temporary file is left behind
            }
        }
    }
}

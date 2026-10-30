package de.eitco.cicd.ociskills.git.jgit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs a command line tool installed on the machine - for tests checking compatibility with other tools.
 */
final class ExternalTool {

    /**
     * @param exitCode the exit code of the process
     * @param output   stdout and stderr of the process, interleaved
     */
    record Result(int exitCode, String output) {
    }

    private ExternalTool() {
    }

    /**
     * @return the executable with the given name on the {@code PATH}
     */
    static Optional<Path> find(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        return Stream.of(path.split(File.pathSeparator))
                .filter(directory -> !directory.isBlank())
                .map(directory -> Path.of(directory).resolve(name))
                .filter(Files::isExecutable)
                .findFirst();
    }

    /**
     * @param environment additional environment variables
     */
    static Result run(Path workDirectory, Map<String, String> environment, Duration timeout, List<String> command)
            throws IOException, InterruptedException {
        Path log = Files.createTempFile(workDirectory, "process", ".log");
        ProcessBuilder builder = new ProcessBuilder(command).directory(workDirectory.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getOutputStream().close();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException(String.join(" ", command) + " did not finish within " + timeout + ":\n"
                    + Files.readString(log, StandardCharsets.UTF_8));
        }
        return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
    }
}

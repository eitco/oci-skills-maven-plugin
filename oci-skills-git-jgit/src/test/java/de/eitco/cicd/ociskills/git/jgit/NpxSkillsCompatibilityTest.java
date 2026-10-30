package de.eitco.cicd.ociskills.git.jgit;

import de.eitco.cicd.ociskills.core.git.GitCredentials;
import de.eitco.cicd.ociskills.core.git.GitPublishRequest;
import de.eitco.cicd.ociskills.core.git.GitSkill;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Installs skills published by {@link JGitSkillRepositoryPublisher} with
 * <a href="https://github.com/vercel-labs/skills">npx skills</a>, served via smart http. Skipped if {@code npx} or
 * {@code git} are not on the {@code PATH} or the npm registry is not reachable.
 */
class NpxSkillsCompatibilityTest {

    /**
     * pinned, so the test only breaks if the plugin changes - update it deliberately
     */
    private static final String SKILLS_CLI = "skills@1.7.4";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final Pattern NPM_UNREACHABLE = Pattern.compile(
            "npm (error|ERR!).*(ENOTFOUND|EAI_AGAIN|ECONNREFUSED|ECONNRESET|ETIMEDOUT|ENETUNREACH|network)");

    @TempDir
    Path directory;

    private Path npx;
    private Path repositories;
    private GitHttpBackend backend;

    @BeforeEach
    void setUp() throws Exception {
        npx = ExternalTool.find("npx").orElse(null);
        Path git = ExternalTool.find("git").orElse(null);
        assumeTrue(npx != null, "npx not found on PATH");
        assumeTrue(git != null, "git not found on PATH");
        repositories = Files.createDirectories(directory.resolve("repositories"));
        Git.init().setBare(true).setInitialBranch("main").setDirectory(repositories.resolve("skills.git").toFile())
                .call().close();
        backend = new GitHttpBackend(git, repositories);
    }

    @AfterEach
    void tearDown() {
        if (backend != null) {
            backend.close();
        }
    }

    @Test
    void installsATaggedRelease() throws Exception {
        publish("acme-skills-1.0.0", Map.of("SKILL.md", skillMd("first"), "scripts/run.sh", "#!/bin/sh\necho hi\n"));
        publish(null, Map.of("SKILL.md", skillMd("second")));
        Path project = project("pinned");

        ExternalTool.Result result = skills(project, "add", url() + "#acme-skills-1.0.0", "--skill", "demo");

        assertThat(result.exitCode()).as(result.output()).isZero();
        Path skill = project.resolve(".claude/skills/demo");
        assertThat(skill.resolve("SKILL.md")).content().contains("first");
        assertThat(Files.isExecutable(skill.resolve("scripts/run.sh"))).isTrue();
        assertThat(project.resolve("skills-lock.json")).content()
                .contains("\"ref\": \"acme-skills-1.0.0\"")
                .contains("\"skillPath\": \"skills/demo/SKILL.md\"");
    }

    @Test
    void installsTheLatestStateOfTheBranchWithoutRef() throws Exception {
        publish("acme-skills-1.0.0", Map.of("SKILL.md", skillMd("first")));
        publish("acme-skills-1.1.0", Map.of("SKILL.md", skillMd("second")));
        Path project = project("latest");

        ExternalTool.Result result = skills(project, "add", url(), "--skill", "demo");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(project.resolve(".claude/skills/demo/SKILL.md")).content().contains("second");
    }

    private void publish(String tag, Map<String, String> files) throws Exception {
        new JGitSkillRepositoryPublisher().publish(new GitPublishRequest(
                repositories.resolve("skills.git").toUri().toString(), "main", "skills",
                List.of(new GitSkill("demo", new FakeSkillImage(files))), tag, tag == null ? "snapshot" : "release " + tag, "skill bot",
                "bot@example.com", GitCredentials.anonymous(), directory.resolve("work"), 1));
    }

    private String url() {
        // ends with .git - otherwise npx skills does not take the fragment as ref on hosts it does not know
        return backend.url("skills.git");
    }

    private Path project(String name) throws IOException {
        return Files.createDirectories(directory.resolve("projects").resolve(name));
    }

    private ExternalTool.Result skills(Path project, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(npx.toString(), "-y", SKILLS_CLI));
        command.addAll(List.of(arguments));
        command.addAll(List.of("-a", "claude-code", "-y"));
        ExternalTool.Result result = ExternalTool.run(project,
                Map.of("DO_NOT_TRACK", "1", "DISABLE_TELEMETRY", "1"), TIMEOUT, command);
        assumeFalse(result.exitCode() != 0 && NPM_UNREACHABLE.matcher(result.output()).find(),
                () -> "npm registry not reachable:\n" + result.output());
        return result;
    }

    private static String skillMd(String content) {
        return """
                ---
                name: demo
                description: Demo skill
                ---

                # Demo

                %s
                """.formatted(content);
    }
}

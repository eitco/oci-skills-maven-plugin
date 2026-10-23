package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillTestException;
import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.report.TestStatus;
import de.eitco.cicd.ociskills.core.report.TestSuite;
import de.eitco.cicd.ociskills.core.testing.ScriptTestRunner;
import de.eitco.cicd.ociskills.core.testing.ScriptTests;
import de.eitco.cicd.ociskills.core.testing.SkillStager;
import de.eitco.cicd.ociskills.core.testing.SpecReader;
import de.eitco.cicd.ociskills.core.testing.StaticSkillChecks;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests every skill:
 * <ul>
 *     <li>static checks - frontmatter, relative links, scripts, unreferenced files and the length of
 *     {@code SKILL.md}</li>
 *     <li>the script tests in {@code <skillTestsDirectory>/<skill>/tests.yaml}, run against a copy of the files that go
 *     into the image</li>
 * </ul>
 * The results are written as surefire reports. Failing tests fail the build.
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true)
public class SkillTestMojo extends AbstractSkillContentMojo {

    /**
     * Whether warnings of the static checks - e.g. unreferenced files - fail the build.
     */
    @Parameter(property = "skill.test.failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    /**
     * The directory the surefire reports are written to.
     */
    @Parameter(property = "skill.test.reportsDirectory", defaultValue = "${project.build.directory}/surefire-reports",
            required = true)
    private File reportsDirectory;

    /**
     * The directory the skills are copied to for the script tests - one subdirectory per skill.
     */
    @Parameter(defaultValue = "${project.build.directory}/skill-tests", required = true, readonly = true)
    private File stagingDirectory;

    /**
     * Skips the skill tests.
     */
    @Parameter(property = "skill.test.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Skips the skill tests like all other tests.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    /**
     * Skips the skill tests like all other tests.
     */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean mavenTestSkip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<Path> skills = skills();
        if (skip || skipTests || mavenTestSkip) {
            getLog().info((skip ? "skill.test.skip" : skipTests ? "skipTests" : "maven.test.skip")
                    + " is set - skipping skill tests");
            return;
        }

        TestReports reports = new TestReports(getLog(), reportsDirectory.toPath());
        List<String> failed = new ArrayList<>();
        for (Path skill : skills) {
            boolean skillFailed = false;
            for (TestSuite suite : test(skill)) {
                reports.report(suite);
                skillFailed |= suite.status() == TestStatus.FAILED;
            }
            if (skillFailed) {
                failed.add(name(skill));
            }
        }
        if (!failed.isEmpty()) {
            throw new MojoFailureException("skill tests failed for " + String.join(", ", failed) + " - see "
                    + reportsDirectory);
        }
    }

    private List<TestSuite> test(Path skill) throws MojoExecutionException, MojoFailureException {
        String prefix = "skill." + name(skill);
        List<TestSuite> suites = new ArrayList<>();
        try {
            suites.add(StaticSkillChecks.toSuite(prefix + ".static",
                    new StaticSkillChecks().check(skill, fileSelection(), skillCardSettings()), failOnWarning));
            Path testsFile = testDirectory(skill).resolve(ScriptTests.FILE_NAME);
            if (Files.isRegularFile(testsFile)) {
                ScriptTests tests = SpecReader.read(testsFile, ScriptTests.class);
                Path staged = new SkillStager().stage(skill, fileSelection(),
                        stagingDirectory.toPath().resolve(name(skill)));
                suites.add(new ScriptTestRunner().run(staged, tests, prefix + ".scripts"));
            }
        } catch (SkillValidationException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (SkillTestException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        return suites;
    }
}

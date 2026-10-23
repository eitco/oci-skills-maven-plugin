package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillTestException;
import de.eitco.cicd.ociskills.core.report.JUnitXmlWriter;
import de.eitco.cicd.ociskills.core.report.TestCase;
import de.eitco.cicd.ociskills.core.report.TestStatus;
import de.eitco.cicd.ociskills.core.report.TestSuite;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.nio.file.Path;

/**
 * Writes test suites as surefire reports and logs them like surefire does.
 */
final class TestReports {

    private final Log log;
    private final Path reportsDirectory;

    TestReports(Log log, Path reportsDirectory) {
        this.log = log;
        this.reportsDirectory = reportsDirectory;
    }

    void report(TestSuite suite) throws MojoExecutionException {
        try {
            new JUnitXmlWriter().write(suite, reportsDirectory);
        } catch (SkillTestException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        String summary = "Tests run: " + suite.cases().size() + ", Failures: " + suite.count(TestStatus.FAILED)
                + ", Skipped: " + suite.count(TestStatus.SKIPPED) + " - in " + suite.name();
        if (suite.status() == TestStatus.FAILED) {
            log.error(summary);
        } else {
            log.info(summary);
        }
        for (TestCase testCase : suite.cases()) {
            if (testCase.status() == TestStatus.FAILED) {
                log.error("  " + testCase.name() + ": " + indent(testCase.message()));
                if (testCase.output() != null) {
                    log.error("    " + indent(testCase.output()));
                }
            } else if (testCase.output() != null && testCase.output().startsWith("warning: ")) {
                log.warn("  " + testCase.name() + ": " + indent(testCase.output()));
            } else if (testCase.output() != null && log.isDebugEnabled()) {
                log.debug("  " + testCase.name() + ": " + indent(testCase.output()));
            }
        }
    }

    private static String indent(String text) {
        return text.replace("\n", "\n    ");
    }
}

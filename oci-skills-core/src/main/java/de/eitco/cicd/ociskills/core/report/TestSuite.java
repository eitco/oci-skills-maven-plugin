package de.eitco.cicd.ociskills.core.report;

import java.util.List;

/**
 * The results of a group of test cases, e.g. the static checks of one skill.
 *
 * @param name  the name of the suite, written like a class name, e.g. {@code skill.nexus-test.static}
 * @param cases the test cases in execution order
 */
public record TestSuite(String name, List<TestCase> cases) {

    public TestSuite {
        cases = List.copyOf(cases);
    }

    public long count(TestStatus status) {
        return cases.stream().filter(testCase -> testCase.status() == status).count();
    }

    /**
     * @return {@code FAILED} if a case failed, {@code SKIPPED} if there are no cases or all were skipped,
     * {@code PASSED} otherwise
     */
    public TestStatus status() {
        if (count(TestStatus.FAILED) > 0) {
            return TestStatus.FAILED;
        }
        return count(TestStatus.PASSED) > 0 ? TestStatus.PASSED : TestStatus.SKIPPED;
    }
}

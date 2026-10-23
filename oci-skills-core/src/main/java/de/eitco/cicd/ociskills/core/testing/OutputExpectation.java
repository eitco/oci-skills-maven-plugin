package de.eitco.cicd.ociskills.core.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Assertions on a text output, e.g. stdout or stderr of a script.
 *
 * @param contains   strings the output must contain
 * @param matches    regular expressions that must be found in the output - {@code ^} and {@code $} match at line
 *                   boundaries
 * @param notMatches regular expressions that must not be found in the output
 * @param empty      whether the output must be empty ({@code true}) or must not be empty ({@code false})
 */
public record OutputExpectation(List<String> contains, List<String> matches, List<String> notMatches, Boolean empty) {

    public OutputExpectation {
        contains = contains == null ? List.of() : List.copyOf(contains);
        matches = matches == null ? List.of() : List.copyOf(matches);
        notMatches = notMatches == null ? List.of() : List.copyOf(notMatches);
    }

    /**
     * @param what   what the output is, used in the messages, e.g. {@code stdout}
     * @param output the output to check
     * @return the violated assertions - empty if all hold
     */
    public List<String> violations(String what, String output) {
        List<String> violations = new ArrayList<>();
        for (String expected : contains) {
            if (!output.contains(expected)) {
                violations.add(what + " does not contain '" + expected + "'");
            }
        }
        for (String regex : matches) {
            find(what, output, regex, true, violations);
        }
        for (String regex : notMatches) {
            find(what, output, regex, false, violations);
        }
        if (Boolean.TRUE.equals(empty) && !output.isEmpty()) {
            violations.add(what + " is not empty");
        } else if (Boolean.FALSE.equals(empty) && output.isEmpty()) {
            violations.add(what + " is empty");
        }
        return violations;
    }

    private static void find(String what, String output, String regex, boolean expected, List<String> violations) {
        try {
            boolean found = Pattern.compile(regex, Pattern.MULTILINE).matcher(output).find();
            if (found != expected) {
                violations.add(what + (expected ? " does not match /" : " matches /") + regex + "/");
            }
        } catch (PatternSyntaxException e) {
            violations.add("invalid regular expression /" + regex + "/: " + e.getDescription());
        }
    }
}

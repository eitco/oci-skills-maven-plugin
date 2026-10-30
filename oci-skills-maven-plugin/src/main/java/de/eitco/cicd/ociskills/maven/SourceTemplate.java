package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A configured value with placeholders for the requested skill - e.g. the group id of a maven source
 * {@code com.example.{namespace}}. Namespace and name of the skill id are split into parts at {@code '-'}, the
 * {@code <repository>} of the skill at {@code '/'}. A placeholder selects all parts, one part or a slice of them -
 * negative indices count from the end, the end of a slice is exclusive:
 * <table>
 *     <caption>placeholders for the skill {@code cicd/nexus-admin-test} and the repository {@code teams/acme/skills}
 *     </caption>
 *     <tr><th>placeholder</th><th>value</th></tr>
 *     <tr><td>{@code {namespace}}, {@code {name}}</td><td>{@code cicd}, {@code nexus-admin-test}</td></tr>
 *     <tr><td>{@code {name[0]}}, {@code {name[-1]}}</td><td>{@code nexus}, {@code test}</td></tr>
 *     <tr><td>{@code {name[1:]}}, {@code {name[:-1]}}</td><td>{@code admin-test}, {@code nexus-admin}</td></tr>
 *     <tr><td>{@code {name[:0]}}</td><td>empty</td></tr>
 *     <tr><td>{@code {repository}}</td><td>{@code teams.acme.skills}</td></tr>
 *     <tr><td>{@code {repository.parent}}, {@code {repository[:-1]}}</td><td>{@code teams.acme}</td></tr>
 *     <tr><td>{@code {repository.last}}, {@code {repository[-1]}}</td><td>{@code skills}</td></tr>
 * </table>
 * Parts of namespace and name are joined with {@code '-'}, parts of the repository with {@code '.'}. A single index
 * has to exist, slices are cut to the available parts and may be empty. Repeated dots as well as dots and dashes at
 * the start or end of the expanded value are dropped - {@code skills-{name[1:]}} of the skill {@code cicd/solo} is
 * {@code skills}.
 */
final class SourceTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*}");
    private static final Pattern INDEXED = Pattern.compile(
            "\\{(namespace|name|repository)(\\[(-?\\d+)?(:(-?\\d+)?)?])?}");
    private static final String REPOSITORY_PARENT = "{repository.parent}";
    private static final String REPOSITORY_LAST = "{repository.last}";
    private static final String SUPPORTED = "{namespace}, {name}, {repository}, {name[i]}, {name[from:to]} "
            + "(likewise for namespace and repository, i, from and to may be negative, from and to may be omitted), "
            + REPOSITORY_PARENT + ", " + REPOSITORY_LAST;

    /**
     * The parts of the skill a placeholder selects from.
     */
    private enum Value {
        NAMESPACE("-"), NAME("-"), REPOSITORY(".");

        private final String delimiter;

        Value(String delimiter) {
            this.delimiter = delimiter;
        }

        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A placeholder: all parts if neither {@code index} nor slice bounds are set, one part if {@code index} is set,
     * otherwise the slice {@code [from:to)} with {@code null} meaning start or end.
     */
    private record Placeholder(int start, int end, String text, Value value, Integer index, Integer from, Integer to,
                               boolean slice) {

        String expand(SkillId id, String repository) {
            List<String> parts = List.of(switch (value) {
                case NAMESPACE -> id.namespace().split("-");
                case NAME -> id.name().split("-");
                case REPOSITORY -> repository.split("/");
            });
            if (index != null) {
                int position = index < 0 ? parts.size() + index : index;
                if (position < 0 || position >= parts.size()) {
                    throw new IllegalArgumentException("the " + value.label() + " of " + id
                            + (value == Value.REPOSITORY ? " (" + repository + ")" : "") + " has no part " + index
                            + ", needed by " + text);
                }
                return parts.get(position);
            }
            if (!slice) {
                return String.join(value.delimiter, parts);
            }
            int first = bound(from, 0, parts.size());
            int last = bound(to, parts.size(), parts.size());
            return first >= last ? "" : String.join(value.delimiter, parts.subList(first, last));
        }

        private static int bound(Integer bound, int fallback, int size) {
            if (bound == null) {
                return fallback;
            }
            return Math.max(0, Math.min(size, bound < 0 ? size + bound : bound));
        }
    }

    private final String template;
    private final List<Placeholder> placeholders;

    /**
     * @throws IllegalArgumentException if the template is empty or contains other placeholders
     */
    SourceTemplate(String parameter, String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("<" + parameter + "> must be configured");
        }
        this.template = template.trim();
        List<Placeholder> parsed = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(this.template);
        while (matcher.find()) {
            parsed.add(parse(parameter, matcher));
        }
        this.placeholders = List.copyOf(parsed);
    }

    private Placeholder parse(String parameter, Matcher matcher) {
        String text = matcher.group();
        int start = matcher.start();
        int end = matcher.end();
        if (REPOSITORY_PARENT.equals(text)) {
            return new Placeholder(start, end, text, Value.REPOSITORY, null, null, -1, true);
        }
        if (REPOSITORY_LAST.equals(text)) {
            return new Placeholder(start, end, text, Value.REPOSITORY, -1, null, null, false);
        }
        Matcher indexed = INDEXED.matcher(text);
        // {name[]} has neither index nor slice
        if (!indexed.matches() || indexed.group(2) != null && indexed.group(3) == null && indexed.group(4) == null) {
            throw new IllegalArgumentException("<" + parameter + "> " + template + " contains the unknown "
                    + "placeholder " + text + " - supported: " + SUPPORTED);
        }
        Value value = Value.valueOf(indexed.group(1).toUpperCase(Locale.ROOT));
        Integer from = indexed.group(3) == null ? null : Integer.valueOf(indexed.group(3));
        if (indexed.group(4) == null) {
            return new Placeholder(start, end, text, value, from, null, null, false);
        }
        Integer to = indexed.group(5) == null ? null : Integer.valueOf(indexed.group(5));
        return new Placeholder(start, end, text, value, null, from, to, true);
    }

    @Override
    public String toString() {
        return template;
    }

    /**
     * @param repository the {@code <repository>} of the skill - {@code null} if it has none
     * @return the expanded value - may be empty
     * @throws IllegalArgumentException if the template needs a repository, but the skill has none, or a part selected
     *                                  by a single index does not exist
     */
    String expand(SkillId id, String repository) {
        if (repository == null && needsRepository()) {
            throw new IllegalArgumentException("the skill " + id + " has no <repository>, but " + template
                    + " needs one");
        }
        StringBuilder value = new StringBuilder();
        int position = 0;
        for (Placeholder placeholder : placeholders) {
            value.append(template, position, placeholder.start()).append(placeholder.expand(id, repository));
            position = placeholder.end();
        }
        value.append(template.substring(position));
        return value.toString()
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^[.-]+|[.-]+$", "");
    }

    private boolean needsRepository() {
        return placeholders.stream().anyMatch(placeholder -> placeholder.value() == Value.REPOSITORY);
    }
}

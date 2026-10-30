package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A configured value with placeholders for the requested skill - e.g. the group id of a maven source
 * {@code com.example.{namespace}}:
 * <ul>
 *     <li>{@code {namespace}} and {@code {name}} of the skill id</li>
 *     <li>{@code {repository}}, {@code {repository.parent}} and {@code {repository.last}}: the {@code <repository>}
 *     of the skill with {@code '/'} replaced by {@code '.'}, without its last segment, and only its last segment.
 *     Repeated dots and dots at the start or end of the expanded value are dropped.</li>
 * </ul>
 */
final class SourceTemplate {

    static final String NAMESPACE = "{namespace}";
    static final String NAME = "{name}";
    static final String REPOSITORY = "{repository}";
    static final String REPOSITORY_PARENT = "{repository.parent}";
    static final String REPOSITORY_LAST = "{repository.last}";

    private static final List<String> PLACEHOLDERS = List.of(NAMESPACE, NAME, REPOSITORY, REPOSITORY_PARENT,
            REPOSITORY_LAST);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*}");

    private final String template;

    /**
     * @throws IllegalArgumentException if the template is empty or contains other placeholders
     */
    SourceTemplate(String parameter, String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("<" + parameter + "> must be configured");
        }
        Matcher placeholders = PLACEHOLDER.matcher(template);
        while (placeholders.find()) {
            String placeholder = placeholders.group();
            if (!PLACEHOLDERS.contains(placeholder)) {
                throw new IllegalArgumentException("<" + parameter + "> " + template + " contains the unknown "
                        + "placeholder " + placeholder + " - supported: " + String.join(", ", PLACEHOLDERS));
            }
        }
        this.template = template.trim();
    }

    /**
     * @param repository the {@code <repository>} of the skill - {@code null} if it has none
     * @throws IllegalArgumentException if the template needs a repository, but the skill has none
     */
    String expand(SkillId id, String repository) {
        String value = template.replace(NAMESPACE, id.namespace()).replace(NAME, id.name());
        if (!value.contains(REPOSITORY) && !value.contains(REPOSITORY_PARENT) && !value.contains(REPOSITORY_LAST)) {
            return value;
        }
        if (repository == null) {
            throw new IllegalArgumentException("the skill " + id + " has no <repository>, but " + template
                    + " needs one");
        }
        List<String> segments = List.of(repository.split("/"));
        return value
                .replace(REPOSITORY, String.join(".", segments))
                .replace(REPOSITORY_PARENT, String.join(".", segments.subList(0, segments.size() - 1)))
                .replace(REPOSITORY_LAST, segments.get(segments.size() - 1))
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^\\.|\\.$", "");
    }
}

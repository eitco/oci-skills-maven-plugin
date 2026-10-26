package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A configured value with the placeholders {@code {namespace}} and {@code {name}} - e.g. the group id of a maven
 * source {@code de.eitco.{namespace}}.
 */
final class SourceTemplate {

    static final String NAMESPACE = "{namespace}";
    static final String NAME = "{name}";

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
            if (!placeholder.equals(NAMESPACE) && !placeholder.equals(NAME)) {
                throw new IllegalArgumentException("<" + parameter + "> " + template + " contains the unknown "
                        + "placeholder " + placeholder + " - supported: " + NAMESPACE + ", " + NAME);
            }
        }
        this.template = template.trim();
    }

    String expand(SkillId id) {
        return template.replace(NAMESPACE, id.namespace()).replace(NAME, id.name());
    }
}

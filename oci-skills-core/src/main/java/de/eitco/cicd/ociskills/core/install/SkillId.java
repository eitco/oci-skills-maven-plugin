package de.eitco.cicd.ociskills.core.install;

import java.util.regex.Pattern;

/**
 * The identity of a skill independent of the repository it is pulled from: {@code <namespace>/<name>} - the
 * namespace and name of its SkillCard.
 */
public record SkillId(String namespace, String name) {

    /**
     * A DNS label as required for the name by the SkillCard validation - the namespace must be one as well, since
     * it is part of repository paths.
     */
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?");

    public SkillId {
        if (namespace == null || !DNS_LABEL.matcher(namespace).matches() || name == null
                || !DNS_LABEL.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid skill id '" + namespace + "/" + name
                    + "' - namespace and name must be DNS labels (lower case alphanumerics and '-')");
        }
    }

    /**
     * @param value {@code <namespace>/<name>}
     * @throws IllegalArgumentException if the value has another form
     */
    public static SkillId parse(String value) {
        String[] parts = value == null ? new String[0] : value.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid skill id '" + value + "' - expected <namespace>/<name>");
        }
        try {
            return new SkillId(parts[0], parts[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid skill id '" + value
                    + "' - namespace and name must be DNS labels (lower case alphanumerics and '-')", e);
        }
    }

    @Override
    public String toString() {
        return namespace + "/" + name;
    }
}

package de.eitco.cicd.ociskills.core.oci;

/**
 * The tag strategy of {@code skillctl}: {@code draft -> <version>-draft}, {@code testing -> <version>-testing},
 * {@code published -> <version>}. No mutable tags like {@code latest} are created.
 */
public class DefaultTagStrategy implements TagStrategy {

    @Override
    public String tag(String version, SkillStatus status) {
        return switch (status) {
            case DRAFT, TESTING -> version + "-" + status.value();
            case PUBLISHED -> version;
        };
    }
}

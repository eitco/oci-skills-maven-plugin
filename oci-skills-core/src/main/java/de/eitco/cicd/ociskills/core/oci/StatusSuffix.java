package de.eitco.cicd.ociskills.core.oci;

/**
 * The status suffix used by tags and maven classifiers: {@code draft -> <base>-draft},
 * {@code testing -> <base>-testing}, {@code published -> <base>}.
 */
public final class StatusSuffix {

    private StatusSuffix() {
    }

    public static String append(String base, SkillStatus status) {
        return switch (status) {
            case DRAFT, TESTING -> base + "-" + status.value();
            case PUBLISHED -> base;
        };
    }

    /**
     * Splits a value created by {@link #append(String, SkillStatus)} into base and status.
     */
    public static Split split(String value) {
        for (SkillStatus status : SkillStatus.values()) {
            String suffix = "-" + status.value();
            if (status != SkillStatus.PUBLISHED && value.endsWith(suffix) && value.length() > suffix.length()) {
                return new Split(value.substring(0, value.length() - suffix.length()), status);
            }
        }
        return new Split(value, SkillStatus.PUBLISHED);
    }

    /**
     * @param base   the value without status suffix
     * @param status the status - {@code published} if there is no suffix
     */
    public record Split(String base, SkillStatus status) {
    }
}

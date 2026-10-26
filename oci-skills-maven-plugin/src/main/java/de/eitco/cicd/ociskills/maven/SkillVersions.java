package de.eitco.cicd.ociskills.maven;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Comparator;

/**
 * Version specs of requested skills - maven version ranges like {@code [1.0,2.0)} or exact versions like
 * {@code 1.2.0}. Unlike maven, a version without brackets matches only itself.
 */
final class SkillVersions {

    /**
     * Orders versions like maven does.
     */
    static final Comparator<String> ORDER = Comparator.comparing(ComparableVersion::new);

    private SkillVersions() {
    }

    /**
     * @return the spec as maven version range - an exact version {@code v} becomes {@code [v]}
     * @throws IllegalArgumentException if the spec is no valid version range
     */
    static String toRange(String versionSpec) {
        String spec = versionSpec == null ? "" : versionSpec.trim();
        if (spec.isEmpty()) {
            throw new IllegalArgumentException("the version must not be empty - use e.g. 1.2.0 or [1.0,2.0)");
        }
        String range = spec.startsWith("[") || spec.startsWith("(") ? spec : "[" + spec + "]";
        parse(range, versionSpec);
        return range;
    }

    /**
     * @throws IllegalArgumentException if the spec is no valid version range
     */
    static boolean matches(String versionSpec, String version) {
        return parse(toRange(versionSpec), versionSpec).containsVersion(new DefaultArtifactVersion(version));
    }

    private static VersionRange parse(String range, String versionSpec) {
        try {
            return VersionRange.createFromVersionSpec(range);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException("invalid version '" + versionSpec + "': " + e.getMessage(), e);
        }
    }
}

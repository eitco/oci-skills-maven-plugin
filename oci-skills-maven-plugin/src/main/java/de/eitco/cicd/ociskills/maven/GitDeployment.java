package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides what {@code oci-skills:git-deploy} publishes: which skills, to which branch and with which tag.
 */
final class GitDeployment {

    /**
     * @param branch the branch to commit to
     * @param tag    the tag to create - {@code null} for none
     */
    record Target(String branch, String tag) {
    }

    private GitDeployment() {
    }

    /**
     * Releases go to {@code branch} and are tagged. Snapshots go to {@code snapshotBranch} without tag - or nowhere,
     * if there is no snapshot branch.
     *
     * @return empty if nothing is to be deployed
     */
    static Optional<Target> target(String version, String branch, String snapshotBranch, String tag) {
        if (version.endsWith("-SNAPSHOT")) {
            return blank(snapshotBranch) ? Optional.empty() : Optional.of(new Target(snapshotBranch.trim(), null));
        }
        return Optional.of(new Target(branch, blank(tag) ? null : tag.trim()));
    }

    /**
     * @return the statuses of the skills to deploy - only {@code published} if none are given
     * @throws IllegalArgumentException if a status is not supported
     */
    static Set<SkillStatus> statuses(List<String> values) {
        if (values == null || values.isEmpty()) {
            return EnumSet.of(SkillStatus.PUBLISHED);
        }
        Set<SkillStatus> statuses = EnumSet.noneOf(SkillStatus.class);
        for (String value : values) {
            statuses.add(SkillStatus.parse(value));
        }
        return statuses;
    }

    /**
     * @return the images having one of the given statuses - images without status are published
     */
    static List<OciImageLayout> select(List<OciImageLayout> images, Set<SkillStatus> statuses) {
        return images.stream().filter(image -> statuses.contains(status(image))).toList();
    }

    /**
     * @return the message followed by the name and digest of every image, so the commit tells which images it contains
     */
    static String message(String message, List<OciImageLayout> images) {
        StringBuilder result = new StringBuilder(message);
        if (!images.isEmpty()) {
            result.append('\n');
        }
        for (OciImageLayout image : images) {
            result.append('\n').append(name(image)).append(' ').append(image.manifestDescriptor().digest());
        }
        return result.toString();
    }

    /**
     * @return the name of the skill - {@code null} if the image has none
     */
    static String name(OciImageLayout image) {
        Map<String, String> annotations = image.manifest().annotations();
        return annotations == null ? null : annotations.get(ImageManifestBuilder.ANNOTATION_TITLE);
    }

    /**
     * @return the version of the skill - {@code null} if the image has none
     */
    static String version(OciImageLayout image) {
        Map<String, String> annotations = image.manifest().annotations();
        return annotations == null ? null : annotations.get(ImageManifestBuilder.ANNOTATION_VERSION);
    }

    private static SkillStatus status(OciImageLayout image) {
        Map<String, String> annotations = image.manifest().annotations();
        String status = annotations == null ? null : annotations.get(SkillStatus.ANNOTATION);
        return status == null ? SkillStatus.PUBLISHED : SkillStatus.parse(status);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

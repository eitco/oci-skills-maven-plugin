package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Reads what a skill image says about itself from the annotations of its manifest.
 */
final class SkillAnnotations {

    private SkillAnnotations() {
    }

    /**
     * @return the annotations of the manifest - empty if it has none
     */
    static Map<String, String> of(SkillImage image) {
        return Optional.ofNullable(image.manifest().annotations()).orElse(Map.of());
    }

    /**
     * @return the id of the skill in the image - vendor and title of the manifest
     * @throws IllegalArgumentException if the image has no valid id
     */
    static SkillId id(SkillImage image) {
        Map<String, String> annotations = of(image);
        return new SkillId(annotations.get(ImageManifestBuilder.ANNOTATION_VENDOR),
                annotations.get(ImageManifestBuilder.ANNOTATION_TITLE));
    }
}

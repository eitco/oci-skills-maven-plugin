package de.eitco.cicd.ociskills.core.oci;

import de.eitco.cicd.ociskills.core.skillcard.SkillCard;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the OCI image manifest of a skill image including the skill annotations.
 */
public class ImageManifestBuilder {

    public static final String ANNOTATION_WORD_COUNT = "io.skillimage.wordcount";
    public static final String ANNOTATION_CREATED = "org.opencontainers.image.created";
    public static final String ANNOTATION_DESCRIPTION = "org.opencontainers.image.description";
    public static final String ANNOTATION_TITLE = "org.opencontainers.image.title";
    public static final String ANNOTATION_VENDOR = "org.opencontainers.image.vendor";
    public static final String ANNOTATION_VERSION = "org.opencontainers.image.version";
    public static final String ANNOTATION_REF_NAME = "org.opencontainers.image.ref.name";

    /**
     * @param config    the descriptor of the image configuration
     * @param layer     the descriptor of the skill layer
     * @param skillCard the SkillCard of the skill
     * @param status    the lifecycle status of the image
     * @param wordCount the number of words in the prompt file - the annotation is omitted if zero, like skillctl does
     * @param created   the creation timestamp - written with second precision in UTC
     */
    public OciImageManifest build(OciDescriptor config, OciDescriptor layer, SkillCard skillCard, SkillStatus status,
                                  int wordCount, Instant created) {
        Map<String, String> annotations = new HashMap<>();
        annotations.put(SkillStatus.ANNOTATION, status.value());
        if (wordCount > 0) {
            annotations.put(ANNOTATION_WORD_COUNT, Integer.toString(wordCount));
        }
        annotations.put(ANNOTATION_CREATED, created.truncatedTo(ChronoUnit.SECONDS).toString());
        String description = skillCard.metadata().description();
        annotations.put(ANNOTATION_DESCRIPTION, description == null ? "" : description);
        annotations.put(ANNOTATION_TITLE, skillCard.metadata().name());
        annotations.put(ANNOTATION_VENDOR, skillCard.metadata().namespace());
        annotations.put(ANNOTATION_VERSION, skillCard.metadata().version());
        return new OciImageManifest(2, MediaTypes.IMAGE_MANIFEST, config, List.of(layer), Map.copyOf(annotations));
    }
}

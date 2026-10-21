package de.eitco.cicd.ociskills.core.skillcard;

/**
 * The content of the {@code skill.yaml} file the plugin generates into the image. It is never read from the skill
 * directory: {@code name} and {@code description} come from the frontmatter of {@code SKILL.md}, everything else
 * from the {@link SkillCardSettings}.
 */
public record SkillCard(String apiVersion, String kind, SkillMetadata metadata, SkillSpec spec) {

    public static final String SUPPORTED_API_VERSION = "skillimage.io/v1alpha1";
    public static final String KIND = "SkillCard";
    public static final String FILE_NAME = "skill.yaml";
    public static final String PROMPT_FILE = "SKILL.md";

    /**
     * @return the SkillCard of a skill with the given frontmatter and settings
     */
    public static SkillCard of(SkillFrontmatter frontmatter, SkillCardSettings settings) {
        return new SkillCard(SUPPORTED_API_VERSION, KIND,
                new SkillMetadata(frontmatter.name(), settings.namespace(), settings.version(),
                        frontmatter.description()),
                new SkillSpec(PROMPT_FILE));
    }
}

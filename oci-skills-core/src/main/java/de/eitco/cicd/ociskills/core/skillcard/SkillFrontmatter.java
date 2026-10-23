package de.eitco.cicd.ociskills.core.skillcard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The YAML frontmatter of {@code SKILL.md} as defined by the agent skills format. Only the fields used by the plugin
 * are modeled; other fields are ignored.
 *
 * @param name        the name of the skill
 * @param description what the skill does and when to use it
 * @param metadata    the {@code metadata} map - empty if missing
 * @param status      the lifecycle status of the image, e.g. {@code draft} - {@code null} if missing, then the image
 *                    is {@code published}
 */
public record SkillFrontmatter(String name, String description, Map<String, String> metadata, String status) {

    public SkillFrontmatter {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public SkillFrontmatter(String name, String description, Map<String, String> metadata) {
        this(name, description, metadata, null);
    }

    public SkillFrontmatter(String name, String description) {
        this(name, description, Map.of());
    }
}

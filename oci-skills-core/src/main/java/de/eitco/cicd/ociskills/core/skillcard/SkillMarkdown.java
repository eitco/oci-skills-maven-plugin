package de.eitco.cicd.ociskills.core.skillcard;

/**
 * A parsed {@code SKILL.md}.
 *
 * @param frontmatter     the parsed frontmatter
 * @param frontmatterYaml the YAML between the opening and the closing {@code ---} line
 * @param body            the bytes after the closing {@code ---} line, unchanged
 */
public record SkillMarkdown(SkillFrontmatter frontmatter, String frontmatterYaml, byte[] body) {
}

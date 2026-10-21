package de.eitco.cicd.ociskills.core.skillcard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes the {@code SKILL.md} that is put into the image: the frontmatter of the original file with the values
 * managed by the plugin added to {@code metadata}, followed by the unchanged rest of the file.
 */
public class SkillMarkdownWriter {

    /**
     * The keys of {@code metadata} that are set by the plugin and must not be set in the source {@code SKILL.md}.
     */
    public static final List<String> MANAGED_METADATA = List.of("namespace", "version");

    private static final String METADATA = "metadata";
    private static final byte[] DELIMITER_LINE = "---\n".getBytes(StandardCharsets.UTF_8);

    /**
     * @param markdown  the original {@code SKILL.md}
     * @param skillCard the generated SkillCard providing the managed values
     * @return the content of the {@code SKILL.md} for the image
     */
    public byte[] write(SkillMarkdown markdown, SkillCard skillCard) {
        ObjectNode frontmatter = (ObjectNode) Yaml.READER.readTree(markdown.frontmatterYaml());
        JsonNode existing = frontmatter.get(METADATA);
        ObjectNode metadata = existing instanceof ObjectNode object ? object : frontmatter.putObject(METADATA);
        metadata.put("namespace", skillCard.metadata().namespace());
        metadata.put("version", skillCard.metadata().version());

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(DELIMITER_LINE);
        content.writeBytes(Yaml.WRITER.writeValueAsBytes(frontmatter));
        content.writeBytes(DELIMITER_LINE);
        content.writeBytes(markdown.body());
        return content.toByteArray();
    }
}

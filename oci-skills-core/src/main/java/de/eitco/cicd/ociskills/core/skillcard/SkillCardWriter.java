package de.eitco.cicd.ociskills.core.skillcard;

/**
 * Serializes a {@link SkillCard} to {@code skill.yaml} in the format written by skillctl. The output is
 * deterministic.
 */
public class SkillCardWriter {

    /**
     * @return the content of {@code skill.yaml}, UTF-8 encoded
     */
    public byte[] write(SkillCard skillCard) {
        return Yaml.WRITER.writeValueAsBytes(skillCard);
    }
}

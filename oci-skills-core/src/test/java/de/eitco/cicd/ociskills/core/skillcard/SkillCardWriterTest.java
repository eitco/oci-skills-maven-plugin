package de.eitco.cicd.ociskills.core.skillcard;

import de.eitco.cicd.ociskills.core.TestSkills;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SkillCardWriterTest {

    private final SkillCardWriter writer = new SkillCardWriter();

    private static SkillCard card(String version, String description) {
        return SkillCard.of(new SkillFrontmatter("nexus-test", description), new SkillCardSettings("cicd", version));
    }

    private static SkillCard read(byte[] content) {
        return YAMLMapper.builder().build().readValue(content, SkillCard.class);
    }

    @Test
    void writesSkillctlFormat() {
        String content = new String(writer.write(card("0.0.1", "Test skill for OCI registry")), UTF_8);

        assertThat(content).isEqualTo(TestSkills.DEFAULT_SKILL_YAML);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Use it for: registries", "# not a comment", "multi\nline\n", "yes", "'quoted'",
            "a very long description that is longer than eighty characters and must not be split into several lines"})
    void writesDescriptionsThatCanBeReadBack(String description) {
        byte[] content = writer.write(card("0.0.1", description));

        assertThat(read(content).metadata().description()).isEqualTo(description);
    }

    @Test
    void writesVersionThatLooksLikeANumberAsString() {
        byte[] content = writer.write(card("1.10", "d"));

        assertThat(new String(content, UTF_8)).contains("version: \"1.10\"");
        assertThat(read(content).metadata().version()).isEqualTo("1.10");
    }

    @Test
    void isDeterministic() {
        assertThat(writer.write(card("1.0.0-SNAPSHOT", "d"))).isEqualTo(writer.write(card("1.0.0-SNAPSHOT", "d")));
    }
}

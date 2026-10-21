package de.eitco.cicd.ociskills.core.skillcard;

import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.TestSkills;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SkillValidatorTest {

    @TempDir
    Path tempDirectory;

    private Path skillDirectory;

    private final SkillValidator validator = new SkillValidator();

    @BeforeEach
    void createSkill() throws Exception {
        skillDirectory = Files.createDirectory(tempDirectory.resolve("nexus-test"));
        TestSkills.createDefaultSkill(skillDirectory);
    }

    /**
     * The input of the validator - frontmatter and settings.
     */
    private record Skill(SkillFrontmatter frontmatter, SkillCardSettings settings) {
    }

    private static Skill skill(String name, String namespace, String version, String description) {
        return new Skill(new SkillFrontmatter(name, description), new SkillCardSettings(namespace, version));
    }

    private static Skill validSkill() {
        return skill("nexus-test", "cicd", "0.0.1", "desc");
    }

    private static Skill withMetadata(Map<String, String> metadata) {
        return new Skill(new SkillFrontmatter("nexus-test", "desc", metadata), new SkillCardSettings("cicd", "0.0.1"));
    }

    private void validate(Skill skill, Path directory) throws SkillValidationException {
        validator.validate(skill.frontmatter(), skill.settings(), directory);
    }

    private SkillValidationException validationErrorFor(Skill skill) {
        return validationErrorFor(skill, skillDirectory);
    }

    private SkillValidationException validationErrorFor(Skill skill, Path directory) {
        return catchThrowableOfType(SkillValidationException.class, () -> validate(skill, directory));
    }

    @Test
    void acceptsValidSkillCard() {
        assertThatCode(() -> validate(validSkill(), skillDirectory)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nexus-test", "a", "a1", "1a", "abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefgh"})
    void acceptsDnsLabelNames(String name) throws Exception {
        Path directory = Files.createDirectories(tempDirectory.resolve("names").resolve(name));
        TestSkills.createDefaultSkill(directory);

        assertThatCode(() -> validate(skill(name, name, "0.0.1", "d"), directory))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nexus-Test", "-test", "test-", "te_st", "te.st", "te st",
            "abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefghi"})
    void rejectsInvalidNamesAndNamespaces(String invalid) throws Exception {
        Path directory = Files.createDirectory(tempDirectory.resolve(invalid));
        TestSkills.createDefaultSkill(directory);

        assertThat(validationErrorFor(skill(invalid, invalid, "0.0.1", "d"), directory).problems())
                .hasSize(2)
                .anySatisfy(problem -> assertThat(problem).startsWith("SKILL.md frontmatter: name"))
                .anySatisfy(problem -> assertThat(problem).startsWith("namespace"));
    }

    @Test
    void rejectsNameThatDiffersFromSkillDirectory() {
        assertThat(validationErrorFor(skill("other-name", "cicd", "0.0.1", "d")).problems()).singleElement()
                .asString()
                .contains("'other-name'")
                .contains("'nexus-test'")
                .contains("directory");
    }

    @Test
    void rejectsMissingValues() {
        assertThat(validationErrorFor(skill(null, " ", "", null)).problems()).containsExactlyInAnyOrder(
                "SKILL.md frontmatter: name is missing",
                "SKILL.md frontmatter: description is missing",
                "namespace is missing",
                "version is missing");
    }

    @Test
    void rejectsBlankDescription() {
        assertThat(validationErrorFor(skill("nexus-test", "cicd", "0.0.1", "  ")).problems())
                .containsExactly("SKILL.md frontmatter: description is missing");
    }

    @Test
    void acceptsDescriptionWith1024Characters() {
        assertThatCode(() -> validate(skill("nexus-test", "cicd", "0.0.1", "x".repeat(1024)), skillDirectory))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDescriptionLongerThan1024Characters() {
        assertThat(validationErrorFor(skill("nexus-test", "cicd", "0.0.1", "x".repeat(1025))).problems())
                .singleElement().asString()
                .startsWith("SKILL.md frontmatter: description")
                .contains("1024");
    }

    @Test
    void acceptsOtherMetadata() {
        assertThatCode(() -> validate(withMetadata(Map.of("author", "eitco")), skillDirectory))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMetadataManagedByThePlugin() {
        assertThat(validationErrorFor(withMetadata(Map.of("version", "1.0", "namespace", "x"))).problems())
                .containsExactlyInAnyOrder(
                        "SKILL.md frontmatter: metadata.namespace is managed by the plugin - remove it and configure "
                                + "<namespace> in the plugin",
                        "SKILL.md frontmatter: metadata.version is managed by the plugin - remove it and configure "
                                + "<version> in the plugin");
    }

    @Test
    void rejectsSkillYamlInSkillDirectory() {
        TestSkills.write(skillDirectory, "skill.yaml", "kind: SkillCard");

        assertThat(validationErrorFor(validSkill()).problems()).singleElement().asString()
                .contains("skill.yaml is generated by the plugin")
                .contains("remove it");
    }

    @Test
    void rejectsSkillMdThatIsADirectory() throws Exception {
        Path directory = Files.createDirectory(tempDirectory.resolve("dir")).resolve("nexus-test");
        Files.createDirectories(directory.resolve("SKILL.md"));

        assertThat(validationErrorFor(validSkill(), directory).problems()).singleElement().asString()
                .contains("SKILL.md")
                .contains("not a regular file");
    }

    @Test
    void rejectsSkillMdSymlinkPointingOutside() throws Exception {
        Path outside = TestSkills.write(tempDirectory, "OUTSIDE.md", "outside");
        Files.delete(skillDirectory.resolve("SKILL.md"));
        Files.createSymbolicLink(skillDirectory.resolve("SKILL.md"), outside);

        assertThat(validationErrorFor(validSkill()).problems()).singleElement().asString()
                .contains("SKILL.md")
                .contains("outside of the skill directory");
    }

    @Test
    void acceptsSkillMdSymlinkPointingInside() throws Exception {
        Files.move(skillDirectory.resolve("SKILL.md"), skillDirectory.resolve("PROMPT.md"));
        Files.createSymbolicLink(skillDirectory.resolve("SKILL.md"), skillDirectory.resolve("PROMPT.md"));

        assertThatCode(() -> validate(validSkill(), skillDirectory)).doesNotThrowAnyException();
    }

    @Test
    void reportsAllProblemsAtOnce() {
        TestSkills.write(skillDirectory, "skill.yaml", "kind: SkillCard");

        assertThat(validationErrorFor(skill("Other", "Cicd", null, null)).problems()).hasSize(5);
    }
}

package de.eitco.cicd.ociskills.core;

import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciImageConfig;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;
import de.eitco.cicd.ociskills.core.oci.OciJson;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class SkillImageBuilderTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-16T14:45:03Z");

    @TempDir
    Path tempDirectory;

    private Path skillDirectory;
    private Path outputDirectory;

    private final SkillImageBuilder builder = new SkillImageBuilder();

    @BeforeEach
    void createSkill() throws Exception {
        skillDirectory = Files.createDirectory(tempDirectory.resolve("nexus-test"));
        outputDirectory = tempDirectory.resolve("target/skill-oci");
        TestSkills.createDefaultSkill(skillDirectory);
        TestSkills.write(skillDirectory, "references/guide.md", "guide");
    }

    private SkillBuildRequest request(SkillStatus status) {
        return request(skillDirectory, outputDirectory, TIMESTAMP, status);
    }

    private SkillBuildRequest request(Path skill, Path output, Instant timestamp, SkillStatus status) {
        return new SkillBuildRequest(skill, output, FileSelection.defaults(), status, timestamp,
                TestSkills.DEFAULT_SETTINGS, generatedDirectory());
    }

    private Path generatedDirectory() {
        return tempDirectory.resolve("target/generated-skills/nexus-test");
    }

    private Path skillCardFile() {
        return generatedDirectory().resolve("skill.yaml");
    }

    private static <T> T readJson(Path file, Class<T> type) throws Exception {
        return OciJson.deserialize(Files.readAllBytes(file), type);
    }

    @Test
    void buildsConsistentOciImageLayout() throws Exception {
        SkillBuildResult result = builder.build(request(SkillStatus.DRAFT));

        OciImageLayout layout = new OciLayoutReader().read(outputDirectory);
        assertThat(layout.tag()).isEqualTo("0.0.1-draft").isEqualTo(result.tag());
        assertThat(layout.manifestDescriptor().digest()).isEqualTo(result.manifest().digest());
        assertThat(layout.manifest().config()).isEqualTo(result.config());
        assertThat(layout.manifest().layers()).containsExactly(result.layer());
        try (var blobs = Files.list(outputDirectory.resolve("blobs/sha256"))) {
            assertThat(blobs.count()).isEqualTo(3);
        }
    }

    @Test
    void writesConfigWithDiffId() throws Exception {
        SkillBuildResult result = builder.build(request(SkillStatus.DRAFT));

        OciImageConfig config = readJson(outputDirectory.resolve("blobs/sha256").resolve(result.config().digest().hex()),
                OciImageConfig.class);
        assertThat(config.rootfs().diffIds()).containsExactly(result.diffId());
        assertThat(result.config().mediaType()).isEqualTo(MediaTypes.IMAGE_CONFIG);
    }

    @Test
    void writesManifestAnnotations() throws Exception {
        SkillBuildResult result = builder.build(request(SkillStatus.TESTING));

        OciImageManifest manifest = readJson(
                outputDirectory.resolve("blobs/sha256").resolve(result.manifest().digest().hex()),
                OciImageManifest.class);
        assertThat(manifest.annotations()).containsOnly(
                entry("io.skillimage.status", "testing"),
                entry("io.skillimage.wordcount", "11"),
                entry("org.opencontainers.image.created", "2026-10-16T14:45:03Z"),
                entry("org.opencontainers.image.description", "Test skill for OCI registry"),
                entry("org.opencontainers.image.title", "nexus-test"),
                entry("org.opencontainers.image.vendor", "cicd"),
                entry("org.opencontainers.image.version", "0.0.1"));
        assertThat(result.tag()).isEqualTo("0.0.1-testing");
    }

    @Test
    void generatesSkillCardFromFrontmatterAndSettings() throws Exception {
        SkillBuildResult result = builder.build(new SkillBuildRequest(skillDirectory, outputDirectory,
                FileSelection.defaults(), SkillStatus.DRAFT, TIMESTAMP,
                new SkillCardSettings("eitco", "2.3.0-SNAPSHOT"), generatedDirectory()));

        String expected = TestSkills.DEFAULT_SKILL_YAML.replace("namespace: cicd", "namespace: eitco")
                .replace("version: 0.0.1", "version: 2.3.0-SNAPSHOT");
        assertThat(skillCardFile()).hasContent(expected);
        OciImageLayout layout = new OciLayoutReader().read(outputDirectory);
        assertThat(files(layout).get("skill.yaml")).isEqualTo(expected);
        assertThat(layout.manifest().annotations())
                .contains(entry("org.opencontainers.image.version", "2.3.0-SNAPSHOT"),
                        entry("org.opencontainers.image.vendor", "eitco"));
        assertThat(result.tag()).isEqualTo("2.3.0-SNAPSHOT-draft");
        assertThat(skillDirectory.resolve("skill.yaml")).doesNotExist();
    }

    @Test
    void addsManagedValuesToFrontmatterOfSkillMd() throws Exception {
        builder.build(new SkillBuildRequest(skillDirectory, outputDirectory, FileSelection.defaults(),
                SkillStatus.DRAFT, TIMESTAMP, new SkillCardSettings("eitco", "2.3.0-SNAPSHOT"), generatedDirectory()));

        String expected = TestSkills.DEFAULT_SKILL_MD.replace("description: Test skill for OCI registry\n",
                "description: Test skill for OCI registry\nnamespace: eitco\nversion: 2.3.0-SNAPSHOT\n");
        assertThat(generatedDirectory().resolve("SKILL.md")).hasContent(expected);
        assertThat(files(new OciLayoutReader().read(outputDirectory)).get("SKILL.md")).isEqualTo(expected);
        assertThat(skillDirectory.resolve("SKILL.md")).hasContent(TestSkills.DEFAULT_SKILL_MD);
    }

    @Test
    void putsSkillFilesIntoLayer() throws Exception {
        SkillBuildResult result = builder.build(request(SkillStatus.DRAFT));

        List<TarEntries.Entry> entries =
                TarEntries.read(outputDirectory.resolve("blobs/sha256").resolve(result.layer().digest().hex()));
        assertThat(entries).extracting(TarEntries.Entry::name)
                .containsExactly("SKILL.md", "references/", "references/guide.md", "skill.yaml");
        assertThat(entries).allSatisfy(entry -> assertThat(entry.modTimeMillis()).isEqualTo(TIMESTAMP.toEpochMilli()));
    }

    @Test
    void doesNotPutOutputDirectoryIntoLayerIfInsideSkillDirectory() throws Exception {
        Path nestedOutput = skillDirectory.resolve("build/skill-oci");
        builder.build(request(skillDirectory, nestedOutput, TIMESTAMP, SkillStatus.DRAFT));

        SkillBuildResult result = builder.build(request(skillDirectory, nestedOutput, TIMESTAMP, SkillStatus.DRAFT));

        assertThat(TarEntries.read(nestedOutput.resolve("blobs/sha256").resolve(result.layer().digest().hex())))
                .extracting(TarEntries.Entry::name)
                .noneMatch(name -> name.startsWith("build"));
    }

    @Test
    void isReproducible() throws Exception {
        SkillBuildResult first = builder.build(request(SkillStatus.DRAFT));
        Files.setLastModifiedTime(skillDirectory.resolve("SKILL.md"), FileTime.from(Instant.now()));
        SkillBuildResult second = builder.build(request(SkillStatus.DRAFT));

        assertThat(second.diffId()).isEqualTo(first.diffId());
        assertThat(second.layer().digest()).isEqualTo(first.layer().digest());
        assertThat(second.config().digest()).isEqualTo(first.config().digest());
        assertThat(second.manifest().digest()).isEqualTo(first.manifest().digest());
    }

    @Test
    void isPublishedByDefault() throws Exception {
        SkillBuildResult result = builder.build(request(null));

        assertThat(result.status()).isEqualTo(SkillStatus.PUBLISHED);
        assertThat(result.tag()).isEqualTo("0.0.1");
        assertThat(new OciLayoutReader().read(outputDirectory).manifest().annotations())
                .containsEntry("io.skillimage.status", "published");
    }

    @Test
    void takesTheStatusFromTheFrontmatter() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill for OCI registry
                status: Draft
                ---
                # Nexus Test
                """);

        SkillBuildResult result = builder.build(request(null));

        assertThat(result.status()).isEqualTo(SkillStatus.DRAFT);
        assertThat(result.tag()).isEqualTo("0.0.1-draft");
        assertThat(new OciLayoutReader().read(outputDirectory).manifest().annotations())
                .containsEntry("io.skillimage.status", "draft");
        assertThat(Files.readString(generatedDirectory().resolve("SKILL.md"))).contains("status: Draft");
    }

    @Test
    void anExplicitStatusWinsOverTheFrontmatter() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill for OCI registry
                status: draft
                ---
                # Nexus Test
                """);

        SkillBuildResult result = builder.build(request(SkillStatus.TESTING));

        assertThat(result.status()).isEqualTo(SkillStatus.TESTING);
        assertThat(result.tag()).isEqualTo("0.0.1-testing");
    }

    @Test
    void rejectsAnUnsupportedStatus() throws Exception {
        TestSkills.write(skillDirectory, "SKILL.md", """
                ---
                name: nexus-test
                description: Test skill for OCI registry
                status: released
                ---
                # Nexus Test
                """);

        assertThatThrownBy(() -> builder.build(request(null)))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("status 'released' is not supported");
        assertThat(outputDirectory).doesNotExist();
    }

    @Test
    void sameLayerForDifferentStatus() throws Exception {
        SkillBuildResult draft = builder.build(request(SkillStatus.DRAFT));
        SkillBuildResult published = builder.build(request(SkillStatus.PUBLISHED));

        assertThat(published.layer().digest()).isEqualTo(draft.layer().digest());
        assertThat(published.manifest().digest()).isNotEqualTo(draft.manifest().digest());
        assertThat(published.tag()).isEqualTo("0.0.1");
    }

    @Test
    void validatesBeforeWritingAnything() throws Exception {
        TestSkills.write(skillDirectory, "skill.yaml", TestSkills.DEFAULT_SKILL_YAML);

        assertThatThrownBy(() -> builder.build(request(SkillStatus.DRAFT)))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("skill.yaml is generated by the plugin");
        assertThat(outputDirectory).doesNotExist();
        assertThat(skillCardFile()).doesNotExist();
    }

    @Test
    void validatesSymlinksBeforeWritingAnything() throws Exception {
        builder.build(request(SkillStatus.DRAFT));
        Path outside = TestSkills.write(tempDirectory, "outside.md", "outside");
        Files.createSymbolicLink(skillDirectory.resolve("link.md"), outside);

        assertThatThrownBy(() -> builder.build(request(SkillStatus.DRAFT)))
                .isInstanceOf(SkillValidationException.class);
        assertThat(new OciLayoutReader().read(outputDirectory).tag()).isEqualTo("0.0.1-draft");
    }

    @Test
    void failsIfSkillMdHasNoFrontmatter() throws Exception {
        Files.writeString(skillDirectory.resolve("SKILL.md"), "# Nexus Test\n");

        assertThatThrownBy(() -> builder.build(request(SkillStatus.DRAFT)))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("frontmatter");
        assertThat(outputDirectory).doesNotExist();
    }

    @Test
    void failsIfSkillDirectoryIsMissing() {
        SkillBuildRequest request = request(tempDirectory.resolve("missing"), outputDirectory, TIMESTAMP,
                SkillStatus.DRAFT);

        assertThatThrownBy(() -> builder.build(request))
                .isInstanceOf(SkillValidationException.class)
                .hasMessageContaining("skill directory")
                .hasMessageContaining("missing");
    }

    @Test
    void isFormatCompatibleWithSkillctl() throws Exception {
        SkillBuildResult result = builder.build(request(GoldenMaster.SOURCE, outputDirectory, GoldenMaster.CREATED,
                SkillStatus.DRAFT));

        OciImageLayout ours = new OciLayoutReader().read(outputDirectory);
        OciImageLayout skillctl = new OciLayoutReader().read(GoldenMaster.LAYOUT);

        // same media types and structure
        assertThat(ours.manifestDescriptor().mediaType()).isEqualTo(skillctl.manifestDescriptor().mediaType());
        assertThat(ours.manifest().mediaType()).isEqualTo(skillctl.manifest().mediaType());
        assertThat(ours.manifest().schemaVersion()).isEqualTo(skillctl.manifest().schemaVersion());
        assertThat(ours.manifest().config().mediaType()).isEqualTo(skillctl.manifest().config().mediaType());
        assertThat(ours.manifest().layers()).extracting(descriptor -> descriptor.mediaType())
                .containsExactlyElementsOf(skillctl.manifest().layers().stream().map(d -> d.mediaType()).toList());

        // same annotations - skillctl also uses "created" as given here
        assertThat(ours.manifest().annotations()).isEqualTo(skillctl.manifest().annotations());

        // same config structure
        OciImageConfig ourConfig = readJson(ours.blob(ours.manifest().config()), OciImageConfig.class);
        OciImageConfig skillctlConfig = readJson(skillctl.blob(skillctl.manifest().config()), OciImageConfig.class);
        assertThat(ourConfig.architecture()).isEqualTo(skillctlConfig.architecture());
        assertThat(ourConfig.os()).isEqualTo(skillctlConfig.os());
        assertThat(ourConfig.config()).isEqualTo(skillctlConfig.config());
        assertThat(ourConfig.rootfs().type()).isEqualTo(skillctlConfig.rootfs().type());
        assertThat(ourConfig.rootfs().diffIds()).containsExactly(result.diffId());

        // same file contents, including the generated skill.yaml - skillctl writes directories without trailing slash
        Map<String, String> ourFiles = files(ours);
        Map<String, String> skillctlFiles = files(skillctl);
        assertThat(ourFiles).containsOnlyKeys(skillctlFiles.keySet());
        ourFiles.keySet().stream().filter(name -> !name.equals("SKILL.md"))
                .forEach(name -> assertThat(ourFiles.get(name)).as(name).isEqualTo(skillctlFiles.get(name)));
        // intentional difference: the plugin adds the managed values to the frontmatter of SKILL.md
        assertThat(ourFiles.get("SKILL.md")).isEqualTo(skillctlFiles.get("SKILL.md").replace(
                "description: Test skill for OCI registry\n",
                "description: Test skill for OCI registry\nnamespace: cicd\nversion: 0.0.1\n"));
    }

    private static Map<String, String> files(OciImageLayout layout) throws Exception {
        return TarEntries.read(layout.blob(layout.manifest().layers().get(0))).stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.name().endsWith("/") ? entry.name().substring(0, entry.name().length() - 1)
                                : entry.name(),
                        entry -> entry.directory() ? "<dir>" : entry.content(),
                        (a, b) -> a, java.util.TreeMap::new));
    }

    @Test
    void preservesExecutableModeOfGoldenMasterScript() throws Exception {
        SkillBuildResult result = builder.build(request(GoldenMaster.SOURCE, outputDirectory, GoldenMaster.CREATED,
                SkillStatus.DRAFT));

        assertThat(TarEntries.read(outputDirectory.resolve("blobs/sha256").resolve(result.layer().digest().hex())))
                .filteredOn(entry -> entry.name().equals("scripts/hello.sh"))
                .singleElement()
                .extracting(TarEntries.Entry::mode)
                .isEqualTo(0755);
    }
}

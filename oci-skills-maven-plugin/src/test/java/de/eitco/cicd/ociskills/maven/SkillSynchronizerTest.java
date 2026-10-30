package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.LockedSkill;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.install.SkillInstaller;
import de.eitco.cicd.ociskills.core.install.SkillLockFile;
import de.eitco.cicd.ociskills.core.install.SkillOrigin;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static de.eitco.cicd.ociskills.core.oci.SkillStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSynchronizerTest {

    private static final SkillId NEXUS_TEST = new SkillId("cicd", "nexus-test");
    private static final SkillRequest NEXUS_TEST_1 = new SkillRequest(NEXUS_TEST, "[1.0,2.0)", PUBLISHED, null);
    private static final Predicate<SkillId> NONE = id -> false;

    @TempDir
    Path tempDirectory;

    private FakeSkillSource source;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        source = new FakeSkillSource("maven", tempDirectory.resolve("images"));
    }

    private Path skills() {
        return tempDirectory.resolve(".claude/skills");
    }

    private SkillLockFile lockFile() {
        return new SkillLockFile(tempDirectory.resolve(SkillLockFile.DEFAULT_FILE_NAME));
    }

    private SkillInstaller installer() {
        return new SkillInstaller(skills());
    }

    private void sync(Predicate<SkillId> update, SkillRequest... requests) throws SkillPullException {
        new SkillSynchronizer(new SkillResolver(List.of(source)), installer(), lockFile(),
                tempDirectory.resolve("work"), log::add).sync(List.of(requests), update);
    }

    private String installedVersion() throws SkillPullException {
        return installer().installed().get("nexus-test").version();
    }

    @Test
    void resolvesInstallsAndLocksRequestedSkills() throws Exception {
        OciImageLayout image = source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);

        sync(NONE, NEXUS_TEST_1);

        SkillOrigin origin = new SkillOrigin(NEXUS_TEST, "1.1.0", PUBLISHED, image.manifestDescriptor().digest(),
                "maven");
        assertThat(installer().installed()).containsEntry("nexus-test", origin);
        assertThat(lockFile().read()).containsExactly(new LockedSkill("[1.0,2.0)", PUBLISHED, origin));
        assertThat(skills().resolve("nexus-test/.gitignore")).content().contains("*");
        assertThat(log).anySatisfy(line -> assertThat(line).contains("cicd/nexus-test", "1.1.0", "maven"));
    }

    @Test
    void doesNothingIfInstalledSkillsMatchTheLock() throws Exception {
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);
        sync(NONE, NEXUS_TEST_1);
        source.fetches().clear();
        source.add("cicd", "nexus-test", "1.2.0", PUBLISHED);

        sync(NONE, NEXUS_TEST_1);

        assertThat(source.fetches()).isEmpty();
        assertThat(installedVersion()).isEqualTo("1.1.0");
        assertThat(log).last().asString().contains("up to date");
    }

    @Test
    void installsTheLockedVersionIfTheSkillIsMissing() throws Exception {
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);
        sync(NONE, NEXUS_TEST_1);
        installer().remove("nexus-test");
        source.add("cicd", "nexus-test", "1.2.0", PUBLISHED);

        sync(NONE, NEXUS_TEST_1);

        assertThat(installedVersion()).isEqualTo("1.1.0");
    }

    @Test
    void resolvesAgainIfTheRequestChanged() throws Exception {
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);
        sync(NONE, NEXUS_TEST_1);
        source.add("cicd", "nexus-test", "2.0.0", PUBLISHED);

        sync(NONE, new SkillRequest(NEXUS_TEST, "[2.0,3.0)", PUBLISHED, null));

        assertThat(installedVersion()).isEqualTo("2.0.0");
        assertThat(lockFile().read()).singleElement().satisfies(locked -> {
            assertThat(locked.requestedVersion()).isEqualTo("[2.0,3.0)");
            assertThat(locked.resolved().version()).isEqualTo("2.0.0");
        });
    }

    @Test
    void locksTheRequestedRepository() throws Exception {
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);

        sync(NONE, new SkillRequest(NEXUS_TEST, "[1.0,2.0)", PUBLISHED, null, "teams/acme/skills"));

        assertThat(source.repositories()).isNotEmpty().containsOnly("teams/acme/skills");
        assertThat(lockFile().read()).singleElement()
                .satisfies(locked -> assertThat(locked.requestedRepository()).isEqualTo("teams/acme/skills"));
    }

    @Test
    void resolvesAgainIfTheRepositoryChanged() throws Exception {
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);
        sync(NONE, NEXUS_TEST_1);
        source.add("cicd", "nexus-test", "1.2.0", PUBLISHED);

        sync(NONE, new SkillRequest(NEXUS_TEST, "[1.0,2.0)", PUBLISHED, null, "teams/acme/skills"));

        assertThat(installedVersion()).isEqualTo("1.2.0");
        assertThat(lockFile().read()).singleElement()
                .satisfies(locked -> assertThat(locked.requestedRepository()).isEqualTo("teams/acme/skills"));
    }

    @Test
    void installsTheLockedVersionFromTheRequestedRepository() throws Exception {
        SkillRequest request = new SkillRequest(NEXUS_TEST, "[1.0,2.0)", PUBLISHED, null, "teams/acme/skills");
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);
        sync(NONE, request);
        installer().remove("nexus-test");
        source.repositories().clear();

        sync(NONE, request);

        assertThat(installedVersion()).isEqualTo("1.1.0");
        assertThat(source.repositories()).containsExactly("teams/acme/skills");
    }

    @Test
    void resolvesAgainIfSelectedForUpdate() throws Exception {
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);
        sync(NONE, NEXUS_TEST_1);
        source.add("cicd", "nexus-test", "1.2.0", PUBLISHED);

        sync(NEXUS_TEST::equals, NEXUS_TEST_1);

        assertThat(installedVersion()).isEqualTo("1.2.0");
        assertThat(lockFile().read()).singleElement()
                .satisfies(locked -> assertThat(locked.resolved().version()).isEqualTo("1.2.0"));
    }

    @Test
    void removesSkillsNoLongerRequested() throws Exception {
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);
        source.add("cicd", "alpha", "1.0.0", PUBLISHED);
        Files.createDirectories(skills().resolve("by-hand"));
        sync(NONE, NEXUS_TEST_1, new SkillRequest(new SkillId("cicd", "alpha"), "1.0.0", PUBLISHED, null));

        sync(NONE, NEXUS_TEST_1);

        assertThat(installer().installed()).containsOnlyKeys("nexus-test");
        assertThat(skills().resolve("alpha")).doesNotExist();
        assertThat(skills().resolve("by-hand")).isDirectory();
        assertThat(lockFile().read()).extracting(LockedSkill::id).containsExactly(NEXUS_TEST);
    }

    @Test
    void keepsSkillsInstalledWithoutLock() throws Exception {
        OciImageLayout pulled = source.add("cicd", "pulled", "1.0.0", PUBLISHED);
        installer().install(pulled, new SkillOrigin(new SkillId("cicd", "pulled"), "1.0.0", PUBLISHED,
                pulled.manifestDescriptor().digest(), "pull"));
        source.add("cicd", "nexus-test", "1.1.0", PUBLISHED);

        sync(NONE, NEXUS_TEST_1);

        assertThat(installer().installed()).containsOnlyKeys("nexus-test", "pulled");
        assertThat(skills().resolve("pulled/.gitignore")).exists();
        assertThat(skills().resolve("nexus-test/.gitignore")).exists();
    }

    @Test
    void rejectsSkillsWithTheSameName() {
        assertThatThrownBy(() -> sync(NONE, NEXUS_TEST_1,
                new SkillRequest(new SkillId("other", "nexus-test"), "1.0.0", PUBLISHED, null)))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("cicd/nexus-test")
                .hasMessageContaining("other/nexus-test");
    }
}

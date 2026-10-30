package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitDeploymentTest {

    @TempDir
    Path directory;

    @Test
    void releasesAreCommittedToTheBranchAndTagged() {
        assertThat(GitDeployment.target("1.2.0", "main", null, "acme-1.2.0"))
                .contains(new GitDeployment.Target("main", "acme-1.2.0"));
    }

    @Test
    void snapshotsAreSkippedWithoutSnapshotBranch() {
        assertThat(GitDeployment.target("1.2.0-SNAPSHOT", "main", null, "acme-1.2.0-SNAPSHOT")).isEmpty();
        assertThat(GitDeployment.target("1.2.0-SNAPSHOT", "main", " ", "acme-1.2.0-SNAPSHOT")).isEmpty();
    }

    @Test
    void snapshotsAreCommittedToTheSnapshotBranchWithoutTag() {
        assertThat(GitDeployment.target("1.2.0-SNAPSHOT", "main", "snapshots", "acme-1.2.0-SNAPSHOT"))
                .contains(new GitDeployment.Target("snapshots", null));
    }

    @Test
    void anEmptyTagCreatesNoTag() {
        assertThat(GitDeployment.target("1.2.0", "main", null, " ")).contains(new GitDeployment.Target("main", null));
    }

    @Test
    void onlyPublishedSkillsAreDeployedByDefault() {
        assertThat(GitDeployment.statuses(null)).containsExactly(SkillStatus.PUBLISHED);
        assertThat(GitDeployment.statuses(List.of())).containsExactly(SkillStatus.PUBLISHED);
    }

    @Test
    void parsesStatuses() {
        assertThat(GitDeployment.statuses(List.of("published", " Testing ")))
                .containsExactlyInAnyOrder(SkillStatus.PUBLISHED, SkillStatus.TESTING);
        assertThatThrownBy(() -> GitDeployment.statuses(List.of("released")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("released");
    }

    @Test
    void selectsSkillsByStatus() throws Exception {
        OciImageLayout published = image("a", SkillStatus.PUBLISHED);
        OciImageLayout testing = image("b", SkillStatus.TESTING);
        OciImageLayout draft = image("c", SkillStatus.DRAFT);

        assertThat(GitDeployment.select(List.of(published, testing, draft),
                Set.of(SkillStatus.PUBLISHED, SkillStatus.TESTING))).containsExactly(published, testing);
    }

    @Test
    void namesTheImagesInTheMessage() throws Exception {
        OciImageLayout a = image("a", SkillStatus.PUBLISHED);
        OciImageLayout b = image("b", SkillStatus.PUBLISHED);

        assertThat(GitDeployment.message("release acme 1.0.0", List.of(a, b)).split("\n", -1))
                .isEqualTo(new String[]{"release acme 1.0.0", "",
                        "a " + a.manifestDescriptor().digest(),
                        "b " + b.manifestDescriptor().digest()});
        assertThat(Arrays.asList(GitDeployment.message("release", List.of()).split("\n"))).containsExactly("release");
    }

    private OciImageLayout image(String name, SkillStatus status) throws Exception {
        return TestImages.build(directory, "acme", name, "1.0.0", status, "# " + name);
    }
}

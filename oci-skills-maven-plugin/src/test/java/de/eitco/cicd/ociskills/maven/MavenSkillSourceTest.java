package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import org.eclipse.aether.artifact.Artifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenSkillSourceTest {

    private static final SkillId TOOLS_DELTA = new SkillId("cicd", "tools-delta");

    @TempDir
    Path directory;

    @Test
    void usesTheNameSuffixedWithTheStatusAsDefaultClassifier() throws Exception {
        MavenSkillSource source = source("de.eitco.{namespace}", "skills", "{name}");

        assertThat(source.artifact(TOOLS_DELTA, null, "1.0.0", SkillStatus.PUBLISHED))
                .hasToString("de.eitco.cicd:skills:jar:tools-delta:1.0.0");
        assertThat(source.artifact(TOOLS_DELTA, null, "1.0.0", SkillStatus.DRAFT))
                .hasToString("de.eitco.cicd:skills:jar:tools-delta-draft:1.0.0");
    }

    @Test
    void derivesArtifactIdAndClassifierFromTheNameParts() throws Exception {
        MavenSkillSource source = source("de.eitco.{namespace}", "{name[0]}", "{name[1:]}");

        assertThat(source.artifact(TOOLS_DELTA, null, "1.0.0", SkillStatus.PUBLISHED))
                .hasToString("de.eitco.cicd:tools:jar:delta:1.0.0");
        assertThat(source.artifact(TOOLS_DELTA, null, "1.0.0", SkillStatus.TESTING))
                .hasToString("de.eitco.cicd:tools:jar:delta-testing:1.0.0");
    }

    @Test
    void usesTheMainArtifactForAnEmptyClassifier() throws Exception {
        MavenSkillSource source = source("de.eitco.{namespace}", "{name[1:]}", "{name[:0]}");

        Artifact artifact = source.artifact(TOOLS_DELTA, null, "1.0.0", SkillStatus.DRAFT);

        assertThat(artifact).hasToString("de.eitco.cicd:delta:jar:1.0.0");
        assertThat(artifact.getClassifier()).isEmpty();
    }

    @Test
    void rejectsAnEmptyArtifactId() {
        MavenSkillSource source = source("de.eitco.{namespace}", "{name[2:]}", "{name}");

        assertThatThrownBy(() -> source.artifact(TOOLS_DELTA, null, "1.0.0", SkillStatus.PUBLISHED))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("my-maven")
                .hasMessageContaining("cicd/tools-delta")
                .hasMessageContaining("{name[2:]}");
    }

    @Test
    void wrapsIndicesOutOfRange() {
        MavenSkillSource source = source("de.eitco.{namespace}", "{name[2]}", "{name}");

        assertThatThrownBy(() -> source.artifact(TOOLS_DELTA, null, "1.0.0", SkillStatus.PUBLISHED))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("my-maven")
                .hasMessageContaining("{name[2]}");
    }

    @Test
    void comparesTheStatusOfTheImage() throws Exception {
        OciImageLayout draft = TestImages.build(directory, "cicd", "tools-delta", "1.0.0", SkillStatus.DRAFT, "");

        assertThat(MavenSkillSource.hasStatus(draft, SkillStatus.DRAFT)).isTrue();
        assertThat(MavenSkillSource.hasStatus(draft, SkillStatus.PUBLISHED)).isFalse();
    }

    private static MavenSkillSource source(String groupId, String artifactId, String classifier) {
        return new MavenSkillSource("my-maven", new SourceTemplate("groupId", groupId),
                new SourceTemplate("artifactId", artifactId), new SourceTemplate("classifier", classifier),
                null, null, List.of());
    }
}

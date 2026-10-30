package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.install.SkillOrigin;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static de.eitco.cicd.ociskills.core.oci.SkillStatus.DRAFT;
import static de.eitco.cicd.ociskills.core.oci.SkillStatus.PUBLISHED;
import static de.eitco.cicd.ociskills.core.oci.SkillStatus.TESTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillResolverTest {

    private static final SkillId NEXUS_TEST = new SkillId("cicd", "nexus-test");
    private static final SkillRequest LOCKED_REQUEST = new SkillRequest(NEXUS_TEST, "1.0.0", PUBLISHED, null);

    @TempDir
    Path tempDirectory;

    private FakeSkillSource maven;
    private FakeSkillSource oci;

    @BeforeEach
    void setUp() {
        maven = new FakeSkillSource("maven", tempDirectory.resolve("images"));
        oci = new FakeSkillSource("oci", tempDirectory.resolve("images"));
    }

    private SkillResolver resolver() {
        return new SkillResolver(List.of(maven, oci));
    }

    private static SkillRequest request(String versionSpec, SkillStatus minimumStatus, String source) {
        return new SkillRequest(NEXUS_TEST, versionSpec, minimumStatus, source);
    }

    private Path work() {
        return tempDirectory.resolve("work");
    }

    @Test
    void acceptsStatusesDownToTheMinimum() {
        assertThat(SkillResolver.accepted(PUBLISHED)).containsExactly(PUBLISHED);
        assertThat(SkillResolver.accepted(TESTING)).containsExactly(PUBLISHED, TESTING);
        assertThat(SkillResolver.accepted(DRAFT)).containsExactly(PUBLISHED, TESTING, DRAFT);
    }

    @Test
    void resolvesTheHighestMatchingVersion() throws Exception {
        maven.add("cicd", "nexus-test", "1.2.0", PUBLISHED);
        OciImageLayout expected = maven.add("cicd", "nexus-test", "1.10.0", PUBLISHED);
        maven.add("cicd", "nexus-test", "2.0.0", PUBLISHED);

        SkillResolver.Resolved resolved = resolver().resolve(request("[1.0,2.0)", PUBLISHED, null), work());

        assertThat(resolved.origin()).isEqualTo(new SkillOrigin(NEXUS_TEST, "1.10.0", PUBLISHED,
                expected.manifestDescriptor().digest(), "maven"));
        assertThat(resolved.image().manifestDescriptor()).isEqualTo(expected.manifestDescriptor());
    }

    @Test
    void skipsVersionsWithoutAcceptedStatus() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        maven.add("cicd", "nexus-test", "1.1.0", DRAFT);

        SkillResolver.Resolved resolved = resolver().resolve(request("[1.0,2.0)", PUBLISHED, null), work());

        assertThat(resolved.origin().version()).isEqualTo("1.0.0");
        assertThat(maven.fetches()).containsExactly("cicd/nexus-test:1.1.0:published",
                "cicd/nexus-test:1.0.0:published");
    }

    @Test
    void prefersTheMatureStatusOfAVersion() throws Exception {
        maven.add("cicd", "nexus-test", "1.1.0", DRAFT);
        maven.add("cicd", "nexus-test", "1.1.0", PUBLISHED);

        SkillResolver.Resolved resolved = resolver().resolve(request("[1.0,2.0)", DRAFT, null), work());

        assertThat(resolved.origin().status()).isEqualTo(PUBLISHED);
    }

    @Test
    void takesALessMatureStatusIfAccepted() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        maven.add("cicd", "nexus-test", "1.1.0", TESTING);

        SkillResolver.Resolved resolved = resolver().resolve(request("[1.0,2.0)", TESTING, null), work());

        assertThat(resolved.origin().version()).isEqualTo("1.1.0");
        assertThat(resolved.origin().status()).isEqualTo(TESTING);
    }

    @Test
    void theFirstSourceKnowingTheSkillWins() throws Exception {
        oci.add("cicd", "nexus-test", "1.5.0", PUBLISHED);
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);

        SkillResolver.Resolved resolved = resolver().resolve(request("[1.0,2.0)", PUBLISHED, null), work());

        assertThat(resolved.origin().source()).isEqualTo("maven");
        assertThat(resolved.origin().version()).isEqualTo("1.0.0");
    }

    @Test
    void fallsBackToTheNextSource() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", DRAFT);
        oci.add("cicd", "nexus-test", "1.0.0", PUBLISHED);

        SkillResolver.Resolved resolved = resolver().resolve(request("1.0.0", PUBLISHED, null), work());

        assertThat(resolved.origin().source()).isEqualTo("oci");
    }

    @Test
    void asksOnlyThePinnedSource() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        oci.add("cicd", "nexus-test", "1.0.0", PUBLISHED);

        SkillResolver.Resolved resolved = resolver().resolve(request("1.0.0", PUBLISHED, "oci"), work());

        assertThat(resolved.origin().source()).isEqualTo("oci");
        assertThat(maven.fetches()).isEmpty();
    }

    @Test
    void rejectsAnUnknownPinnedSource() {
        assertThatThrownBy(() -> resolver().resolve(request("1.0.0", PUBLISHED, "missing"), work()))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("maven, oci");
    }

    @Test
    void failsIfNoSourceHasAMatchingVersion() throws Exception {
        maven.add("cicd", "nexus-test", "2.0.0", PUBLISHED);
        oci.add("cicd", "nexus-test", "1.0.0", DRAFT);

        assertThatThrownBy(() -> resolver().resolve(request("[1.0,2.0)", PUBLISHED, null), work()))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("cicd/nexus-test")
                .hasMessageContaining("[1.0,2.0)")
                .hasMessageContaining("published")
                .hasMessageContaining("maven, oci");
    }

    @Test
    void fetchesALockedSkillFromItsSourceFirst() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        OciImageLayout image = oci.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        SkillOrigin locked = new SkillOrigin(NEXUS_TEST, "1.0.0", PUBLISHED, image.manifestDescriptor().digest(),
                "oci");

        SkillResolver.Fetched fetched = resolver().fetch(locked, LOCKED_REQUEST, work());

        assertThat(fetched.source()).isEqualTo("oci");
        assertThat(fetched.image().manifestDescriptor()).isEqualTo(image.manifestDescriptor());
        assertThat(maven.fetches()).isEmpty();
    }

    @Test
    void fetchesALockedSkillFromAnotherSource() throws Exception {
        OciImageLayout image = oci.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        SkillOrigin locked = new SkillOrigin(NEXUS_TEST, "1.0.0", PUBLISHED, image.manifestDescriptor().digest(),
                "gone");

        assertThat(resolver().fetch(locked, LOCKED_REQUEST, work()).source()).isEqualTo("oci");
    }

    @Test
    void rejectsALockedSkillWithAnotherDigest() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED, "rebuilt");
        OciImageLayout image = oci.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        SkillOrigin locked = new SkillOrigin(NEXUS_TEST, "1.0.0", PUBLISHED, image.manifestDescriptor().digest(),
                "maven");

        assertThatThrownBy(() -> resolver().fetch(locked, LOCKED_REQUEST, work()))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("cicd/nexus-test")
                .hasMessageContaining("maven")
                .hasMessageContaining(image.manifestDescriptor().digest().toString())
                .hasMessageContaining("oci-skills:update");
    }

    @Test
    void failsIfNoSourceHasTheLockedSkill() throws Exception {
        OciImageLayout image = TestImages.build(tempDirectory.resolve("other"), "cicd", "nexus-test", "1.0.0",
                PUBLISHED, "content");
        SkillOrigin locked = new SkillOrigin(NEXUS_TEST, "1.0.0", PUBLISHED, image.manifestDescriptor().digest(),
                "maven");

        assertThatThrownBy(() -> resolver().fetch(locked, LOCKED_REQUEST, work()))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("cicd/nexus-test")
                .hasMessageContaining("1.0.0")
                .hasMessageContaining("maven, oci");
    }

    @Test
    void asksTheSourcesForTheRepositoryOfTheSkill() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);

        resolver().resolve(new SkillRequest(NEXUS_TEST, "1.0.0", PUBLISHED, null, "teams/acme/skills"), work());

        assertThat(maven.repositories()).isNotEmpty().containsOnly("teams/acme/skills");
    }

    @Test
    void fetchesALockedSkillFromTheRepositoryOfTheSkill() throws Exception {
        OciImageLayout image = maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        SkillOrigin locked = new SkillOrigin(NEXUS_TEST, "1.0.0", PUBLISHED, image.manifestDescriptor().digest(),
                "maven");

        resolver().fetch(locked, new SkillRequest(NEXUS_TEST, "1.0.0", PUBLISHED, null, "teams/acme/skills"),
                work());

        assertThat(maven.repositories()).containsExactly("teams/acme/skills");
    }

    @Test
    void failsIfASourceCannotPlaceTheSkill() throws Exception {
        maven.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        oci.add("cicd", "nexus-test", "1.0.0", PUBLISHED);
        maven.requireRepository();

        assertThatThrownBy(() -> resolver().resolve(request("1.0.0", PUBLISHED, null), work()))
                .isInstanceOf(SkillPullException.class)
                .hasMessageContaining("<repository>");
    }
}

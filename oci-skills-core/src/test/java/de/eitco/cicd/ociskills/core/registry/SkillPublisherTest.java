package de.eitco.cicd.ociskills.core.registry;

import de.eitco.cicd.ociskills.core.GoldenMaster;
import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.Digest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SkillPublisherTest {

    private static final OciReference DESTINATION = new OciReference("localhost:5000", "skills/nexus-test", "0.0.1-draft");
    private static final Digest OTHER_DIGEST =
            Digest.parse("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    private OciImageLayout image;

    @BeforeEach
    void readImage() throws Exception {
        image = new OciLayoutReader().read(GoldenMaster.LAYOUT);
    }

    private static OciRegistryClient confirming(Digest digest) {
        return (image, destination, credentials) -> new PushResult(destination, digest, List.of(), List.of());
    }

    @Test
    void returnsResultIfConfirmedDigestMatches() throws Exception {
        PushResult result = new SkillPublisher(confirming(GoldenMaster.MANIFEST_DIGEST))
                .publish(image, DESTINATION, RegistryCredentials.anonymous(), true);

        assertThat(result.confirmedDigest()).isEqualTo(GoldenMaster.MANIFEST_DIGEST);
    }

    @Test
    void failsIfConfirmedDigestDiffers() {
        SkillPushException exception = catchThrowableOfType(SkillPushException.class,
                () -> new SkillPublisher(confirming(OTHER_DIGEST))
                        .publish(image, DESTINATION, RegistryCredentials.anonymous(), true));

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.DIGEST_MISMATCH);
        assertThat(exception).hasMessageContaining(OTHER_DIGEST.toString())
                .hasMessageContaining(GoldenMaster.MANIFEST_DIGEST.toString())
                .hasMessageContaining(DESTINATION.toString())
                .hasMessageContaining("[push]");
    }

    @Test
    void skipsVerificationIfDisabled() throws Exception {
        PushResult result = new SkillPublisher(confirming(OTHER_DIGEST))
                .publish(image, DESTINATION, RegistryCredentials.anonymous(), false);

        assertThat(result.confirmedDigest()).isEqualTo(OTHER_DIGEST);
    }

    @Test
    void failsIfRegistryDoesNotConfirmADigestAndVerificationIsEnabled() {
        SkillPushException exception = catchThrowableOfType(SkillPushException.class,
                () -> new SkillPublisher(confirming(null))
                        .publish(image, DESTINATION, RegistryCredentials.anonymous(), true));

        assertThat(exception.reason()).isEqualTo(SkillPushException.Reason.DIGEST_MISMATCH);
    }
}

package de.eitco.cicd.ociskills.maven;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static de.eitco.cicd.ociskills.maven.Configurations.configure;
import static org.assertj.core.api.Assertions.assertThat;

class SkillSourceConfigurationTest {

    @Test
    void showsAllFieldsInToString() {
        SkillSourceConfiguration source = new SkillSourceConfiguration();
        configure(source, "id", "my-oci");
        configure(source, "type", "oci");
        configure(source, "classifier", "{name[1:]}");
        configure(source, "registry", "registry.example.com");
        configure(source, "repository", "skills/{namespace}");
        configure(source, "registryServerId", "nexus");
        configure(source, "caCertificates", List.of(new File("ca.pem")));
        configure(source, "allowPlainHttp", true);

        assertThat(source).hasToString("SkillSourceConfiguration[id=my-oci, type=oci, groupId=null,"
                + " artifactId=null, classifier={name[1:]}, registry=registry.example.com,"
                + " repository=skills/{namespace}, registryServerId=nexus, caCertificates=[ca.pem], allowPlainHttp=true]");
    }

    @Test
    void showsUnsetFieldsAsNull() {
        assertThat(new SkillSourceConfiguration()).hasToString("SkillSourceConfiguration[id=null, type=null,"
                + " groupId=null, artifactId=null, classifier=null, registry=null, repository=null,"
                + " registryServerId=null, caCertificates=null, allowPlainHttp=false]");
    }
}

package de.eitco.cicd.ociskills.maven;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static de.eitco.cicd.ociskills.maven.Configurations.configure;
import static org.assertj.core.api.Assertions.assertThat;

class SkillConfigurationTest {

    @Test
    void showsAllFieldsInToString() {
        SkillConfiguration skill = new SkillConfiguration();
        configure(skill, "id", "acme/nexus-test");
        configure(skill, "version", "[1.0,2.0)");
        configure(skill, "status", "published");
        configure(skill, "source", "my-maven");
        configure(skill, "repository", "teams/acme/skills");
        configure(skill, "image", "registry.example.com/acme/nexus-test");
        configure(skill, "registryServerId", "my-registry");
        configure(skill, "allowPlainHttp", true);
        configure(skill, "caCertificates", List.of(new File("ca.pem")));
        configure(skill, "groupId", "com.example");
        configure(skill, "artifactId", "skills");
        configure(skill, "classifier", "{name}");

        assertThat(skill).hasToString("SkillConfiguration[id=acme/nexus-test, version=[1.0,2.0), status=published,"
                + " source=my-maven, repository=teams/acme/skills, image=registry.example.com/acme/nexus-test,"
                + " registryServerId=my-registry, allowPlainHttp=true, caCertificates=[ca.pem], groupId=com.example,"
                + " artifactId=skills, classifier={name}]");
    }

    @Test
    void showsUnsetFieldsAsNull() {
        assertThat(new SkillConfiguration()).hasToString(
                "SkillConfiguration[id=null, version=null, status=null, source=null, repository=null, image=null,"
                        + " registryServerId=null, allowPlainHttp=null, caCertificates=null, groupId=null,"
                        + " artifactId=null, classifier=null]");
    }
}

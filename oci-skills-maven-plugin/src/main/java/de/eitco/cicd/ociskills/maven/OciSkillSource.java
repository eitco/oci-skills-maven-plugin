package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.oci.StatusSuffix;
import de.eitco.cicd.ociskills.core.registry.OciReference;
import de.eitco.cicd.ociskills.core.registry.OciRegistryClient;
import de.eitco.cicd.ociskills.core.registry.RegistryCredentials;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Pulls skills from an OCI registry - like {@code oci-skills:push} pushes them: a skill is the image
 * {@code <registry>/<repository>/<name>:<tag>}, the tag is the version suffixed with the status unless the status
 * is {@code published}. Versions are taken from the tags of the repository.
 */
final class OciSkillSource implements SkillSource {

    private final String id;
    private final String registry;
    private final SourceTemplate repository;
    private final OciRegistryClient client;
    private final RegistryCredentials credentials;

    OciSkillSource(String id, String registry, SourceTemplate repository, OciRegistryClient client,
                   RegistryCredentials credentials) {
        this.id = id;
        this.registry = registry;
        this.repository = repository;
        this.client = client;
        this.credentials = credentials;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> versions(SkillId skill, String versionSpec) throws SkillPullException {
        return client.tags(reference(skill, null), credentials).stream()
                .map(tag -> StatusSuffix.split(tag).base())
                .filter(version -> SkillVersions.matches(versionSpec, version))
                .distinct()
                .toList();
    }

    @Override
    public Optional<OciImageLayout> fetch(SkillId skill, String version, SkillStatus status, Digest digest,
                                          Path directory) throws SkillPullException {
        return client.pull(reference(skill, StatusSuffix.append(version, status)), digest, directory, credentials);
    }

    private OciReference reference(SkillId skill, String tag) {
        return new OciReference(registry, repository.expand(skill) + "/" + skill.name(), tag);
    }
}

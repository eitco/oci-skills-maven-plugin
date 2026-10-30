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
 * is {@code published}. The repository is the {@code <repository>} of the skill - for skills without, the one
 * configured for the source. Versions are taken from the tags of the repository.
 */
final class OciSkillSource implements SkillSource {

    private final String id;
    private final String registry;
    private final SourceTemplate repositoryTemplate;
    private final OciRegistryClient client;
    private final RegistryCredentials credentials;

    /**
     * @param repositoryTemplate the repository of skills requested without - {@code null} if there is none
     */
    OciSkillSource(String id, String registry, SourceTemplate repositoryTemplate, OciRegistryClient client,
                   RegistryCredentials credentials) {
        this.id = id;
        this.registry = registry;
        this.repositoryTemplate = repositoryTemplate;
        this.client = client;
        this.credentials = credentials;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> versions(SkillId skill, String repository, String versionSpec) throws SkillPullException {
        return client.tags(reference(skill, repository, null), credentials).stream()
                .map(tag -> StatusSuffix.split(tag).base())
                .filter(version -> SkillVersions.matches(versionSpec, version))
                .distinct()
                .toList();
    }

    @Override
    public Optional<OciImageLayout> fetch(SkillId skill, String repository, String version, SkillStatus status,
                                          Digest digest, Path directory) throws SkillPullException {
        return client.pull(reference(skill, repository, StatusSuffix.append(version, status)), digest, directory,
                credentials);
    }

    private OciReference reference(SkillId skill, String repository, String tag) throws SkillPullException {
        if (repository != null) {
            return new OciReference(registry, repository + "/" + skill.name(), tag);
        }
        if (repositoryTemplate == null) {
            throw new SkillPullException("the source " + id + " has no <repository> - the skill " + skill
                    + " needs a <repository> to be pulled from it");
        }
        try {
            return new OciReference(registry, repositoryTemplate.expand(skill, null) + "/" + skill.name(), tag);
        } catch (IllegalArgumentException e) {
            throw new SkillPullException("the source " + id + " cannot locate the skill: " + e.getMessage(), e);
        }
    }
}

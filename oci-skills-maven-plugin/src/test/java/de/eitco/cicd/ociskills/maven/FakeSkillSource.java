package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory source of skill images. Like a maven repository, it ignores the requested digest. It stores every skill
 * regardless of its repository - the repositories asked for are recorded.
 */
final class FakeSkillSource implements SkillSource {

    private record Key(SkillId id, String version, SkillStatus status) {
    }

    private final String id;
    private final Path directory;
    private final Map<Key, OciImageLayout> images = new HashMap<>();
    private final List<String> fetches = new ArrayList<>();
    private final List<String> repositories = new ArrayList<>();
    private boolean repositoryRequired;

    FakeSkillSource(String id, Path directory) {
        this.id = id;
        this.directory = directory;
    }

    OciImageLayout add(String namespace, String name, String version, SkillStatus status) throws Exception {
        return add(namespace, name, version, status, "content");
    }

    OciImageLayout add(String namespace, String name, String version, SkillStatus status, String content)
            throws Exception {
        OciImageLayout image = TestImages.build(directory.resolve(id), namespace, name, version, status, content);
        images.put(new Key(new SkillId(namespace, name), version, status), image);
        return image;
    }

    /**
     * Lets the source fail for skills without repository - like a source whose templates use the repository.
     */
    void requireRepository() {
        repositoryRequired = true;
    }

    /**
     * @return {@code "<id>:<version>:<status>"} of every fetch
     */
    List<String> fetches() {
        return fetches;
    }

    /**
     * @return the repository of every call of {@link #versions} and {@link #fetch}
     */
    List<String> repositories() {
        return repositories;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> versions(SkillId skill, String repository, String versionSpec) throws SkillPullException {
        place(skill, repository);
        return images.keySet().stream()
                .filter(key -> key.id().equals(skill) && SkillVersions.matches(versionSpec, key.version()))
                .map(Key::version)
                .distinct()
                .toList();
    }

    @Override
    public Optional<SkillImage> fetch(SkillId skill, String repository, String version, SkillStatus status,
                                      Digest digest, Path target) throws SkillPullException {
        place(skill, repository);
        fetches.add(skill + ":" + version + ":" + status.value());
        return Optional.ofNullable(images.get(new Key(skill, version, status)));
    }

    private void place(SkillId skill, String repository) throws SkillPullException {
        repositories.add(repository);
        if (repositoryRequired && repository == null) {
            throw new SkillPullException("the source " + id + " needs a <repository> for " + skill);
        }
    }
}

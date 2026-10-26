package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.install.SkillId;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory source of skill images. Like a maven repository, it ignores the requested digest.
 */
final class FakeSkillSource implements SkillSource {

    private record Key(SkillId id, String version, SkillStatus status) {
    }

    private final String id;
    private final Path directory;
    private final Map<Key, OciImageLayout> images = new HashMap<>();
    private final List<String> fetches = new ArrayList<>();

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
     * @return {@code "<id>:<version>:<status>"} of every fetch
     */
    List<String> fetches() {
        return fetches;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> versions(SkillId skill, String versionSpec) {
        return images.keySet().stream()
                .filter(key -> key.id().equals(skill) && SkillVersions.matches(versionSpec, key.version()))
                .map(Key::version)
                .distinct()
                .toList();
    }

    @Override
    public Optional<OciImageLayout> fetch(SkillId skill, String version, SkillStatus status, Digest digest,
                                          Path target) {
        fetches.add(skill + ":" + version + ":" + status.value());
        return Optional.ofNullable(images.get(new Key(skill, version, status)));
    }
}

package de.eitco.cicd.ociskills.git.jgit;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

/**
 * A skill image that only knows its files - files below {@code scripts/} are executable.
 */
record FakeSkillImage(Map<String, String> files) implements SkillImage {

    @Override
    public OciDescriptor manifestDescriptor() {
        return null;
    }

    @Override
    public OciImageManifest manifest() {
        return null;
    }

    @Override
    public void extract(Path target) throws SkillBuildException {
        try {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path file = target.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, entry.getValue());
                if (entry.getKey().startsWith("scripts/")) {
                    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
                }
            }
        } catch (IOException e) {
            throw new SkillBuildException("extracting failed: " + e, e);
        }
    }
}

package de.eitco.cicd.ociskills.core.files;

import java.nio.file.Path;

/**
 * A file or directory that goes into the skill layer.
 *
 * @param path       the path inside the layer - relative to the skill directory, separated by {@code /}. Directory
 *                   paths end with {@code /}.
 * @param source     the file on disk providing the content. For symbolic links this is the link target.
 * @param directory  whether this entry is a directory
 * @param executable whether this entry is an executable file
 */
public record SkillFile(String path, Path source, boolean directory, boolean executable) {

    public static SkillFile directory(String path, Path source) {
        return new SkillFile(path, source, true, false);
    }

    public static SkillFile file(String path, Path source, boolean executable) {
        return new SkillFile(path, source, false, executable);
    }
}

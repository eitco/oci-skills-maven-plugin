package de.eitco.cicd.ociskills.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the skill directories below the skills directory: every direct subdirectory that is not hidden.
 */
final class SkillDirectories {

    private SkillDirectories() {
    }

    /**
     * @param skillsDirectory the directory containing one subdirectory per skill
     * @return the skill directories, sorted by name
     * @throws MojoFailureException if the directory does not exist or contains no skill
     */
    static List<Path> discover(Path skillsDirectory) throws MojoFailureException, MojoExecutionException {
        List<Path> skills = List.of();
        if (Files.isDirectory(skillsDirectory)) {
            try (Stream<Path> children = Files.list(skillsDirectory)) {
                skills = children.filter(Files::isDirectory)
                        .filter(child -> !child.getFileName().toString().startsWith("."))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                throw new MojoExecutionException("could not list skills in " + skillsDirectory, e);
            }
        }
        if (skills.isEmpty()) {
            throw new MojoFailureException("no skills found in " + skillsDirectory
                    + " - every subdirectory is expected to contain a skill with skill.yaml");
        }
        return skills;
    }
}

package de.eitco.cicd.ociskills.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * The common configuration of the skill goals: where the skills are and where their OCI image layouts are written.
 */
abstract class AbstractSkillMojo extends AbstractMojo {

    /**
     * The directory containing the skills. Every direct subdirectory that is not hidden is a skill directory with
     * {@code skill.yaml} and the prompt file.
     */
    @Parameter(property = "skill.skillsDirectory", defaultValue = "${project.basedir}/src/main/skills",
            required = true)
    private File skillsDirectory;

    /**
     * The directory the OCI image layouts are written to - one subdirectory per skill, named like the skill
     * directory.
     */
    @Parameter(property = "skill.outputDirectory", defaultValue = "${project.build.directory}/skill-oci",
            required = true)
    private File outputDirectory;

    /**
     * @return the skill directories, sorted by name
     * @throws MojoFailureException if there are no skills
     */
    protected List<Path> skills() throws MojoFailureException, MojoExecutionException {
        return SkillDirectories.discover(skillsDirectory.toPath());
    }

    /**
     * @return the directory of the OCI image layout of the given skill
     */
    protected Path outputDirectory(Path skill) {
        return outputDirectory.toPath().resolve(skill.getFileName().toString());
    }
}

package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.layer.TarArchiver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import javax.inject.Inject;
import java.io.File;
import java.util.Date;

/**
 * Packs the OCI image layouts of all skills into a tar file, so {@code install} and
 * {@code deploy} put them into maven repositories as well. With packaging {@code skill} the tar file is the main
 * artifact of the project, otherwise it is attached with classifier {@code skills}.
 */
@Mojo(name = "archive", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SkillArchiveMojo extends AbstractSkillMojo {

    static final String PACKAGING = "skill";

    /**
     * The tar file to write.
     */
    @Parameter(property = "skill.archive.file",
            defaultValue = "${project.build.directory}/${project.build.finalName}.tar", required = true)
    private File archiveFile;

    /**
     * The start time of the maven build - used as modification time of all entries.
     */
    @Parameter(defaultValue = "${session.request.startTime}", readonly = true, required = true)
    private Date buildStartTime;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private final MavenProjectHelper projectHelper;

    @Inject
    public SkillArchiveMojo(MavenProjectHelper projectHelper) {
        this.projectHelper = projectHelper;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        skills();
        try {
            new TarArchiver().archive(outputDirectory(), buildStartTime.toInstant(), archiveFile.toPath());
        } catch (SkillBuildException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        if (PACKAGING.equals(project.getPackaging())) {
            project.getArtifact().setFile(archiveFile);
        } else {
            projectHelper.attachArtifact(project, "tar", "skills", archiveFile);
        }
        getLog().info("Archived the skill images to " + archiveFile);
    }
}

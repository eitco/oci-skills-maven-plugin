package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.layer.TarArchiver;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import de.eitco.cicd.ociskills.core.layout.OciLayoutReader;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.oci.StatusSuffix;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Packs the OCI image layouts of all skills into a tar file, so {@code install} and
 * {@code deploy} put them into maven repositories as well. With packaging {@code skill} the tar file is the main
 * artifact of the project, otherwise it is attached with classifier {@code skills}.
 * <p>
 * Additionally, the layout of every skill is attached on its own, so a single skill can be resolved from a maven
 * repository. Its classifier is the skill name, suffixed with the status unless the status is {@code published} -
 * like the tag of the image, e.g. {@code nexus-test} or {@code nexus-test-draft}.
 */
@Mojo(name = "archive", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SkillArchiveMojo extends AbstractSkillMojo {

    static final String PACKAGING = "skill";
    static final String AGGREGATE_CLASSIFIER = "skills";

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
        List<Path> skills = skills();
        archive(outputDirectory(), archiveFile.toPath());
        if (PACKAGING.equals(project.getPackaging())) {
            project.getArtifact().setFile(archiveFile);
        } else {
            projectHelper.attachArtifact(project, "tar", AGGREGATE_CLASSIFIER, archiveFile);
        }
        getLog().info("Archived the skill images to " + archiveFile);
        for (Path skill : skills) {
            String classifier = classifier(outputDirectory(skill));
            if (!PACKAGING.equals(project.getPackaging()) && AGGREGATE_CLASSIFIER.equals(classifier)) {
                throw new MojoFailureException("the skill " + name(skill) + " cannot be attached, its classifier '"
                        + classifier + "' is used by the archive of all skills");
            }
            Path skillArchive = archiveFile.toPath().resolveSibling(baseName() + "-" + classifier + ".tar");
            archive(outputDirectory(skill), skillArchive);
            projectHelper.attachArtifact(project, "tar", classifier, skillArchive.toFile());
            getLog().info("Archived the skill image " + name(skill) + " to " + skillArchive);
        }
    }

    private void archive(Path directory, Path target) throws MojoExecutionException {
        try {
            new TarArchiver().archive(directory, buildStartTime.toInstant(), target);
        } catch (SkillBuildException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private static String classifier(Path layout) throws MojoFailureException {
        OciImageLayout image;
        try {
            image = new OciLayoutReader().read(layout);
        } catch (SkillBuildException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        Map<String, String> annotations = image.manifest().annotations() == null ? Map.of()
                : image.manifest().annotations();
        String name = annotations.get(ImageManifestBuilder.ANNOTATION_TITLE);
        String status = annotations.get(SkillStatus.ANNOTATION);
        if (name == null || status == null) {
            throw new MojoFailureException("manifest in " + layout + " has no " + ImageManifestBuilder.ANNOTATION_TITLE
                    + " or " + SkillStatus.ANNOTATION + " annotation - rebuild it with oci-skills:build");
        }
        return StatusSuffix.append(name, SkillStatus.parse(status));
    }

    private String baseName() {
        String fileName = archiveFile.getName();
        return fileName.endsWith(".tar") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}

package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.archive.SkillJarWriter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Packs all skills into a jar, so {@code install} and {@code deploy} put them into maven repositories as well. The
 * jar contains the files of every skill below a directory named like the skill - ready to be unpacked into
 * {@code .claude/skills} - and manifest and config of its image below {@code META-INF/oci-skills/<name>/}, so the
 * digest of the image can be verified. With packaging {@code skill} the jar is the main artifact of the project,
 * otherwise it is attached with classifier {@code skills}.
 * <p>
 * Additionally, every skill is attached as jar on its own, so a single skill can be resolved from a maven
 * repository. Its classifier is the skill name, suffixed with the status unless the status is {@code published} -
 * like the tag of the image, e.g. {@code nexus-test} or {@code nexus-test-draft}.
 * <p>
 * The jars are reproducible: entries are sorted and stamped with the creation time of the images.
 */
@Mojo(name = "archive", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SkillArchiveMojo extends AbstractSkillMojo {

    static final String PACKAGING = "skill";
    static final String AGGREGATE_CLASSIFIER = "skills";
    static final String TYPE = "jar";

    /**
     * The jar file of all skills to write.
     */
    @Parameter(property = "skill.archive.file",
            defaultValue = "${project.build.directory}/${project.build.finalName}.jar", required = true)
    private File archiveFile;

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
        List<OciImageLayout> images = new ArrayList<>();
        for (Path skill : skills) {
            images.add(read(outputDirectory(skill)));
        }
        archive(images, archiveFile.toPath());
        if (PACKAGING.equals(project.getPackaging())) {
            project.getArtifact().setFile(archiveFile);
        } else {
            projectHelper.attachArtifact(project, TYPE, AGGREGATE_CLASSIFIER, archiveFile);
        }
        getLog().info("Archived the skills to " + archiveFile);
        for (int index = 0; index < skills.size(); index++) {
            Path skill = skills.get(index);
            OciImageLayout image = images.get(index);
            String classifier = classifier(image);
            if (!PACKAGING.equals(project.getPackaging()) && AGGREGATE_CLASSIFIER.equals(classifier)) {
                throw new MojoFailureException("the skill " + name(skill) + " cannot be attached, its classifier '"
                        + classifier + "' is used by the archive of all skills");
            }
            Path skillArchive = archiveFile.toPath().resolveSibling(baseName() + "-" + classifier + "." + TYPE);
            archive(List.of(image), skillArchive);
            projectHelper.attachArtifact(project, TYPE, classifier, skillArchive.toFile());
            getLog().info("Archived the skill " + name(skill) + " to " + skillArchive);
        }
    }

    private static void archive(List<OciImageLayout> images, Path target) throws MojoExecutionException {
        try {
            new SkillJarWriter().write(images, target);
        } catch (SkillBuildException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private static OciImageLayout read(Path layout) throws MojoFailureException {
        try {
            return new OciLayoutReader().read(layout);
        } catch (SkillBuildException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private static String classifier(OciImageLayout image) throws MojoFailureException {
        Map<String, String> annotations = image.manifest().annotations() == null ? Map.of()
                : image.manifest().annotations();
        String name = annotations.get(ImageManifestBuilder.ANNOTATION_TITLE);
        String status = annotations.get(SkillStatus.ANNOTATION);
        if (name == null || status == null) {
            throw new MojoFailureException("manifest in " + image.directory() + " has no " + ImageManifestBuilder.ANNOTATION_TITLE
                    + " or " + SkillStatus.ANNOTATION + " annotation - rebuild it with oci-skills:build");
        }
        return StatusSuffix.append(name, SkillStatus.parse(status));
    }

    private String baseName() {
        String fileName = archiveFile.getName();
        String extension = "." + TYPE;
        return fileName.endsWith(extension) ? fileName.substring(0, fileName.length() - extension.length()) : fileName;
    }
}

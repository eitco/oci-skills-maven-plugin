package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillBuildRequest;
import de.eitco.cicd.ociskills.core.SkillBuildResult;
import de.eitco.cicd.ociskills.core.SkillImageBuilder;
import de.eitco.cicd.ociskills.core.SkillValidationException;
import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Builds every skill directory below {@code skillsDirectory} into an OCI image layout. The {@code skill.yaml} of each
 * image is generated from the frontmatter of the skill's {@code SKILL.md} and the plugin configuration. The results are written to
 * {@code <outputDirectory>/<skill directory name>} and are the input of {@code oci-skills:push}. No registry configuration
 * or credentials are needed.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class SkillBuildMojo extends AbstractSkillMojo {

    /**
     * Ant style patterns of the files to put into the layer, relative to each skill directory. Defaults to all
     * files ({@code **}{@code /*}).
     */
    @Parameter
    private List<String> includes;

    /**
     * Ant style patterns of the files to leave out of the layer. Excludes win over includes. Defaults to
     * {@code .git/**} and {@code target/**}. Setting this parameter replaces the defaults.
     */
    @Parameter
    private List<String> excludes;

    /**
     * Ant style patterns of files that are written with mode {@code 0755}. Files with the executable bit set in
     * the file system are executable as well.
     */
    @Parameter
    private List<String> executables;

    /**
     * The lifecycle status of the image: {@code draft}, {@code testing} or {@code published}. Determines the tag
     * ({@code <version>-draft}, {@code <version>-testing}, {@code <version>}) and the
     * {@code io.skillimage.status} annotation.
     */
    @Parameter(property = "skill.status", defaultValue = "draft", required = true)
    private String status;

    /**
     * The namespace of all skills - a DNS label, e.g. {@code cicd}. Written to {@code metadata.namespace} of the
     * generated {@code skill.yaml} and of the frontmatter of the packaged {@code SKILL.md}. Name and description of a
     * skill are taken from the frontmatter of its {@code SKILL.md}.
     */
    @Parameter(property = "skill.namespace", required = true)
    private String namespace;

    /**
     * The version of all skills. Written to {@code metadata.version} of the generated {@code skill.yaml} and of the
     * frontmatter of the packaged {@code SKILL.md}. Determines the tag of the images.
     */
    @Parameter(property = "skill.version", defaultValue = "${project.version}", required = true)
    private String version;

    /**
     * The directory the generated files are written to - one subdirectory per skill, named like the skill directory,
     * containing the generated {@code skill.yaml} and the {@code SKILL.md} with {@code namespace} and {@code version}
     * added to the {@code metadata} of its frontmatter. Both are put into the image instead of the source files.
     */
    @Parameter(property = "skill.generatedSkillsDirectory",
            defaultValue = "${project.build.directory}/generated-skills", required = true)
    private File generatedSkillsDirectory;

    /**
     * The start time of the maven build - used for all timestamps of the images.
     */
    @Parameter(defaultValue = "${session.request.startTime}", readonly = true, required = true)
    private Date buildStartTime;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        SkillStatus skillStatus;
        try {
            skillStatus = SkillStatus.parse(status);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        Instant timestamp = buildStartTime.toInstant();
        FileSelection selection = new FileSelection(includes, excludes, executables);
        SkillCardSettings settings = new SkillCardSettings(namespace, version);
        List<Path> skills = skills();
        for (Path skill : skills) {
            build(new SkillBuildRequest(skill, outputDirectory(skill), selection, skillStatus, timestamp, settings,
                    generatedDirectory(skill)));
        }
        getLog().info("Built " + skills.size() + " skill image" + (skills.size() == 1 ? "" : "s"));
    }

    private Path generatedDirectory(Path skill) {
        return generatedSkillsDirectory.toPath().resolve(skill.getFileName().toString());
    }

    private void build(SkillBuildRequest request) throws MojoExecutionException, MojoFailureException {
        try {
            SkillBuildResult result = new SkillImageBuilder().build(request);
            getLog().info("Built skill image " + result.skillCard().metadata().namespace() + "/"
                    + result.skillCard().metadata().name() + ":" + result.tag() + " in " + result.outputDirectory());
            getLog().info("  generated " + request.generatedDirectory());
            getLog().info("  manifest " + result.manifest().digest() + " (" + result.manifest().size() + " bytes)");
            getLog().info("  config   " + result.config().digest() + " (" + result.config().size() + " bytes)");
            getLog().info("  layer    " + result.layer().digest() + " (" + result.layer().size() + " bytes), diffID "
                    + result.diffId());
        } catch (SkillValidationException e) {
            throw new MojoFailureException(e.getMessage(), e);
        } catch (SkillBuildException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}

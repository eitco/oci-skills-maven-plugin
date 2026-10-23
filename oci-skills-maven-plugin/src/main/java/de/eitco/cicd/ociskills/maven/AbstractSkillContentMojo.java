package de.eitco.cicd.ociskills.maven;

import de.eitco.cicd.ociskills.core.files.FileSelection;
import de.eitco.cicd.ociskills.core.skillcard.SkillCardSettings;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

/**
 * The configuration of the goals that need to know the content of the images: which files go into them and the values
 * of the generated SkillCard.
 */
abstract class AbstractSkillContentMojo extends AbstractSkillMojo {

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
     * The namespace of all skills - a DNS label, e.g. {@code cicd}. Written to {@code metadata.namespace} of the
     * generated {@code skill.yaml} and to {@code namespace} of the frontmatter of the packaged {@code SKILL.md}. Name and description of a
     * skill are taken from the frontmatter of its {@code SKILL.md}.
     */
    @Parameter(property = "skill.namespace", required = true)
    private String namespace;

    /**
     * The version of all skills. Written to {@code metadata.version} of the generated {@code skill.yaml} and to
     * {@code version} of the frontmatter of the packaged {@code SKILL.md}. Determines the tag of the images.
     */
    @Parameter(property = "skill.version", defaultValue = "${project.version}", required = true)
    private String version;

    protected FileSelection fileSelection() {
        return new FileSelection(includes, excludes, executables);
    }

    protected SkillCardSettings skillCardSettings() {
        return new SkillCardSettings(namespace, version);
    }
}

package de.eitco.cicd.ociskills.core.archive;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.files.FileTrees;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * A skill read from a skill jar and verified against its manifest.
 * <p>
 * A skill jar contains the files of the layer below a directory named like the skill and the manifest and the config
 * of the image byte for byte below {@value #METADATA_DIRECTORY}{@code <name>/}. A jar may contain several skills.
 *
 * @param directory          the extracted files of the skill
 * @param manifestDescriptor the descriptor of the manifest stored in the jar
 * @param manifest           the parsed manifest
 */
public record SkillJar(Path directory, OciDescriptor manifestDescriptor, OciImageManifest manifest)
        implements SkillImage {

    public static final String METADATA_DIRECTORY = "META-INF/oci-skills/";
    public static final String MANIFEST_FILE = "manifest.json";
    public static final String CONFIG_FILE = "config.json";

    @Override
    public void extract(Path target) throws SkillBuildException {
        try {
            FileTrees.copyRecursively(directory, target);
        } catch (IOException e) {
            throw new SkillBuildException("copying " + directory + " to " + target + " failed: " + e, e);
        }
    }

    static String metadataPath(String name, String file) {
        return METADATA_DIRECTORY + name + "/" + file;
    }

    /**
     * @return the name of the skill - the directory its files are stored in
     */
    static String name(OciImageManifest manifest, String location) throws SkillBuildException {
        String name = annotations(manifest).get(ImageManifestBuilder.ANNOTATION_TITLE);
        if (name == null || name.isBlank() || name.contains("/") || name.startsWith(".")) {
            throw new SkillBuildException("the manifest of " + location + " has no valid "
                    + ImageManifestBuilder.ANNOTATION_TITLE + " annotation: " + name);
        }
        return name;
    }

    /**
     * @return the creation time of the image - the modification time of all entries
     */
    static Instant created(OciImageManifest manifest, String location) throws SkillBuildException {
        String created = annotations(manifest).get(ImageManifestBuilder.ANNOTATION_CREATED);
        try {
            return Instant.parse(created);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new SkillBuildException("the manifest of " + location + " has no valid "
                    + ImageManifestBuilder.ANNOTATION_CREATED + " annotation: " + created, e);
        }
    }

    /**
     * @return the only layer of the image
     */
    static OciDescriptor layer(OciImageManifest manifest, String location) throws SkillBuildException {
        List<OciDescriptor> layers = manifest.layers();
        if (layers == null || layers.size() != 1 || !MediaTypes.IMAGE_LAYER_TAR_GZIP.equals(layers.get(0).mediaType())) {
            throw new SkillBuildException("the image of " + location + " must have exactly one layer of type "
                    + MediaTypes.IMAGE_LAYER_TAR_GZIP);
        }
        return layers.get(0);
    }

    private static Map<String, String> annotations(OciImageManifest manifest) {
        return manifest.annotations() == null ? Map.of() : manifest.annotations();
    }
}

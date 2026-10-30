package de.eitco.cicd.ociskills.core.install;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.SkillPullException;
import de.eitco.cicd.ociskills.core.layout.SkillImage;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.SkillStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Installs skill images into a directory like {@code .claude/skills} - one subdirectory per skill, named like the
 * skill. Installed skills are marked with {@value #ORIGIN_FILE}. Only marked directories are ever replaced or
 * removed, so skills written by hand can live in the same directory.
 */
public class SkillInstaller {

    public static final String ORIGIN_FILE = ".oci-skill.json";
    public static final String GITIGNORE_FILE = ".gitignore";

    static final List<String> GITIGNORE = List.of("# installed by oci-skills-maven-plugin - not checked in", "*");

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final Path directory;

    public SkillInstaller(Path directory) {
        this.directory = directory;
    }

    /**
     * @return the origins of the installed skills by directory name
     */
    public Map<String, SkillOrigin> installed() throws SkillPullException {
        Map<String, SkillOrigin> installed = new TreeMap<>();
        if (!Files.isDirectory(directory)) {
            return installed;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory, Files::isDirectory)) {
            for (Path child : children) {
                Path originFile = child.resolve(ORIGIN_FILE);
                if (Files.isRegularFile(originFile)) {
                    installed.put(child.getFileName().toString(), readOrigin(originFile));
                }
            }
        } catch (IOException e) {
            throw new SkillPullException("reading the installed skills in " + directory + " failed: " + e, e);
        }
        return installed;
    }

    /**
     * Extracts the files of the image into the directory of the skill, replacing a previously installed version.
     * The directory contains a {@value #GITIGNORE_FILE} ignoring it in git.
     *
     * @throws SkillPullException if the image is not the skill of the origin or the directory exists, but is not
     *                            installed by the plugin
     */
    public void install(SkillImage image, SkillOrigin origin) throws SkillPullException {
        Map<String, String> annotations = image.manifest().annotations() == null ? Map.of()
                : image.manifest().annotations();
        String imageId = annotations.get(ImageManifestBuilder.ANNOTATION_VENDOR) + "/"
                + annotations.get(ImageManifestBuilder.ANNOTATION_TITLE);
        if (!imageId.equals(origin.id().toString())) {
            throw new SkillPullException("the image " + image.manifestDescriptor().digest() + " contains the skill "
                    + imageId + ", not " + origin.id());
        }
        List<OciDescriptor> layers = image.manifest().layers();
        if (layers.size() != 1 || !MediaTypes.IMAGE_LAYER_TAR_GZIP.equals(layers.get(0).mediaType())) {
            throw new SkillPullException("the image of " + origin.id() + " must have exactly one layer of type "
                    + MediaTypes.IMAGE_LAYER_TAR_GZIP);
        }
        String name = origin.id().name();
        Path target = directory.resolve(name);
        if (Files.exists(target) && !Files.isRegularFile(target.resolve(ORIGIN_FILE))) {
            throw new SkillPullException(target + " is not installed by the plugin - remove it or rename it to "
                    + "install " + origin.id());
        }
        Path staging = directory.resolve("." + name + ".installing");
        try {
            delete(staging);
            image.extract(staging);
            writeOrigin(staging.resolve(ORIGIN_FILE), origin);
            Files.write(staging.resolve(GITIGNORE_FILE), GITIGNORE);
            delete(target);
            Files.move(staging, target);
        } catch (IOException e) {
            throw new SkillPullException("installing " + origin.id() + " into " + target + " failed: " + e, e);
        } catch (SkillBuildException e) {
            throw new SkillPullException("installing " + origin.id() + " into " + target + " failed: "
                    + e.getMessage(), e);
        } finally {
            try {
                delete(staging);
            } catch (IOException ignored) {
                // the staging directory is removed by the next install
            }
        }
    }

    /**
     * Removes an installed skill.
     */
    public void remove(String name) throws SkillPullException {
        Path target = directory.resolve(name);
        if (!Files.isRegularFile(target.resolve(ORIGIN_FILE))) {
            throw new SkillPullException(target + " is not installed by the plugin and is not removed");
        }
        try {
            delete(target);
        } catch (IOException e) {
            throw new SkillPullException("removing " + target + " failed: " + e, e);
        }
    }

    private static SkillOrigin readOrigin(Path file) throws SkillPullException {
        try {
            OriginJson json = JSON.readValue(file.toFile(), OriginJson.class);
            return new SkillOrigin(SkillId.parse(json.id()), json.version(), SkillStatus.parse(json.status()),
                    Digest.parse(json.digest()), json.source());
        } catch (JacksonException | IllegalArgumentException e) {
            throw new SkillPullException(file + " is invalid - remove the skill directory to install it again: "
                    + e.getMessage(), e);
        }
    }

    private static void writeOrigin(Path file, SkillOrigin origin) throws IOException {
        try {
            JSON.writeValue(file.toFile(), new OriginJson(origin.id().toString(), origin.version(),
                    origin.status().value(), origin.digest().toString(), origin.source()));
        } catch (JacksonException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path each : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        }
    }

    private record OriginJson(String id, String version, String status, String digest, String source) {
    }
}

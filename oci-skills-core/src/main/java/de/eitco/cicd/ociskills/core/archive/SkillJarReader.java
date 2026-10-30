package de.eitco.cicd.ociskills.core.archive;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.files.FileTrees;
import de.eitco.cicd.ociskills.core.files.SkillFile;
import de.eitco.cicd.ociskills.core.layer.LayerBlob;
import de.eitco.cicd.ociskills.core.layer.TarLayerBuilder;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageConfig;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;
import de.eitco.cicd.ociskills.core.oci.OciJson;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads and verifies a jar written by {@link SkillJarWriter} containing a single skill.
 * <p>
 * The digest of the image is the digest of the stored manifest. The stored config has to match the config descriptor
 * of the manifest. The layer is rebuilt from the files of the jar - with the creation time of the image and the modes
 * of the jar entries - and its diff id has to match the one of the config. The compressed layer is not compared, its
 * bytes depend on the JDK.
 */
public class SkillJarReader {

    private static final String META_INF = "META-INF/";

    /**
     * @param jar           the jar to read
     * @param workDirectory an empty directory the files of the skill are extracted to - created if missing
     * @throws SkillBuildException if the jar does not contain exactly one skill or the skill does not match its
     *                             manifest
     */
    public SkillJar read(Path jar, Path workDirectory) throws SkillBuildException {
        Path root = workDirectory.toAbsolutePath().normalize();
        Map<String, byte[]> metadata = new HashMap<>();
        Set<String> names = new TreeSet<>();
        List<SkillFile> files = new ArrayList<>();
        try (ZipFile zip = ZipFile.builder().setPath(jar).get()) {
            Files.createDirectories(root);
            for (ZipArchiveEntry entry : Collections.list(zip.getEntriesInPhysicalOrder())) {
                String path = entry.getName();
                if (path.startsWith(SkillJar.METADATA_DIRECTORY)) {
                    String metadataPath = path.substring(SkillJar.METADATA_DIRECTORY.length());
                    int separator = metadataPath.indexOf('/');
                    if (separator > 0 && !entry.isDirectory()) {
                        names.add(metadataPath.substring(0, separator));
                        try (InputStream in = zip.getInputStream(entry)) {
                            metadata.put(path, in.readAllBytes());
                        }
                    }
                } else if (!path.startsWith(META_INF)) {
                    extract(zip, entry, root, names, files);
                }
            }
        } catch (IOException e) {
            throw new SkillBuildException("reading " + jar + " failed: " + e, e);
        }
        if (names.isEmpty()) {
            throw new SkillBuildException(jar + " contains no skill");
        }
        if (names.size() > 1) {
            throw new SkillBuildException(jar + " has to contain exactly one skill, but contains " + names);
        }
        String name = names.iterator().next();
        String location = name + " in " + jar;
        byte[] manifestBytes = metadata(metadata, name, SkillJar.MANIFEST_FILE, jar);
        OciDescriptor manifestDescriptor = descriptor(MediaTypes.IMAGE_MANIFEST, manifestBytes);
        OciImageManifest manifest = OciJson.deserialize(manifestBytes, OciImageManifest.class);
        verify(manifest, name, location, metadata(metadata, name, SkillJar.CONFIG_FILE, jar),
                files.stream().filter(file -> file.path().startsWith(name + "/"))
                        .map(file -> new SkillFile(file.path().substring(name.length() + 1), file.source(),
                                file.directory(), file.executable()))
                        .toList(), root);
        return new SkillJar(root.resolve(name), manifestDescriptor, manifest);
    }

    /**
     * Extracts a file or directory of a skill.
     *
     * @param files receives the extracted entry - its path is relative to the work directory, the skill directory
     *              itself is omitted
     */
    private static void extract(ZipFile zip, ZipArchiveEntry entry, Path root, Set<String> names,
                                List<SkillFile> files) throws IOException, SkillBuildException {
        String path = entry.getName();
        Path target = FileTrees.resolveEntry(root, path);
        Path relative = root.relativize(target);
        if (target.equals(root) || relative.getNameCount() == 1 && !entry.isDirectory()) {
            throw new SkillBuildException("entry " + path + " is not inside a skill directory");
        }
        names.add(relative.getName(0).toString());
        String normalized = relative.toString().replace('\\', '/');
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            if (relative.getNameCount() > 1) {
                files.add(SkillFile.directory(normalized + "/", target));
            }
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream in = zip.getInputStream(entry)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        boolean executable = (entry.getUnixMode() & 0111) != 0;
        FileTrees.setExecutable(target, executable);
        files.add(SkillFile.file(normalized, target, executable));
    }

    private static void verify(OciImageManifest manifest, String name, String location, byte[] configBytes,
                               List<SkillFile> files, Path root) throws SkillBuildException {
        if (!MediaTypes.IMAGE_MANIFEST.equals(manifest.mediaType()) || manifest.config() == null) {
            throw new SkillBuildException("the manifest of " + location + " is no OCI image manifest with config");
        }
        if (!name.equals(SkillJar.name(manifest, location))) {
            throw new SkillBuildException("the manifest of " + location + " belongs to the skill "
                    + SkillJar.name(manifest, location));
        }
        OciDescriptor config = descriptor(manifest.config().mediaType(), configBytes);
        if (!config.digest().equals(manifest.config().digest()) || config.size() != manifest.config().size()) {
            throw new SkillBuildException("the config of " + location + " does not match the manifest: it has the "
                    + "digest " + config.digest() + " and " + config.size() + " bytes, the manifest says "
                    + manifest.config().digest() + " and " + manifest.config().size() + " bytes");
        }
        SkillJar.layer(manifest, location);
        Instant created = SkillJar.created(manifest, location);
        OciImageConfig.RootFs rootFs = OciJson.deserialize(configBytes, OciImageConfig.class).rootfs();
        if (rootFs == null || rootFs.diffIds() == null || rootFs.diffIds().size() != 1) {
            throw new SkillBuildException("the config of " + location + " has to list exactly one diff_id");
        }
        Digest diffId = rebuiltDiffId(files, created, root);
        if (!diffId.equals(rootFs.diffIds().get(0))) {
            throw new SkillBuildException("the files of " + location + " do not match the image: the layer built "
                    + "from them has the diff_id " + diffId + ", the config says " + rootFs.diffIds().get(0));
        }
    }

    private static Digest rebuiltDiffId(List<SkillFile> files, Instant created, Path root) throws SkillBuildException {
        Path layer;
        try {
            layer = Files.createTempFile(root, ".layer-", ".tar.gz");
        } catch (IOException e) {
            throw new SkillBuildException("creating a temporary file in " + root + " failed: " + e, e);
        }
        try {
            LayerBlob blob = new TarLayerBuilder().build(files, created, layer);
            return blob.diffId();
        } finally {
            try {
                Files.deleteIfExists(layer);
            } catch (IOException ignored) {
                // the work directory is removed by the caller
            }
        }
    }

    private static byte[] metadata(Map<String, byte[]> metadata, String name, String file, Path jar)
            throws SkillBuildException {
        byte[] content = metadata.get(SkillJar.metadataPath(name, file));
        if (content == null) {
            throw new SkillBuildException(jar + " has no " + SkillJar.metadataPath(name, file)
                    + " - it is no skill jar written by oci-skills:archive");
        }
        return content;
    }

    private static OciDescriptor descriptor(String mediaType, byte[] content) {
        return new OciDescriptor(mediaType, Digest.sha256(OciJson.sha256().digest(content)), content.length);
    }
}

package de.eitco.cicd.ociskills.core.layout;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.ImageManifestBuilder;
import de.eitco.cicd.ociskills.core.oci.MediaTypes;
import de.eitco.cicd.ociskills.core.oci.OciDescriptor;
import de.eitco.cicd.ociskills.core.oci.OciImageIndex;
import de.eitco.cicd.ociskills.core.oci.OciImageManifest;
import de.eitco.cicd.ociskills.core.oci.OciJson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and verifies an OCI image layout containing a single skill image.
 */
public class OciLayoutReader {

    /**
     * Reads the layout and verifies that all referenced blobs exist and match their digests and sizes.
     *
     * @throws SkillBuildException if the layout is missing or inconsistent - naming the affected descriptor
     */
    public OciImageLayout read(Path directory) throws SkillBuildException {
        Path indexFile = directory.resolve(OciImageLayout.INDEX_FILE);
        if (!Files.isRegularFile(directory.resolve(OciImageLayout.OCI_LAYOUT_FILE)) || !Files.isRegularFile(indexFile)) {
            throw new SkillBuildException("no OCI image layout found in " + directory
                    + " - run oci-skills:build before oci-skills:push");
        }
        OciImageIndex index = OciJson.deserialize(readAll(indexFile), OciImageIndex.class);
        if (index.manifests() == null || index.manifests().size() != 1) {
            throw new SkillBuildException("index.json in " + directory + " must reference exactly one manifest");
        }
        OciDescriptor indexEntry = index.manifests().get(0);
        if (!MediaTypes.IMAGE_MANIFEST.equals(indexEntry.mediaType())) {
            throw new SkillBuildException("unsupported manifest media type " + indexEntry.mediaType() + " in "
                    + indexFile);
        }
        Map<String, String> annotations = indexEntry.annotations() == null ? Map.of() : indexEntry.annotations();
        String tag = annotations.get(ImageManifestBuilder.ANNOTATION_REF_NAME);
        if (tag == null || tag.isBlank()) {
            throw new SkillBuildException("manifest in " + indexFile + " has no "
                    + ImageManifestBuilder.ANNOTATION_REF_NAME + " annotation");
        }

        OciDescriptor manifestDescriptor = new OciDescriptor(indexEntry.mediaType(), indexEntry.digest(),
                indexEntry.size());
        OciImageLayout layout = new OciImageLayout(directory, manifestDescriptor, null, tag);
        byte[] manifestBytes = verifiedBlob(layout, manifestDescriptor);
        OciImageManifest manifest = OciJson.deserialize(manifestBytes, OciImageManifest.class);
        if (manifest.config() == null || manifest.layers() == null || manifest.layers().isEmpty()) {
            throw new SkillBuildException("manifest " + manifestDescriptor.digest() + " in " + directory
                    + " must reference a config and at least one layer");
        }
        List<OciDescriptor> blobs = new ArrayList<>();
        blobs.add(manifest.config());
        blobs.addAll(manifest.layers());
        for (OciDescriptor blob : blobs) {
            verify(layout, blob);
        }
        return new OciImageLayout(directory, manifestDescriptor, manifest, tag);
    }

    private static byte[] verifiedBlob(OciImageLayout layout, OciDescriptor descriptor) throws SkillBuildException {
        verify(layout, descriptor);
        return readAll(layout.blob(descriptor));
    }

    private static void verify(OciImageLayout layout, OciDescriptor descriptor) throws SkillBuildException {
        Path blob = layout.blob(descriptor);
        String name = descriptor.mediaType() + " " + descriptor.digest();
        if (!Files.isRegularFile(blob)) {
            throw new SkillBuildException("OCI image layout " + layout.directory() + " is inconsistent: blob of "
                    + name + " is missing");
        }
        MessageDigest messageDigest = OciJson.sha256();
        long size = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(blob)) {
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                messageDigest.update(buffer, 0, read);
                size += read;
            }
        } catch (IOException e) {
            throw new SkillBuildException("reading blob of " + name + " failed: " + e, e);
        }
        if (size != descriptor.size()) {
            throw new SkillBuildException("OCI image layout " + layout.directory() + " is inconsistent: size of "
                    + name + " is " + size + " bytes, but descriptor says " + descriptor.size());
        }
        Digest actual = Digest.sha256(messageDigest.digest());
        if (!actual.equals(descriptor.digest())) {
            throw new SkillBuildException("OCI image layout " + layout.directory() + " is inconsistent: content of "
                    + name + " does not match its digest (actual " + actual + ")");
        }
    }

    private static byte[] readAll(Path file) throws SkillBuildException {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new SkillBuildException("reading " + file + " failed: " + e, e);
        }
    }
}

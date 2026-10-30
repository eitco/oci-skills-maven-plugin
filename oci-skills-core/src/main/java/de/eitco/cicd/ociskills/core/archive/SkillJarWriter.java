package de.eitco.cicd.ociskills.core.archive;

import de.eitco.cicd.ociskills.core.SkillBuildException;
import de.eitco.cicd.ociskills.core.files.SkillFileCollector;
import de.eitco.cicd.ociskills.core.layer.TarLayerBuilder;
import de.eitco.cicd.ociskills.core.layout.OciImageLayout;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Writes skill images into a reproducible jar - see {@link SkillJar} for its layout. The files are taken from the
 * layer with their modes, manifest and config are copied byte for byte, so the digest of the image is kept.
 * <p>
 * Normalization: entries sorted by path, the creation time of the image as modification time, no extra fields and
 * no {@code META-INF/MANIFEST.MF}.
 */
public class SkillJarWriter {

    private record Entry(String path, int mode, Instant time, byte[] content) {

        boolean directory() {
            return content == null;
        }
    }

    /**
     * @param layouts the images to write - their names have to be unique
     * @param jar     the jar to write
     */
    public void write(List<OciImageLayout> layouts, Path jar) throws SkillBuildException {
        Map<String, Entry> entries = new TreeMap<>(SkillFileCollector.PATH_ORDER);
        for (OciImageLayout layout : layouts) {
            add(layout, entries);
        }
        try {
            Files.createDirectories(jar.toAbsolutePath().getParent());
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(jar)) {
                for (Entry entry : entries.values()) {
                    ZipArchiveEntry zipEntry = new ZipArchiveEntry(entry.path());
                    zipEntry.setUnixMode((entry.directory() ? UnixStat.DIR_FLAG : UnixStat.FILE_FLAG) | entry.mode());
                    zipEntry.setTime(dosTime(entry.time()));
                    // a known size keeps the stream from reserving a zip64 extra field
                    zipEntry.setSize(entry.directory() ? 0 : entry.content().length);
                    out.putArchiveEntry(zipEntry);
                    if (!entry.directory()) {
                        out.write(entry.content());
                    }
                    out.closeArchiveEntry();
                }
            }
        } catch (IOException e) {
            throw new SkillBuildException("writing " + jar + " failed: " + e, e);
        }
    }

    private static void add(OciImageLayout layout, Map<String, Entry> entries) throws SkillBuildException {
        String location = layout.directory().toString();
        String name = SkillJar.name(layout.manifest(), location);
        Instant created = SkillJar.created(layout.manifest(), location);
        if (entries.containsKey(name + "/")) {
            throw new SkillBuildException("the skill " + name + " of " + location + " is already contained in the jar"
                    + " - skill names have to be unique");
        }
        entries.put(name + "/", new Entry(name + "/", TarLayerBuilder.DIRECTORY_MODE, created, null));
        Path layer = layout.blob(SkillJar.layer(layout.manifest(), location));
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(new BufferedInputStream(
                Files.newInputStream(layer))))) {
            for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                String path = name + "/" + entry.getName();
                if (entry.isDirectory()) {
                    String directory = path.endsWith("/") ? path : path + "/";
                    entries.put(directory, new Entry(directory, entry.getMode() & 0777, created, null));
                } else if (entry.isFile()) {
                    entries.put(path, new Entry(path, entry.getMode() & 0777, created, tar.readAllBytes()));
                } else {
                    throw new SkillBuildException("the layer of " + location + " contains " + entry.getName()
                            + ", which is neither a regular file nor a directory");
                }
            }
            entries.put(SkillJar.metadataPath(name, SkillJar.MANIFEST_FILE), new Entry(
                    SkillJar.metadataPath(name, SkillJar.MANIFEST_FILE), TarLayerBuilder.FILE_MODE, created,
                    Files.readAllBytes(layout.blob(layout.manifestDescriptor()))));
            entries.put(SkillJar.metadataPath(name, SkillJar.CONFIG_FILE), new Entry(
                    SkillJar.metadataPath(name, SkillJar.CONFIG_FILE), TarLayerBuilder.FILE_MODE, created,
                    Files.readAllBytes(layout.blob(layout.manifest().config()))));
        } catch (IOException e) {
            throw new SkillBuildException("reading the image " + location + " failed: " + e, e);
        }
    }

    /**
     * DOS times are local times without zone and with a precision of two seconds. The time in UTC is stored, so the
     * jar does not depend on the time zone of the build.
     */
    private static long dosTime(Instant time) {
        LocalDateTime utc = LocalDateTime.ofInstant(time, ZoneOffset.UTC).withNano(0);
        return utc.withSecond(utc.getSecond() / 2 * 2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

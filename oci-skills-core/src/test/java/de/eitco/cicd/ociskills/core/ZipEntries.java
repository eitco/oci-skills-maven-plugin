package de.eitco.cicd.ociskills.core;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads and writes the entries of zip files in tests.
 *
 * @see #read(Path)
 */
public final class ZipEntries {

    /**
     * @param mode            the unix permissions without file type
     * @param time            the modification time as stored - the DOS time is a local time without zone
     * @param extraFieldCount the number of extra fields of the local and central header
     * @param content         {@code null} for directories
     */
    public record Entry(String name, int mode, LocalDateTime time, int extraFieldCount, byte[] content) {

        public Entry withContent(byte[] newContent) {
            return new Entry(name, mode, time, extraFieldCount, newContent);
        }
    }

    private ZipEntries() {
    }

    /**
     * @return the entries in the order they are stored
     */
    public static List<Entry> read(Path zip) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (ZipFile file = ZipFile.builder().setPath(zip).get()) {
            for (ZipArchiveEntry entry : Collections.list(file.getEntriesInPhysicalOrder())) {
                byte[] content = null;
                if (!entry.isDirectory()) {
                    try (InputStream in = file.getInputStream(entry)) {
                        content = in.readAllBytes();
                    }
                }
                entries.add(new Entry(entry.getName(), entry.getUnixMode() & 0777,
                        entry.getLastModifiedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                        entry.getExtraFields(true).length, content));
            }
        }
        return entries;
    }

    public static List<String> names(Path zip) throws IOException {
        return read(zip).stream().map(Entry::name).toList();
    }

    /**
     * Writes the entries with their names, modes and contents.
     */
    public static void write(Path zip, List<Entry> entries) throws IOException {
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zip)) {
            for (Entry entry : entries) {
                ZipArchiveEntry zipEntry = new ZipArchiveEntry(entry.name());
                zipEntry.setUnixMode(entry.mode());
                out.putArchiveEntry(zipEntry);
                if (entry.content() != null) {
                    out.write(entry.content());
                }
                out.closeArchiveEntry();
            }
        }
    }
}

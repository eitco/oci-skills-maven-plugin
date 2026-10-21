package de.eitco.cicd.ociskills.core;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Reads the entries of a gzip compressed tar layer in tests.
 */
public final class TarEntries {

    public record Entry(String name, boolean directory, int mode, long uid, long gid, String userName,
                        String groupName, long modTimeMillis, String content) {
    }

    private TarEntries() {
    }

    public static List<Entry> read(Path gzipTar) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (InputStream file = Files.newInputStream(gzipTar);
             TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(file))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String content = entry.isDirectory() ? null : new String(tar.readAllBytes(), UTF_8);
                entries.add(new Entry(entry.getName(), entry.isDirectory(), entry.getMode() & 07777,
                        entry.getLongUserId(), entry.getLongGroupId(), entry.getUserName(), entry.getGroupName(),
                        entry.getModTime().getTime(), content));
            }
        }
        return entries;
    }
}

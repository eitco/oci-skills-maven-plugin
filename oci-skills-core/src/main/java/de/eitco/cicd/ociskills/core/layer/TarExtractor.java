package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.SkillBuildException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Extracts an uncompressed tar stream - e.g. a skill layer or an archive written by {@link TarArchiver} - into a
 * directory. Only regular files and directories are supported. Entries pointing out of the target directory are
 * rejected.
 * <p>
 * Paths are read from the ustar header, a preceding PAX extended header or a GNU long name entry. Files with any
 * executable bit are made executable, if the file system supports POSIX permissions.
 */
public class TarExtractor {

    private static final int BLOCK_SIZE = 512;

    private static final byte TYPE_FILE = '0';
    private static final byte TYPE_FILE_OLD = 0;
    private static final byte TYPE_DIRECTORY = '5';
    private static final byte TYPE_PAX_HEADER = 'x';
    private static final byte TYPE_PAX_GLOBAL_HEADER = 'g';
    private static final byte TYPE_GNU_LONG_NAME = 'L';

    /**
     * @param tar    the uncompressed tar stream - not closed
     * @param target the directory to extract into - created if missing
     */
    public void extract(InputStream tar, Path target) throws SkillBuildException {
        Path root = target.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            String pendingPath = null;
            byte[] header = new byte[BLOCK_SIZE];
            while (readBlock(tar, header)) {
                if (isZero(header)) {
                    return;
                }
                byte type = header[156];
                long size = octal(header, 124, 12);
                String path = pendingPath != null ? pendingPath : headerPath(header);
                pendingPath = null;
                switch (type) {
                    case TYPE_PAX_HEADER -> pendingPath = paxPath(readContent(tar, size));
                    case TYPE_GNU_LONG_NAME -> pendingPath = cString(readContent(tar, size), 0, (int) size);
                    case TYPE_PAX_GLOBAL_HEADER -> readContent(tar, size);
                    case TYPE_DIRECTORY -> Files.createDirectories(resolve(root, path));
                    case TYPE_FILE, TYPE_FILE_OLD -> writeFile(tar, resolve(root, path), size,
                            (octal(header, 100, 8) & 0111) != 0);
                    default -> throw new SkillBuildException("tar entry " + path + " has the unsupported type '"
                            + (char) type + "' - only regular files and directories are supported");
                }
            }
        } catch (IOException e) {
            throw new SkillBuildException("extracting into " + target + " failed: " + e, e);
        }
    }

    private static Path resolve(Path root, String path) throws SkillBuildException {
        if (path.isEmpty() || path.startsWith("/")) {
            throw new SkillBuildException("tar entry '" + path + "' is not a relative path");
        }
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new SkillBuildException("tar entry " + path + " points out of the target directory");
        }
        return resolved;
    }

    private static void writeFile(InputStream tar, Path file, long size, boolean executable) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = size;
            while (remaining > 0) {
                int read = tar.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("unexpected end of tar stream in " + file.getFileName());
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
        skipPadding(tar, size);
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(executable ? "rwxr-xr-x" : "rw-r--r--"));
        }
    }

    private static byte[] readContent(InputStream tar, long size) throws IOException {
        if (size > Integer.MAX_VALUE) {
            throw new IOException("tar header entry of " + size + " bytes is too large");
        }
        byte[] content = tar.readNBytes((int) size);
        if (content.length != size) {
            throw new IOException("unexpected end of tar stream");
        }
        skipPadding(tar, size);
        return content;
    }

    private static void skipPadding(InputStream tar, long size) throws IOException {
        int remainder = (int) (size % BLOCK_SIZE);
        if (remainder != 0) {
            tar.skipNBytes(BLOCK_SIZE - remainder);
        }
    }

    /**
     * @return {@code false} at the end of the stream
     */
    private static boolean readBlock(InputStream tar, byte[] block) throws IOException {
        int read = tar.readNBytes(block, 0, BLOCK_SIZE);
        if (read == 0) {
            return false;
        }
        if (read != BLOCK_SIZE) {
            throw new IOException("unexpected end of tar stream");
        }
        return true;
    }

    private static boolean isZero(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String headerPath(byte[] header) {
        String name = cString(header, 0, 100);
        boolean ustar = cString(header, 257, 6).startsWith("ustar");
        String prefix = ustar ? cString(header, 345, 155) : "";
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    /**
     * Reads the {@code path} record of a PAX extended header - records have the form
     * {@code "<length> <key>=<value>\n"}.
     */
    private static String paxPath(byte[] content) throws IOException {
        String path = null;
        int offset = 0;
        while (offset < content.length) {
            int space = offset;
            while (space < content.length && content[space] != ' ') {
                space++;
            }
            int length;
            try {
                length = Integer.parseInt(new String(content, offset, space - offset, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                throw new IOException("invalid PAX header record");
            }
            if (length <= 0 || offset + length > content.length) {
                throw new IOException("invalid PAX header record");
            }
            String record = new String(content, space + 1, offset + length - space - 2, StandardCharsets.UTF_8);
            int equals = record.indexOf('=');
            if (equals > 0 && record.substring(0, equals).equals("path")) {
                path = record.substring(equals + 1);
            }
            offset += length;
        }
        return path;
    }

    private static String cString(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] header, int offset, int length) throws IOException {
        String value = cString(header, offset, length).trim();
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(value, 8);
        } catch (NumberFormatException e) {
            throw new IOException("invalid octal number '" + value + "' in tar header");
        }
    }
}

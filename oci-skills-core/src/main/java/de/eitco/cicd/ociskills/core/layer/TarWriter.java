package de.eitco.cicd.ociskills.core.layer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

/**
 * A minimal, fully deterministic tar writer producing POSIX ustar entries. Paths that do not fit into the ustar
 * header or contain non ASCII characters are written using a preceding PAX extended header.
 * <p>
 * A custom writer is used, because common tar libraries fill in the name of the current user for internal
 * entries, which breaks reproducibility.
 */
final class TarWriter {

    private static final int BLOCK_SIZE = 512;
    private static final int NAME_LENGTH = 100;
    private static final int PREFIX_LENGTH = 155;
    private static final long MAX_OCTAL_SIZE = 077777777777L;

    private static final byte TYPE_FILE = '0';
    private static final byte TYPE_DIRECTORY = '5';
    private static final byte TYPE_PAX_HEADER = 'x';

    private final OutputStream out;
    private final long modificationTime;

    TarWriter(OutputStream out, Instant modificationTime) {
        if (modificationTime.getEpochSecond() < 0) {
            throw new IllegalArgumentException("timestamp " + modificationTime + " is before 1970-01-01");
        }
        this.out = out;
        this.modificationTime = modificationTime.getEpochSecond();
    }

    void writeDirectory(String path, int mode) throws IOException {
        writeHeader(path, TYPE_DIRECTORY, mode, 0);
    }

    void writeFile(String path, int mode, long size, InputStream content) throws IOException {
        writeHeader(path, TYPE_FILE, mode, size);
        long written = content.transferTo(out);
        if (written != size) {
            throw new IOException("size of " + path + " changed while writing - expected " + size + " bytes but read "
                    + written);
        }
        pad(size);
    }

    /**
     * Writes the end of archive marker - two empty blocks.
     */
    void finish() throws IOException {
        out.write(new byte[2 * BLOCK_SIZE]);
    }

    private void writeHeader(String path, byte type, int mode, long size) throws IOException {
        if (size > MAX_OCTAL_SIZE) {
            throw new IOException(path + " is too large for a tar entry (" + size + " bytes)");
        }
        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        String[] split = splitUstarPath(path, pathBytes);
        if (split == null) {
            writePaxHeader(path);
            split = new String[]{"", truncateAscii(path, NAME_LENGTH)};
        }
        out.write(header(split[0], split[1], type, mode, size));
    }

    private void writePaxHeader(String path) throws IOException {
        byte[] record = paxRecord("path", path);
        String baseName = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        out.write(header("", truncateAscii("PaxHeaders/" + baseName, NAME_LENGTH), TYPE_PAX_HEADER, 0644,
                record.length));
        out.write(record);
        pad(record.length);
    }

    /**
     * A PAX record has the form {@code "<length> <key>=<value>\n"} where length includes itself.
     */
    private static byte[] paxRecord(String key, String value) {
        int payload = (" " + key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8).length;
        int length = payload + Integer.toString(payload).length();
        if (Integer.toString(length).length() != Integer.toString(payload).length()) {
            length = payload + Integer.toString(length).length();
        }
        return (length + " " + key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return {@code [prefix, name]} if the path can be represented in a ustar header, {@code null} otherwise
     */
    private static String[] splitUstarPath(String path, byte[] pathBytes) {
        if (pathBytes.length != path.length()) {
            return null;
        }
        if (pathBytes.length <= NAME_LENGTH) {
            return new String[]{"", path};
        }
        int searchFrom = path.endsWith("/") ? path.length() - 2 : path.length() - 1;
        for (int index = path.lastIndexOf('/', searchFrom); index > 0; index = path.lastIndexOf('/', index - 1)) {
            String prefix = path.substring(0, index);
            String name = path.substring(index + 1);
            if (prefix.length() <= PREFIX_LENGTH && name.length() <= NAME_LENGTH && !name.isEmpty()) {
                return new String[]{prefix, name};
            }
        }
        return null;
    }

    private static String truncateAscii(String value, int maxLength) {
        StringBuilder ascii = new StringBuilder();
        for (char character : value.toCharArray()) {
            ascii.append(character < 0x80 ? character : '_');
        }
        return ascii.length() <= maxLength ? ascii.toString() : ascii.substring(0, maxLength);
    }

    private byte[] header(String prefix, String name, byte type, int mode, long size) {
        byte[] header = new byte[BLOCK_SIZE];
        put(header, 0, NAME_LENGTH, name.getBytes(StandardCharsets.US_ASCII));
        putOctal(header, 100, 8, mode);
        putOctal(header, 108, 8, 0);
        putOctal(header, 116, 8, 0);
        putOctal(header, 124, 12, size);
        putOctal(header, 136, 12, modificationTime);
        Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = type;
        put(header, 257, 6, "ustar\0".getBytes(StandardCharsets.US_ASCII));
        put(header, 263, 2, "00".getBytes(StandardCharsets.US_ASCII));
        putOctal(header, 329, 8, 0);
        putOctal(header, 337, 8, 0);
        put(header, 345, PREFIX_LENGTH, prefix.getBytes(StandardCharsets.US_ASCII));

        long checksum = 0;
        for (byte value : header) {
            checksum += value & 0xff;
        }
        byte[] checksumField = String.format("%06o", checksum).getBytes(StandardCharsets.US_ASCII);
        put(header, 148, 6, checksumField);
        header[154] = 0;
        header[155] = ' ';
        return header;
    }

    private static void put(byte[] header, int offset, int length, byte[] value) {
        System.arraycopy(value, 0, header, offset, Math.min(length, value.length));
    }

    /**
     * Writes a zero padded octal number terminated by NUL.
     */
    private static void putOctal(byte[] header, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        String padded = "0".repeat(length - 1 - octal.length()) + octal;
        put(header, offset, length - 1, padded.getBytes(StandardCharsets.US_ASCII));
        header[offset + length - 1] = 0;
    }

    private void pad(long size) throws IOException {
        int remainder = (int) (size % BLOCK_SIZE);
        if (remainder != 0) {
            out.write(new byte[BLOCK_SIZE - remainder]);
        }
    }
}

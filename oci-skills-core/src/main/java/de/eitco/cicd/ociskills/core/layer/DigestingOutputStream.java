package de.eitco.cicd.ociskills.core.layer;

import de.eitco.cicd.ociskills.core.oci.Digest;
import de.eitco.cicd.ociskills.core.oci.OciJson;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * Computes the SHA-256 digest and the size of all bytes passing through.
 */
final class DigestingOutputStream extends FilterOutputStream {

    private final MessageDigest messageDigest = OciJson.sha256();
    private long size;

    DigestingOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int value) throws IOException {
        out.write(value);
        messageDigest.update((byte) value);
        size++;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        out.write(bytes, offset, length);
        messageDigest.update(bytes, offset, length);
        size += length;
    }

    Digest digest() {
        return Digest.sha256(messageDigest.digest());
    }

    long size() {
        return size;
    }
}

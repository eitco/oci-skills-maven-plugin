package de.eitco.cicd.ociskills.core.registry;

import java.nio.file.Path;
import java.util.List;

/**
 * Transport settings for the connection to a registry. TLS verification is always enabled - additional trusted
 * certificate authorities can be configured explicitly, plain http must be allowed explicitly.
 *
 * @param caCertificates     PEM files of additional trusted certificate authorities
 * @param trustStore         a trust store of additional trusted certificate authorities - may be {@code null}
 * @param trustStorePassword the password of the trust store - may be {@code null}
 * @param trustStoreType     the type of the trust store, e.g. {@code PKCS12} - {@code null} for the JVM default
 * @param allowPlainHttp     whether to connect via plain http instead of https
 */
public record RegistryConnection(List<Path> caCertificates, Path trustStore, String trustStorePassword,
                                 String trustStoreType, boolean allowPlainHttp) {

    public RegistryConnection {
        caCertificates = caCertificates == null ? List.of() : List.copyOf(caCertificates);
    }

    public static RegistryConnection defaults() {
        return new RegistryConnection(List.of(), null, null, null, false);
    }

    @Override
    public String toString() {
        return "RegistryConnection[caCertificates=" + caCertificates + ", trustStore=" + trustStore
                + ", trustStoreType=" + trustStoreType + ", allowPlainHttp=" + allowPlainHttp + "]";
    }
}

package de.eitco.cicd.ociskills.registry.oras;

import de.eitco.cicd.ociskills.core.SkillPushException;
import de.eitco.cicd.ociskills.core.registry.RegistryConnection;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Builds the PEM bundle of trusted certificate authorities handed to ORAS.
 */
final class TrustMaterial {

    private TrustMaterial() {
    }

    /**
     * @return {@code null} if no additional certificate authorities are configured - the JVM defaults apply then.
     * Otherwise the JVM default certificate authorities plus the configured ones, since ORAS replaces the default
     * trust store with the given certificates.
     */
    static String pemBundle(RegistryConnection connection) throws SkillPushException {
        if (connection.caCertificates().isEmpty() && connection.trustStore() == null) {
            return null;
        }
        List<X509Certificate> certificates = new ArrayList<>(defaultCertificateAuthorities());
        for (Path pemFile : connection.caCertificates()) {
            certificates.addAll(readPem(pemFile));
        }
        if (connection.trustStore() != null) {
            certificates.addAll(readTrustStore(connection));
        }
        StringBuilder bundle = new StringBuilder();
        for (X509Certificate certificate : certificates) {
            bundle.append(toPem(certificate));
        }
        return bundle.toString();
    }

    private static List<X509Certificate> defaultCertificateAuthorities() throws SkillPushException {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            List<X509Certificate> certificates = new ArrayList<>();
            for (TrustManager trustManager : factory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager x509) {
                    Collections.addAll(certificates, x509.getAcceptedIssuers());
                }
            }
            return certificates;
        } catch (GeneralSecurityException e) {
            throw configurationError("default trust store of the JVM cannot be loaded: " + e.getMessage(), e);
        }
    }

    private static List<X509Certificate> readPem(Path pemFile) throws SkillPushException {
        try (InputStream in = Files.newInputStream(pemFile)) {
            Collection<? extends Certificate> certificates =
                    CertificateFactory.getInstance("X.509").generateCertificates(in);
            if (certificates.isEmpty()) {
                throw configurationError("CA certificate file " + pemFile + " contains no certificates", null);
            }
            return certificates.stream().map(X509Certificate.class::cast).toList();
        } catch (IOException | GeneralSecurityException e) {
            throw configurationError("CA certificate file " + pemFile + " cannot be read: " + e, e);
        }
    }

    private static List<X509Certificate> readTrustStore(RegistryConnection connection) throws SkillPushException {
        Path file = connection.trustStore();
        String type = connection.trustStoreType() == null ? KeyStore.getDefaultType() : connection.trustStoreType();
        char[] password = connection.trustStorePassword() == null ? null
                : connection.trustStorePassword().toCharArray();
        try (InputStream in = Files.newInputStream(file)) {
            KeyStore store = KeyStore.getInstance(type);
            store.load(in, password);
            List<X509Certificate> certificates = new ArrayList<>();
            for (String alias : Collections.list(store.aliases())) {
                if (store.getCertificate(alias) instanceof X509Certificate certificate) {
                    certificates.add(certificate);
                }
            }
            if (certificates.isEmpty()) {
                throw configurationError("trust store " + file + " contains no certificates", null);
            }
            return certificates;
        } catch (IOException | GeneralSecurityException e) {
            throw configurationError("trust store " + file + " cannot be loaded: " + e.getMessage(), null);
        }
    }

    private static String toPem(X509Certificate certificate) throws SkillPushException {
        try {
            return "-----BEGIN CERTIFICATE-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded())
                    + "\n-----END CERTIFICATE-----\n";
        } catch (CertificateEncodingException e) {
            throw configurationError("certificate " + certificate.getSubjectX500Principal() + " cannot be encoded", e);
        }
    }

    private static SkillPushException configurationError(String detail, Throwable cause) {
        return new SkillPushException(SkillPushException.Reason.INVALID_CONFIGURATION, null, null, detail, cause);
    }
}

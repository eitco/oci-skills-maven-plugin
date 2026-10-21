package de.eitco.cicd.ociskills.registry.oras;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal in-memory OCI distribution registry for tests - supporting blob checks, monolithic and two step blob
 * uploads, manifest push/pull, basic authentication and injected errors.
 */
final class FakeOciRegistry implements AutoCloseable {

    private static final Pattern BLOB = Pattern.compile("/v2/(.+)/blobs/(sha256:[a-f0-9]{64})");
    private static final Pattern UPLOAD = Pattern.compile("/v2/(.+)/blobs/uploads/([^/]*)");
    private static final Pattern MANIFEST = Pattern.compile("/v2/(.+)/manifests/([^/]+)");

    record Request(String method, String path, String authorization) {
    }

    record StoredManifest(byte[] content, String contentType) {
    }

    record InjectedError(int status, String code) {
    }

    private final HttpServer server;
    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
    private final Map<String, StoredManifest> manifests = new ConcurrentHashMap<>();
    private final Map<String, InjectedError> errors = new ConcurrentHashMap<>();
    private final List<Request> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile String username;
    private volatile String password;
    private volatile String confirmedDigestOverride;

    private FakeOciRegistry(HttpServer server) {
        this.server = server;
        server.createContext("/", this::handle);
        server.start();
    }

    static FakeOciRegistry http() throws IOException {
        return new FakeOciRegistry(HttpServer.create(new InetSocketAddress("localhost", 0), 0));
    }

    static FakeOciRegistry https(Path keyStore, String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = java.nio.file.Files.newInputStream(keyStore)) {
            store.load(in, password.toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(store, password.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        return new FakeOciRegistry(server);
    }

    /**
     * @return {@code localhost:<port>}
     */
    String address() {
        return "localhost:" + server.getAddress().getPort();
    }

    void requireCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Answers every request whose {@code "<METHOD> <path>"} matches the given regex with the given error.
     */
    void failOn(String methodAndPathRegex, int status, String code) {
        errors.put(methodAndPathRegex, new InjectedError(status, code));
    }

    void confirmDigest(String digest) {
        this.confirmedDigestOverride = digest;
    }

    void putBlob(String digest, byte[] content) {
        blobs.put(digest, content);
    }

    Map<String, byte[]> blobs() {
        return blobs;
    }

    StoredManifest manifest(String repository, String reference) {
        return manifests.get(repository + ":" + reference);
    }

    List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    List<Request> requests(String method) {
        return requests().stream().filter(request -> request.method().equals(method)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requests.add(new Request(method, path, authorization));
            byte[] body = exchange.getRequestBody().readAllBytes();

            if (username != null && !isAuthorized(authorization)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"fake\"");
                error(exchange, 401, "UNAUTHORIZED");
                return;
            }
            for (Map.Entry<String, InjectedError> error : errors.entrySet()) {
                if ((method + " " + path).matches(error.getKey())) {
                    error(exchange, error.getValue().status(), error.getValue().code());
                    return;
                }
            }
            if (path.equals("/v2/") || path.equals("/v2")) {
                respond(exchange, 200, Map.of(), new byte[0]);
                return;
            }
            Matcher blob = BLOB.matcher(path);
            Matcher upload = UPLOAD.matcher(path);
            Matcher manifest = MANIFEST.matcher(path);
            if (blob.matches()) {
                handleBlob(exchange, method, blob.group(2));
            } else if (upload.matches()) {
                handleUpload(exchange, method, upload.group(1), upload.group(2), body);
            } else if (manifest.matches()) {
                handleManifest(exchange, method, manifest.group(1), manifest.group(2), body);
            } else {
                error(exchange, 404, "NAME_UNKNOWN");
            }
        }
    }

    private boolean isAuthorized(String authorization) {
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return expected.equals(authorization);
    }

    private void handleBlob(HttpExchange exchange, String method, String digest) throws IOException {
        byte[] content = blobs.get(digest);
        if (content == null) {
            error(exchange, 404, "BLOB_UNKNOWN");
            return;
        }
        Map<String, String> headers = Map.of("Docker-Content-Digest", digest,
                "Content-Type", "application/octet-stream");
        respond(exchange, 200, headers, method.equals("HEAD") ? null : content, content.length);
    }

    private void handleUpload(HttpExchange exchange, String method, String repository, String session, byte[] body)
            throws IOException {
        String digest = queryParameter(exchange, "digest");
        if (method.equals("POST") && digest == null) {
            String location = "/v2/" + repository + "/blobs/uploads/" + UUID.randomUUID();
            respond(exchange, 202, Map.of("Location", location), new byte[0]);
            return;
        }
        if ((method.equals("POST") || method.equals("PUT")) && digest != null) {
            if (!digest.equals(sha256(body))) {
                error(exchange, 400, "DIGEST_INVALID");
                return;
            }
            blobs.put(digest, body);
            respond(exchange, 201, Map.of("Location", "/v2/" + repository + "/blobs/" + digest,
                    "Docker-Content-Digest", digest), new byte[0]);
            return;
        }
        error(exchange, 405, "UNSUPPORTED");
    }

    private void handleManifest(HttpExchange exchange, String method, String repository, String reference,
                                byte[] body) throws IOException {
        String key = repository + ":" + reference;
        if (method.equals("PUT")) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String digest = sha256(body);
            StoredManifest stored = new StoredManifest(body, contentType);
            manifests.put(key, stored);
            manifests.put(repository + ":" + digest, stored);
            respond(exchange, 201, Map.of("Docker-Content-Digest", digest,
                    "Location", "/v2/" + repository + "/manifests/" + digest), new byte[0]);
            return;
        }
        StoredManifest stored = manifests.get(key);
        if (stored == null) {
            error(exchange, 404, "MANIFEST_UNKNOWN");
            return;
        }
        String digest = confirmedDigestOverride != null ? confirmedDigestOverride : sha256(stored.content());
        respond(exchange, 200, Map.of("Docker-Content-Digest", digest, "Content-Type", stored.contentType()),
                method.equals("HEAD") ? null : stored.content(), stored.content().length);
    }

    private static String queryParameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator > 0 && parameter.substring(0, separator).equals(name)) {
                return URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void error(HttpExchange exchange, int status, String code) throws IOException {
        byte[] body = ("{\"errors\":[{\"code\":\"" + code + "\",\"message\":\"" + code.toLowerCase()
                + "\"}]}").getBytes(StandardCharsets.UTF_8);
        respond(exchange, status, Map.of("Content-Type", "application/json"), body);
    }

    private static void respond(HttpExchange exchange, int status, Map<String, String> headers, byte[] body)
            throws IOException {
        respond(exchange, status, headers, body, body.length);
    }

    private static void respond(HttpExchange exchange, int status, Map<String, String> headers, byte[] body,
                                long contentLength) throws IOException {
        headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        if (body == null) {
            exchange.getResponseHeaders().add("Content-Length", Long.toString(contentLength));
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    static String sha256(byte[] content) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

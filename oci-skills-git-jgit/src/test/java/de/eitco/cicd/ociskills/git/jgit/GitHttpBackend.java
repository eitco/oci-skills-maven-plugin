package de.eitco.cicd.ociskills.git.jgit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Serves the git repositories below a directory via the smart http protocol, by running {@code git http-backend} as
 * CGI script - clients like {@code npx skills} clone shallowly, which the dumb protocol does not support.
 */
final class GitHttpBackend implements AutoCloseable {

    private final HttpServer server;
    private final Path git;
    private final Path root;

    GitHttpBackend(Path git, Path root) throws IOException {
        this.git = git;
        this.root = root;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * @return the url of the repository with the given name below the root
     */
    String url(String repository) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + repository;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            ProcessBuilder builder = new ProcessBuilder(git.toString(), "http-backend");
            Map<String, String> environment = builder.environment();
            environment.put("GIT_PROJECT_ROOT", root.toString());
            environment.put("GIT_HTTP_EXPORT_ALL", "1");
            environment.put("REQUEST_METHOD", exchange.getRequestMethod());
            environment.put("PATH_INFO", exchange.getRequestURI().getRawPath());
            environment.put("QUERY_STRING", exchange.getRequestURI().getRawQuery() == null ? ""
                    : exchange.getRequestURI().getRawQuery());
            environment.put("CONTENT_LENGTH", Integer.toString(body.length));
            environment.put("REMOTE_ADDR", exchange.getRemoteAddress().getAddress().getHostAddress());
            header(exchange, "Content-Type", environment, "CONTENT_TYPE");
            header(exchange, "Content-Encoding", environment, "HTTP_CONTENT_ENCODING");
            header(exchange, "Git-Protocol", environment, "GIT_PROTOCOL");
            Process process = builder.redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (OutputStream in = process.getOutputStream()) {
                in.write(body);
            }
            byte[] output;
            try (InputStream out = process.getInputStream()) {
                output = out.readAllBytes();
            }
            respond(exchange, output);
        }
    }

    private static void header(HttpExchange exchange, String name, Map<String, String> environment, String variable) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value != null) {
            environment.put(variable, value);
        }
    }

    /**
     * Translates the CGI response - headers, an empty line and the body - into the http response.
     */
    private static void respond(HttpExchange exchange, byte[] output) throws IOException {
        int separator = indexOf(output, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        int bodyStart = separator + 4;
        if (separator < 0) {
            separator = indexOf(output, "\n\n".getBytes(StandardCharsets.US_ASCII));
            bodyStart = separator + 2;
        }
        if (separator < 0) {
            exchange.sendResponseHeaders(500, -1);
            return;
        }
        int status = 200;
        for (String line : new String(output, 0, separator, StandardCharsets.US_ASCII).split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (name.equalsIgnoreCase("Status")) {
                status = Integer.parseInt(value.split(" ")[0]);
            } else {
                exchange.getResponseHeaders().add(name, value);
            }
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(output, bodyStart, output.length - bodyStart);
        exchange.sendResponseHeaders(status, body.size() == 0 ? -1 : body.size());
        if (body.size() > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                body.writeTo(out);
            }
        }
    }

    private static int indexOf(byte[] bytes, byte[] pattern) {
        for (int i = 0; i + pattern.length <= bytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length && match; j++) {
                match = bytes[i + j] == pattern[j];
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }
}

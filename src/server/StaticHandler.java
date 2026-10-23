package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.file.*;

/** Serves the HTML / CSS / JS files from the web/ folder. */
public class StaticHandler implements HttpHandler {
    private final Path root;

    public StaticHandler(String folder) { this.root = Paths.get(folder).toAbsolutePath().normalize(); }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String uri = ex.getRequestURI().getPath();
        if (uri.equals("/")) uri = "/index.html";
        Path file = root.resolve(uri.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {   // blocks ../ path tricks
            byte[] msg = "404 - File not found".getBytes();
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type(file.toString()));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static String type(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}

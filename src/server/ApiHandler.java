package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import model.Person;
import service.AppService;
import util.ApiException;
import util.Json;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP LAYER for everything under /api/.
 * Steps for every request: 1) read method + path + JSON body, 2) find the logged-in user from the
 * token, 3) call the matching AppService method, 4) send the result back as JSON.
 */
public class ApiHandler implements HttpHandler {
    private final AppService service;

    public ApiHandler(AppService service) { this.service = service; }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        int status = 200;
        Object result;
        try {
            result = route(ex);
        } catch (ApiException e) {
            status = e.getStatus();
            result = error(e.getMessage());
        } catch (IllegalArgumentException | ClassCastException e) {
            status = 400;
            result = error("Invalid request data.");
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            result = error("Server error: " + e.getMessage());
        }
        byte[] bytes = Json.stringify(result).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        return m;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBody(HttpExchange ex) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        try (InputStream in = ex.getRequestBody()) {
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > 1_000_000) throw ApiException.badRequest("Request too large.");
            }
        }
        String text = buf.toString("UTF-8").trim();
        if (text.isEmpty()) return new LinkedHashMap<>();
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map)) throw ApiException.badRequest("JSON object expected.");
        return (Map<String, Object>) parsed;
    }

    private static Map<String, String> readQuery(String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null) return q;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            try {
                q.put(URLDecoder.decode(kv[0], "UTF-8"), kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "");
            } catch (UnsupportedEncodingException ignored) { }
        }
        return q;
    }

    private static boolean is(String[] p, String... pattern) {   // "*" matches any single segment
        if (p.length != pattern.length) return false;
        for (int i = 0; i < p.length; i++) if (!pattern[i].equals("*") && !pattern[i].equals(p[i])) return false;
        return true;
    }

    private Object route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath().replaceAll("^/api/?", "").replaceAll("/$", "");
        String[] p = path.isEmpty() ? new String[0] : path.split("/");
        Map<String, String> query = readQuery(ex.getRequestURI().getRawQuery());
        Map<String, Object> body = (method.equals("POST") || method.equals("PUT")) ? readBody(ex) : new HashMap<>();

        // ---- public endpoints (no login needed)
        if (method.equals("POST") && is(p, "login")) return service.login(body);
        if (method.equals("POST") && is(p, "signup")) return service.signUp(body);

        // ---- everything below needs a valid token
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        Person me = service.authenticate(token);

        if (method.equals("POST") && is(p, "logout")) { service.logout(token); return ok(); }
        if (method.equals("GET") && is(p, "me")) return service.userView(me);
        if (method.equals("GET") && is(p, "stats")) return service.stats(me);

        // events
        if (method.equals("GET") && is(p, "events")) return service.listEvents(me, query);
        if (method.equals("POST") && is(p, "events")) return service.createEvent(me, body);
        if (method.equals("PUT") && is(p, "events", "*")) return service.updateEvent(me, p[1], body);
        if (method.equals("DELETE") && is(p, "events", "*")) { service.deleteEvent(me, p[1]); return ok(); }
        if (method.equals("POST") && is(p, "events", "*", "register")) return service.register(me, p[1]);
        if (method.equals("POST") && is(p, "events", "*", "cancel")) return service.cancel(me, p[1]);
        if (method.equals("GET") && is(p, "events", "*", "registrations")) return service.eventRegistrations(me, p[1]);

        // registrations / attendance
        if (method.equals("GET") && is(p, "my", "registrations")) return service.myRegistrations(me);
        if (method.equals("GET") && is(p, "my", "participation")) return service.participation(me);
        if (method.equals("POST") && is(p, "registrations", "*", "attendance")) return service.markAttendance(me, p[1], body);

        // volunteers and duties
        if (method.equals("GET") && is(p, "volunteers")) return service.listVolunteers(me);
        if (method.equals("GET") && is(p, "duties")) return service.listDuties(me);
        if (method.equals("POST") && is(p, "duties")) return service.assignDuty(me, body);
        if (method.equals("PUT") && is(p, "duties", "*", "status")) return service.updateDutyStatus(me, p[1], body);
        if (method.equals("POST") && is(p, "duties", "*", "attendance")) return service.dutyAttendance(me, p[1], body);
        if (method.equals("DELETE") && is(p, "duties", "*")) { service.deleteDuty(me, p[1]); return ok(); }

        // admin: users
        if (method.equals("GET") && is(p, "users")) return service.listUsers(me, query);
        if (method.equals("POST") && is(p, "users")) return service.createUser(me, body);
        if (method.equals("PUT") && is(p, "users", "*")) return service.updateUser(me, p[1], body);
        if (method.equals("DELETE") && is(p, "users", "*")) { service.deleteUser(me, p[1]); return ok(); }

        throw ApiException.notFound("Unknown API endpoint: " + method + " /api/" + path);
    }
}

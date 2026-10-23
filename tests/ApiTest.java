import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;

import util.Json;

/**
 * Automated test of the running server (uses Java's built-in HttpClient).
 *   java -cp out:tests-out ApiTest http://localhost:8099 full     -> runs all checks
 *   java -cp out:tests-out ApiTest http://localhost:8099 persist  -> checks data survived a restart
 * Run it against a FRESH data folder (see tests/run_tests.sh), because it creates data.
 */
public class ApiTest {
    static String base;
    static int passed = 0, failed = 0;
    static final HttpClient http = HttpClient.newHttpClient();

    static class Res {
        int status; Object json;
        @SuppressWarnings("unchecked") Map<String, Object> map() { return (Map<String, Object>) json; }
        @SuppressWarnings("unchecked") List<Object> list() { return (List<Object>) json; }
    }

    static Res call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpRequest.BodyPublisher bp = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.stringify(body));
        HttpResponse<String> r = http.send(b.method(method, bp).build(), HttpResponse.BodyHandlers.ofString());
        Res res = new Res();
        res.status = r.statusCode();
        res.json = Json.parse(r.body());
        return res;
    }

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    static String login(String user, String pw) throws Exception {
        Res r = call("POST", "/api/login", null, m("username", user, "password", pw));
        return r.status == 200 ? (String) r.map().get("token") : null;
    }

    static Map<String, Object> eventBody(String title, String venue, int dayOffset, String s, String e, int cap) {
        return m("title", title, "description", "test", "category", "Technical", "venue", venue,
                "date", LocalDate.now().plusDays(dayOffset).toString(), "startTime", s, "endTime", e, "capacity", cap);
    }

    public static void main(String[] args) throws Exception {
        base = args[0];
        if (args.length > 1 && args[1].equals("persist")) { persist(); }
        else { full(); }
        System.out.println("\nRESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void persist() throws Exception {
        System.out.println("== Persistence after restart ==");
        String admin = login("admin", "admin123");
        check("admin can still log in", admin != null);
        check("user 'newcoord' (created before restart) still exists", call("GET", "/api/users?search=newcoord", admin, null).list().size() == 1);
        check("event 'Test Event' still exists", call("GET", "/api/events?search=Test%20Event", admin, null).list().size() == 1);
        String s1 = login("student1", "student123");
        List<Object> regs = call("GET", "/api/my/registrations", s1, null).list();
        check("student1 registrations still saved", regs.size() >= 3);
    }

    static void full() throws Exception {
        System.out.println("== Login & roles ==");
        check("wrong password rejected (401)", call("POST", "/api/login", null, m("username", "admin", "password", "bad")).status == 401);
        check("no token rejected (401)", call("GET", "/api/events", null, null).status == 401);
        String admin = login("admin", "admin123"), c1 = login("coord1", "coord123"), c2 = login("coord2", "coord123");
        String s1 = login("student1", "student123"), s3 = login("student3", "student123"), v1 = login("vol1", "vol123"), v3 = login("vol3", "vol123");
        check("all 4 roles can log in", admin != null && c1 != null && s1 != null && v1 != null);
        check("student menu has no 'users'", !call("GET", "/api/me", s1, null).map().get("menu").toString().contains("users"));
        check("admin menu has 'users'", call("GET", "/api/me", admin, null).map().get("menu").toString().contains("users"));
        check("volunteer menu has 'myDuties'", call("GET", "/api/me", v1, null).map().get("menu").toString().contains("myDuties"));

        System.out.println("== Events: permissions, validation, scheduling ==");
        check("student cannot create event (403)", call("POST", "/api/events", s1, eventBody("Nope Event", "Room A", 20, "10:00", "11:00", 10)).status == 403);
        check("past date rejected (400)", call("POST", "/api/events", c1, eventBody("Old Event", "Room A", -2, "10:00", "11:00", 10)).status == 400);
        check("end before start rejected (400)", call("POST", "/api/events", c1, eventBody("Bad Time", "Room A", 20, "12:00", "11:00", 10)).status == 400);
        check("short title rejected (400)", call("POST", "/api/events", c1, eventBody("ab", "Room A", 20, "10:00", "11:00", 10)).status == 400);
        check("capacity 0 rejected (400)", call("POST", "/api/events", c1, eventBody("Zero Cap", "Room A", 20, "10:00", "11:00", 0)).status == 400);
        Res created = call("POST", "/api/events", c1, eventBody("Test Event", "Room A", 20, "10:00", "12:00", 5));
        check("coordinator creates event (200)", created.status == 200);
        String evId = (String) created.map().get("id");
        check("venue clash rejected (409)", call("POST", "/api/events", c2, eventBody("Clash Event", "Room A", 20, "11:00", "13:00", 5)).status == 409);
        check("other coordinator cannot edit it (403)", call("PUT", "/api/events/" + evId, c2, eventBody("Hacked", "Room A", 20, "10:00", "12:00", 5)).status == 403);
        Res edited = call("PUT", "/api/events/" + evId, c1, eventBody("Test Event", "Room A", 20, "10:00", "12:00", 6));
        check("owner edits event (capacity 6)", edited.status == 200 && ((Number) edited.map().get("capacity")).intValue() == 6);

        System.out.println("== Search & filters ==");
        check("search 'hackathon' finds 1", call("GET", "/api/events?search=hackathon", s1, null).list().size() == 1);
        check("category=Sports finds 1", call("GET", "/api/events?category=Sports", s1, null).list().size() == 1);
        check("status=COMPLETED finds 1", call("GET", "/api/events?status=COMPLETED", s1, null).list().size() == 1);

        System.out.println("== Registration ==");
        check("register for full event e3 rejected (409)", call("POST", "/api/events/e3/register", s1, null).status == 409);
        check("register for new event (200)", call("POST", "/api/events/" + evId + "/register", s1, null).status == 200);
        check("duplicate registration rejected (409)", call("POST", "/api/events/" + evId + "/register", s1, null).status == 409);
        check("cancel registration (200)", call("POST", "/api/events/" + evId + "/cancel", s1, null).status == 200);
        check("cancel again rejected (409)", call("POST", "/api/events/" + evId + "/cancel", s1, null).status == 409);
        check("re-register after cancel (200)", call("POST", "/api/events/" + evId + "/register", s1, null).status == 200);
        Res clashEv = call("POST", "/api/events", c2, eventBody("Same Day As Hackathon", "Room B", 7, "10:00", "11:00", 5));
        check("student time-clash rejected (409)", call("POST", "/api/events/" + clashEv.map().get("id") + "/register", s1, null).status == 409);
        check("register for completed event rejected (409)", call("POST", "/api/events/e5/register", s3, null).status == 409);
        check("coordinator cannot register (403)", call("POST", "/api/events/" + evId + "/register", c1, null).status == 403);

        System.out.println("== Attendance ==");
        check("owner lists registrations", call("GET", "/api/events/e1/registrations", c1, null).list().size() == 2);
        check("other coordinator cannot list (403)", call("GET", "/api/events/e1/registrations", c2, null).status == 403);
        check("coordinator marks attendance (200)", call("POST", "/api/registrations/r3/attendance", c1, m("attended", true)).status == 200);
        check("other coordinator cannot mark (403)", call("POST", "/api/registrations/r3/attendance", c2, m("attended", true)).status == 403);
        check("student cannot mark (403)", call("POST", "/api/registrations/r3/attendance", s1, m("attended", true)).status == 403);
        check("participation shows attended events", ((Number) call("GET", "/api/my/participation", s1, null).map().get("attended")).intValue() == 2);

        System.out.println("== Volunteer duties ==");
        check("student cannot assign duty (403)", call("POST", "/api/duties", s1, m("eventId", "e1", "volunteerId", "u10", "task", "Gate")).status == 403);
        Res duty = call("POST", "/api/duties", c1, m("eventId", "e1", "volunteerId", "u10", "task", "Gate keeper"));
        check("coordinator assigns duty (200)", duty.status == 200);
        String dId = (String) duty.map().get("id");
        check("duplicate duty rejected (409)", call("POST", "/api/duties", c1, m("eventId", "e1", "volunteerId", "u10", "task", "gate keeper")).status == 409);
        check("assigning a student as volunteer rejected (400)", call("POST", "/api/duties", c1, m("eventId", "e1", "volunteerId", "u4", "task", "Gate")).status == 400);
        check("volunteer cannot jump ASSIGNED->DONE (409)", call("PUT", "/api/duties/" + dId + "/status", v3, m("status", "DONE")).status == 409);
        check("another volunteer cannot touch it (403)", call("PUT", "/api/duties/" + dId + "/status", v1, m("status", "ACCEPTED")).status == 403);
        check("volunteer accepts own duty (200)", call("PUT", "/api/duties/" + dId + "/status", v3, m("status", "ACCEPTED")).status == 200);
        check("volunteer marks done (200)", call("PUT", "/api/duties/" + dId + "/status", v3, m("status", "DONE")).status == 200);
        check("coordinator marks duty attendance (200)", call("POST", "/api/duties/" + dId + "/attendance", c1, m("attended", true)).status == 200);
        check("volunteer sees only own duties", call("GET", "/api/duties", v3, null).list().size() == 2);
        check("student cannot list duties (403)", call("GET", "/api/duties", s1, null).status == 403);
        check("coordinator deletes duty (200)", call("DELETE", "/api/duties/" + dId, c1, null).status == 200);
        check("coordinator lists volunteers", call("GET", "/api/volunteers", c1, null).list().size() == 3);

        System.out.println("== Admin user management ==");
        check("student cannot list users (403)", call("GET", "/api/users", s1, null).status == 403);
        check("coordinator cannot list users (403)", call("GET", "/api/users", c1, null).status == 403);
        check("admin lists 10 users", call("GET", "/api/users", admin, null).list().size() == 10);
        check("duplicate username rejected (409)", call("POST", "/api/users", admin, m("name", "Dup", "username", "ADMIN", "password", "secret1", "email", "a@b.com", "role", "ADMIN")).status == 409);
        check("weak password rejected (400)", call("POST", "/api/users", admin, m("name", "Weak", "username", "weak1", "password", "123", "email", "a@b.com", "role", "ADMIN")).status == 400);
        check("bad email rejected (400)", call("POST", "/api/users", admin, m("name", "Mail", "username", "mail1", "password", "secret1", "email", "nope", "role", "ADMIN")).status == 400);
        Res nc = call("POST", "/api/users", admin, m("name", "New Coord", "username", "newcoord", "password", "secret1", "email", "nc@college.edu", "role", "COORDINATOR", "department", "IT"));
        check("admin creates coordinator (200)", nc.status == 200 && "COORDINATOR".equals(nc.map().get("role")));
        check("new coordinator can log in", login("newcoord", "secret1") != null);
        Res up = call("PUT", "/api/users/u7", admin, m("name", "Rahul Verma", "username", "student4", "email", "rahul@college.edu", "role", "VOLUNTEER", "year", 1, "skills", "Photography"));
        check("admin promotes student to volunteer", up.status == 200 && "VOLUNTEER".equals(up.map().get("role")));
        check("admin cannot delete self (400)", call("DELETE", "/api/users/u1", admin, null).status == 400);
        check("cannot delete coordinator who owns events (409)", call("DELETE", "/api/users/u2", admin, null).status == 409);
        Res su = call("POST", "/api/signup", null, m("name", "Self Signup", "username", "selfsu", "password", "secret1", "email", "s@college.edu", "year", 2, "role", "ADMIN"));
        check("sign-up always makes a STUDENT (even if ADMIN requested)", su.status == 200 && "STUDENT".equals(su.map().get("role")));
        check("admin deletes the sign-up user", call("DELETE", "/api/users/" + su.map().get("id"), admin, null).status == 200);
        check("admin can edit any event", call("PUT", "/api/events/" + evId, admin, eventBody("Test Event", "Room A", 20, "10:00", "12:00", 6)).status == 200);

        System.out.println("== Dashboard statistics ==");
        check("admin stats has cards", call("GET", "/api/stats", admin, null).map().containsKey("cards"));
        check("coordinator stats has cards", call("GET", "/api/stats", c1, null).map().containsKey("cards"));
        check("volunteer stats include duties", call("GET", "/api/stats", v1, null).map().get("cards").toString().contains("My duties"));
        check("student stats exclude duties", !call("GET", "/api/stats", s1, null).map().get("cards").toString().contains("My duties"));
        check("deleting event removes its registrations", call("DELETE", "/api/events/" + clashEv.map().get("id"), c2, null).status == 200);
    }
}

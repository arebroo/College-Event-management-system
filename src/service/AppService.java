package service;

import model.*;
import util.ApiException;
import util.Hash;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * SERVICE LAYER: all business rules, validations and permission checks live here.
 * The HTTP layer (server package) only translates requests into calls to these methods.
 * Methods are "synchronized" so two browser requests can never change data at the same time.
 */
public class AppService {
    private final DataStore ds;
    private final Map<String, String> sessions = new HashMap<>();   // token -> userId

    static final List<String> CATEGORIES = Arrays.asList("Technical", "Cultural", "Sports", "Workshop", "Seminar", "Social");
    static final List<String> ROLES = Arrays.asList("STUDENT", "VOLUNTEER", "COORDINATOR", "ADMIN");
    static final List<String> DUTY_STATUSES = Arrays.asList("ASSIGNED", "ACCEPTED", "DECLINED", "DONE");

    public AppService(DataStore ds) { this.ds = ds; }

    // ---------------------------------------------------------------- helpers
    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String s(Map<String, Object> in, String key) {
        Object v = in.get(key);
        return v == null ? "" : v.toString().trim();
    }

    private static int toInt(Object v) {
        try { return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString().trim()); }
        catch (Exception e) { return -1; }
    }

    private void require(Person p, String permission) {
        if (!p.can(permission))
            throw ApiException.forbidden("Your role (" + p.getRole() + ") is not allowed to do this.");
    }

    private Event findEvent(String id) {
        for (Event e : ds.events) if (e.getId().equals(id)) return e;
        throw ApiException.notFound("Event not found.");
    }

    private Person findUser(String id) {
        Person p = ds.users.get(id);
        if (p == null) throw ApiException.notFound("User not found.");
        return p;
    }

    private boolean owns(Person p, Event e) { return p instanceof Admin || e.coordinatorId.equals(p.getId()); }

    private void requireOwner(Person p, Event e) {
        if (!owns(p, e)) throw ApiException.forbidden("You can only manage events that you coordinate.");
    }

    private int activeCount(String eventId) {
        int n = 0;
        for (Registration r : ds.registrations) if (r.eventId.equals(eventId) && r.status.equals("REGISTERED")) n++;
        return n;
    }

    private Registration findReg(String eventId, String studentId) {
        for (Registration r : ds.registrations)
            if (r.eventId.equals(eventId) && r.studentId.equals(studentId)) return r;
        return null;
    }

    private String userName(String id) {
        Person p = ds.users.get(id);
        return p == null ? "(deleted user)" : p.getName();
    }

    // ---------------------------------------------------------------- authentication
    public synchronized Map<String, Object> login(Map<String, Object> in) {
        String username = s(in, "username"), password = s(in, "password");
        if (username.isEmpty() || password.isEmpty()) throw ApiException.badRequest("Enter username and password.");
        for (Person p : ds.users.values()) {
            if (p.getUsername().equalsIgnoreCase(username) && p.getPasswordHash().equals(Hash.sha256(password))) {
                String token = UUID.randomUUID().toString();
                sessions.put(token, p.getId());
                return map("token", token, "user", userView(p));
            }
        }
        throw ApiException.unauthorized("Wrong username or password.");
    }

    public synchronized Person authenticate(String token) {
        String uid = token == null ? null : sessions.get(token);
        Person p = uid == null ? null : ds.users.get(uid);
        if (p == null) throw ApiException.unauthorized("Please log in.");
        return p;
    }

    public synchronized void logout(String token) { sessions.remove(token); }

    public Map<String, Object> userView(Person p) {
        Map<String, Object> m = p.toPublicMap();
        m.put("dashboardTitle", p.getDashboardTitle());   // polymorphism: differs per role
        m.put("menu", p.getMenu());
        m.put("permissions", new TreeSet<>(p.getPermissions()));
        return m;
    }

    // ---------------------------------------------------------------- events
    private Map<String, Object> eventView(Event e, Person viewer) {
        Map<String, Object> m = e.toMap();
        m.put("registeredCount", activeCount(e.getId()));
        m.put("coordinatorName", userName(e.coordinatorId));
        int vols = 0;
        for (Duty d : ds.duties) if (d.eventId.equals(e.getId()) && !d.status.equals("DECLINED")) vols++;
        m.put("volunteerCount", vols);
        m.put("canManage", viewer.can("MANAGE_EVENTS") && owns(viewer, e));
        if (viewer.can("REGISTER_EVENT")) {
            Registration r = findReg(e.getId(), viewer.getId());
            m.put("myStatus", r == null ? "NONE" : r.status);
        }
        return m;
    }

    public synchronized List<Object> listEvents(Person actor, Map<String, String> q) {
        require(actor, "VIEW_EVENTS");
        String search = q.getOrDefault("search", "").toLowerCase();
        String category = q.getOrDefault("category", "");
        String status = q.getOrDefault("status", "");
        boolean mine = "true".equals(q.get("mine")) && !(actor instanceof Admin);
        List<Event> list = new ArrayList<>();
        for (Event e : ds.events) {
            if (!search.isEmpty() && !(e.title + " " + e.description + " " + e.venue).toLowerCase().contains(search)) continue;
            if (!category.isEmpty() && !e.category.equals(category)) continue;
            if (!status.isEmpty() && !e.status.equals(status)) continue;
            if (mine && !e.coordinatorId.equals(actor.getId())) continue;
            list.add(e);
        }
        list.sort(Comparator.comparing((Event e) -> e.date).thenComparing(e -> e.startTime));
        List<Object> out = new ArrayList<>();
        for (Event e : list) out.add(eventView(e, actor));
        return out;
    }

    /** Checks every field of an event and returns clean values. Throws 400/409 on bad input. */
    private Map<String, Object> cleanEvent(Map<String, Object> in, String excludeId, boolean isNew) {
        String title = s(in, "title"), desc = s(in, "description"), cat = s(in, "category"), venue = s(in, "venue");
        if (title.length() < 3 || title.length() > 80) throw ApiException.badRequest("Title must be 3-80 characters.");
        if (desc.length() > 500) throw ApiException.badRequest("Description can be at most 500 characters.");
        if (!CATEGORIES.contains(cat)) throw ApiException.badRequest("Choose a valid category.");
        if (venue.length() < 2 || venue.length() > 60) throw ApiException.badRequest("Venue must be 2-60 characters.");
        LocalDate date;
        LocalTime start, end;
        try {
            date = LocalDate.parse(s(in, "date"));
            start = LocalTime.parse(s(in, "startTime"));
            end = LocalTime.parse(s(in, "endTime"));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Enter a valid date (yyyy-MM-dd) and times (HH:mm).");
        }
        if (isNew && date.isBefore(LocalDate.now())) throw ApiException.badRequest("Event date cannot be in the past.");
        if (!end.isAfter(start)) throw ApiException.badRequest("End time must be after start time.");
        int cap = toInt(in.get("capacity"));
        if (cap < 1 || cap > 5000) throw ApiException.badRequest("Capacity must be between 1 and 5000.");

        Map<String, Object> clean = map("title", title, "description", desc, "category", cat, "venue", venue,
                "date", date.toString(), "startTime", start.toString(), "endTime", end.toString(), "capacity", cap);
        // scheduling: the same venue cannot host two upcoming events at overlapping times
        Event candidate = new Event(clean);
        for (Event other : ds.events) {
            if (other.getId().equals(excludeId) || !other.status.equals("UPCOMING")) continue;
            if (other.venue.equalsIgnoreCase(venue) && other.overlapsWith(candidate))
                throw ApiException.conflict("Venue is already booked for '" + other.title + "' at that time.");
        }
        return clean;
    }

    public synchronized Map<String, Object> createEvent(Person actor, Map<String, Object> in) {
        require(actor, "MANAGE_EVENTS");
        Map<String, Object> clean = cleanEvent(in, null, true);
        String coord = actor.getId();
        if (actor instanceof Admin && !s(in, "coordinatorId").isEmpty()) {
            Person c = findUser(s(in, "coordinatorId"));
            if (!(c instanceof Coordinator) && !(c instanceof Admin)) throw ApiException.badRequest("Selected user is not a coordinator.");
            coord = c.getId();
        }
        clean.put("id", ds.newId("e", ds.events));
        clean.put("coordinatorId", coord);
        clean.put("status", "UPCOMING");
        Event e = new Event(clean);
        ds.events.add(e);
        ds.saveEvents();
        return eventView(e, actor);
    }

    public synchronized Map<String, Object> updateEvent(Person actor, String id, Map<String, Object> in) {
        require(actor, "MANAGE_EVENTS");
        Event e = findEvent(id);
        requireOwner(actor, e);
        Map<String, Object> clean = cleanEvent(in, id, false);
        String status = s(in, "status").isEmpty() ? e.status : s(in, "status");
        if (!Arrays.asList("UPCOMING", "COMPLETED", "CANCELLED").contains(status))
            throw ApiException.badRequest("Invalid status.");
        if ((int) clean.get("capacity") < activeCount(id))
            throw ApiException.badRequest("Capacity cannot be less than the " + activeCount(id) + " students already registered.");
        e.title = (String) clean.get("title");
        e.description = (String) clean.get("description");
        e.category = (String) clean.get("category");
        e.venue = (String) clean.get("venue");
        e.date = (String) clean.get("date");
        e.startTime = (String) clean.get("startTime");
        e.endTime = (String) clean.get("endTime");
        e.capacity = (int) clean.get("capacity");
        e.status = status;
        if (actor instanceof Admin && !s(in, "coordinatorId").isEmpty()) {
            Person c = findUser(s(in, "coordinatorId"));
            if (!(c instanceof Coordinator) && !(c instanceof Admin)) throw ApiException.badRequest("Selected user is not a coordinator.");
            e.coordinatorId = c.getId();
        }
        ds.saveEvents();
        return eventView(e, actor);
    }

    public synchronized void deleteEvent(Person actor, String id) {
        require(actor, "MANAGE_EVENTS");
        Event e = findEvent(id);
        requireOwner(actor, e);
        ds.events.remove(e);
        ds.registrations.removeIf(r -> r.eventId.equals(id));
        ds.duties.removeIf(d -> d.eventId.equals(id));
        ds.saveEvents();
        ds.saveRegistrations();
        ds.saveDuties();
    }

    // ---------------------------------------------------------------- registrations
    public synchronized Map<String, Object> register(Person actor, String eventId) {
        require(actor, "REGISTER_EVENT");
        Event e = findEvent(eventId);
        if (!e.status.equals("UPCOMING")) throw ApiException.conflict("This event is " + e.status.toLowerCase() + " - registration is closed.");
        if (LocalDate.parse(e.date).isBefore(LocalDate.now())) throw ApiException.conflict("This event date has passed.");
        Registration existing = findReg(eventId, actor.getId());
        if (existing != null && existing.status.equals("REGISTERED")) throw ApiException.conflict("You are already registered.");
        if (activeCount(eventId) >= e.capacity) throw ApiException.conflict("Sorry, this event is full.");
        for (Registration r : ds.registrations) {
            if (!r.studentId.equals(actor.getId()) || !r.status.equals("REGISTERED")) continue;
            Event other = findEvent(r.eventId);
            if (other.overlapsWith(e))
                throw ApiException.conflict("Time clash with your registration for '" + other.title + "'.");
        }
        if (existing != null) {
            existing.status = "REGISTERED";
            existing.attended = false;
        } else {
            ds.registrations.add(new Registration(map("id", ds.newId("r", ds.registrations), "eventId", eventId,
                    "studentId", actor.getId(), "status", "REGISTERED", "attended", false)));
        }
        ds.saveRegistrations();
        return eventView(e, actor);
    }

    public synchronized Map<String, Object> cancel(Person actor, String eventId) {
        require(actor, "REGISTER_EVENT");
        Event e = findEvent(eventId);
        Registration r = findReg(eventId, actor.getId());
        if (r == null || !r.status.equals("REGISTERED")) throw ApiException.conflict("You are not registered for this event.");
        if (e.status.equals("COMPLETED") || r.attended) throw ApiException.conflict("Cannot cancel - the event is already over.");
        r.status = "CANCELLED";
        ds.saveRegistrations();
        return eventView(e, actor);
    }

    public synchronized List<Object> myRegistrations(Person actor) {
        require(actor, "REGISTER_EVENT");
        List<Object> out = new ArrayList<>();
        for (Registration r : ds.registrations) {
            if (!r.studentId.equals(actor.getId())) continue;
            Event e = findEvent(r.eventId);
            Map<String, Object> m = r.toMap();
            m.put("event", eventView(e, actor));
            out.add(m);
        }
        return out;
    }

    public synchronized List<Object> eventRegistrations(Person actor, String eventId) {
        require(actor, "MANAGE_EVENTS");
        Event e = findEvent(eventId);
        requireOwner(actor, e);
        List<Object> out = new ArrayList<>();
        for (Registration r : ds.registrations) {
            if (!r.eventId.equals(eventId) || !r.status.equals("REGISTERED")) continue;
            Map<String, Object> m = r.toMap();
            Person st = ds.users.get(r.studentId);
            m.put("studentName", userName(r.studentId));
            m.put("username", st == null ? "" : st.getUsername());
            m.put("department", st instanceof Student ? ((Student) st).getDepartment() : "");
            out.add(m);
        }
        return out;
    }

    public synchronized Map<String, Object> markAttendance(Person actor, String regId, Map<String, Object> in) {
        require(actor, "MARK_ATTENDANCE");
        for (Registration r : ds.registrations) {
            if (!r.getId().equals(regId)) continue;
            requireOwner(actor, findEvent(r.eventId));
            if (!r.status.equals("REGISTERED")) throw ApiException.conflict("Registration is cancelled.");
            r.attended = Boolean.TRUE.equals(in.get("attended"));
            ds.saveRegistrations();
            return r.toMap();
        }
        throw ApiException.notFound("Registration not found.");
    }

    public synchronized Map<String, Object> participation(Person actor) {
        require(actor, "REGISTER_EVENT");
        List<Object> attendedEvents = new ArrayList<>();
        int registered = 0, attended = 0;
        for (Registration r : ds.registrations) {
            if (!r.studentId.equals(actor.getId()) || !r.status.equals("REGISTERED")) continue;
            registered++;
            if (r.attended) { attended++; attendedEvents.add(eventView(findEvent(r.eventId), actor)); }
        }
        List<Object> dutyList = new ArrayList<>();
        if (actor instanceof Volunteer) {
            for (Duty d : ds.duties) if (d.volunteerId.equals(actor.getId()) && d.attended) dutyList.add(dutyView(d));
        }
        return map("registered", registered, "attended", attended,
                "rate", registered == 0 ? 0 : Math.round(attended * 100.0 / registered),
                "attendedEvents", attendedEvents, "dutiesCompleted", dutyList);
    }

    // ---------------------------------------------------------------- duties
    private Map<String, Object> dutyView(Duty d) {
        Map<String, Object> m = d.toMap();
        Event e = null;
        for (Event x : ds.events) if (x.getId().equals(d.eventId)) e = x;
        m.put("eventTitle", e == null ? "(deleted)" : e.title);
        m.put("date", e == null ? "" : e.date);
        m.put("time", e == null ? "" : e.startTime + " - " + e.endTime);
        m.put("venue", e == null ? "" : e.venue);
        m.put("volunteerName", userName(d.volunteerId));
        return m;
    }

    private Duty findDuty(String id) {
        for (Duty d : ds.duties) if (d.getId().equals(id)) return d;
        throw ApiException.notFound("Duty not found.");
    }

    public synchronized List<Object> listVolunteers(Person actor) {
        require(actor, "VIEW_VOLUNTEERS");
        List<Object> out = new ArrayList<>();
        for (Person p : ds.users.values()) if (p instanceof Volunteer) out.add(p.toPublicMap());
        return out;
    }

    public synchronized List<Object> listDuties(Person actor) {
        List<Object> out = new ArrayList<>();
        if (actor instanceof Volunteer) {
            for (Duty d : ds.duties) if (d.volunteerId.equals(actor.getId())) out.add(dutyView(d));
        } else if (actor.can("ASSIGN_DUTY")) {
            for (Duty d : ds.duties) if (owns(actor, findEvent(d.eventId))) out.add(dutyView(d));
        } else {
            throw ApiException.forbidden("Your role (" + actor.getRole() + ") is not allowed to do this.");
        }
        return out;
    }

    public synchronized Map<String, Object> assignDuty(Person actor, Map<String, Object> in) {
        require(actor, "ASSIGN_DUTY");
        Event e = findEvent(s(in, "eventId"));
        requireOwner(actor, e);
        Person v = findUser(s(in, "volunteerId"));
        if (!(v instanceof Volunteer)) throw ApiException.badRequest("Selected user is not a volunteer.");
        String task = s(in, "task");
        if (task.length() < 3 || task.length() > 100) throw ApiException.badRequest("Task must be 3-100 characters.");
        if (e.status.equals("CANCELLED")) throw ApiException.conflict("Cannot assign duties for a cancelled event.");
        for (Duty d : ds.duties)
            if (d.eventId.equals(e.getId()) && d.volunteerId.equals(v.getId()) && d.task.equalsIgnoreCase(task))
                throw ApiException.conflict("This volunteer already has that duty for this event.");
        for (Duty d : ds.duties) {   // a volunteer cannot be in two places at once
            if (!d.volunteerId.equals(v.getId()) || d.status.equals("DECLINED") || d.eventId.equals(e.getId())) continue;
            Event other = findEvent(d.eventId);
            if (other.overlapsWith(e))
                throw ApiException.conflict(v.getName() + " already has a duty at '" + other.title + "' at that time.");
        }
        Duty d = new Duty(map("id", ds.newId("d", ds.duties), "eventId", e.getId(), "volunteerId", v.getId(),
                "task", task, "status", "ASSIGNED", "attended", false));
        ds.duties.add(d);
        ds.saveDuties();
        return dutyView(d);
    }

    public synchronized Map<String, Object> updateDutyStatus(Person actor, String id, Map<String, Object> in) {
        Duty d = findDuty(id);
        String status = s(in, "status");
        if (!DUTY_STATUSES.contains(status)) throw ApiException.badRequest("Invalid duty status.");
        if (actor instanceof Volunteer) {
            if (!d.volunteerId.equals(actor.getId())) throw ApiException.forbidden("This is not your duty.");
            boolean ok = (d.status.equals("ASSIGNED") && (status.equals("ACCEPTED") || status.equals("DECLINED")))
                    || (d.status.equals("ACCEPTED") && status.equals("DONE"));
            if (!ok) throw ApiException.conflict("You cannot change a " + d.status + " duty to " + status + ".");
        } else {
            require(actor, "ASSIGN_DUTY");
            requireOwner(actor, findEvent(d.eventId));
        }
        d.status = status;
        ds.saveDuties();
        return dutyView(d);
    }

    public synchronized Map<String, Object> dutyAttendance(Person actor, String id, Map<String, Object> in) {
        require(actor, "MARK_ATTENDANCE");
        Duty d = findDuty(id);
        requireOwner(actor, findEvent(d.eventId));
        if (d.status.equals("DECLINED")) throw ApiException.conflict("Duty was declined.");
        d.attended = Boolean.TRUE.equals(in.get("attended"));
        ds.saveDuties();
        return dutyView(d);
    }

    public synchronized void deleteDuty(Person actor, String id) {
        require(actor, "ASSIGN_DUTY");
        Duty d = findDuty(id);
        requireOwner(actor, findEvent(d.eventId));
        ds.duties.remove(d);
        ds.saveDuties();
    }

    // ---------------------------------------------------------------- users (admin)
    public synchronized List<Object> listUsers(Person actor, Map<String, String> q) {
        require(actor, "MANAGE_USERS");
        String search = q.getOrDefault("search", "").toLowerCase();
        String role = q.getOrDefault("role", "");
        List<Object> out = new ArrayList<>();
        for (Person p : ds.users.values()) {
            if (!role.isEmpty() && !p.getRole().equals(role)) continue;
            if (!search.isEmpty() && !(p.getName() + " " + p.getUsername() + " " + p.getEmail()).toLowerCase().contains(search)) continue;
            out.add(p.toPublicMap());
        }
        return out;
    }

    private void checkUserFields(Map<String, Object> in, String excludeId, boolean passwordRequired, String role) {
        String name = s(in, "name"), username = s(in, "username"), email = s(in, "email"), pw = s(in, "password");
        if (name.length() < 2 || name.length() > 50) throw ApiException.badRequest("Name must be 2-50 characters.");
        if (!username.matches("[A-Za-z0-9_]{3,20}")) throw ApiException.badRequest("Username: 3-20 letters, digits or underscore.");
        for (Person p : ds.users.values())
            if (!p.getId().equals(excludeId) && p.getUsername().equalsIgnoreCase(username))
                throw ApiException.conflict("Username already taken.");
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw ApiException.badRequest("Enter a valid email address.");
        if ((passwordRequired || !pw.isEmpty()) && (pw.length() < 6 || pw.length() > 50))
            throw ApiException.badRequest("Password must be 6-50 characters.");
        if (!ROLES.contains(role)) throw ApiException.badRequest("Invalid role.");
        if (role.equals("STUDENT") || role.equals("VOLUNTEER")) {
            int year = toInt(in.get("year"));
            if (year < 1 || year > 4) throw ApiException.badRequest("Year must be between 1 and 4.");
        }
        if (s(in, "department").length() > 40) throw ApiException.badRequest("Department is too long.");
        if (s(in, "skills").length() > 100) throw ApiException.badRequest("Skills text is too long.");
    }

    public synchronized Map<String, Object> createUser(Person actor, Map<String, Object> in) {
        require(actor, "MANAGE_USERS");
        return addUser(in, s(in, "role"));
    }

    /** Public sign-up: always creates a STUDENT, whatever the browser sends. */
    public synchronized Map<String, Object> signUp(Map<String, Object> in) {
        return addUser(in, "STUDENT");
    }

    private Map<String, Object> addUser(Map<String, Object> in, String role) {
        checkUserFields(in, null, true, role);
        Map<String, Object> m = map("id", ds.newId("u", new ArrayList<BaseEntity>(ds.users.values())),
                "role", role, "name", s(in, "name"), "username", s(in, "username"),
                "passwordHash", Hash.sha256(s(in, "password")), "email", s(in, "email"),
                "department", s(in, "department"), "year", toInt(in.get("year")), "skills", s(in, "skills"));
        Person p = Person.fromMap(m);     // factory picks Student / Volunteer / Coordinator / Admin
        ds.users.put(p.getId(), p);
        ds.saveUsers();
        return p.toPublicMap();
    }

    public synchronized Map<String, Object> updateUser(Person actor, String id, Map<String, Object> in) {
        require(actor, "MANAGE_USERS");
        Person old = findUser(id);
        String role = s(in, "role").isEmpty() ? old.getRole() : s(in, "role");
        checkUserFields(in, id, false, role);
        if (id.equals(actor.getId()) && !role.equals("ADMIN")) throw ApiException.badRequest("You cannot remove your own admin role.");
        if (old instanceof Coordinator && !role.equals("COORDINATOR"))
            for (Event e : ds.events)
                if (e.coordinatorId.equals(id)) throw ApiException.conflict("This coordinator still owns events. Reassign or delete them first.");
        Map<String, Object> m = old.toMap();
        m.put("role", role);
        m.put("name", s(in, "name"));
        m.put("username", s(in, "username"));
        m.put("email", s(in, "email"));
        m.put("department", s(in, "department"));
        m.put("year", toInt(in.get("year")));
        m.put("skills", s(in, "skills"));
        if (!s(in, "password").isEmpty()) m.put("passwordHash", Hash.sha256(s(in, "password")));
        Person updated = Person.fromMap(m);
        ds.users.put(id, updated);
        if (!(updated instanceof Student)) {              // staff cannot hold registrations
            ds.registrations.removeIf(r -> r.studentId.equals(id));
            ds.saveRegistrations();
        }
        if (!(updated instanceof Volunteer)) {            // only volunteers hold duties
            ds.duties.removeIf(d -> d.volunteerId.equals(id));
            ds.saveDuties();
        }
        ds.saveUsers();
        return updated.toPublicMap();
    }

    public synchronized void deleteUser(Person actor, String id) {
        require(actor, "MANAGE_USERS");
        Person p = findUser(id);
        if (id.equals(actor.getId())) throw ApiException.badRequest("You cannot delete your own account.");
        for (Event e : ds.events)
            if (e.coordinatorId.equals(id)) throw ApiException.conflict("This user coordinates events. Reassign or delete them first.");
        ds.users.remove(id);
        ds.registrations.removeIf(r -> r.studentId.equals(id));
        ds.duties.removeIf(d -> d.volunteerId.equals(id));
        sessions.values().removeIf(uid -> uid.equals(id));
        ds.saveAll();
    }

    // ---------------------------------------------------------------- dashboard statistics
    public synchronized Map<String, Object> stats(Person actor) {
        Map<String, Object> st = new LinkedHashMap<>();
        if (actor instanceof Admin) {
            Map<String, Integer> byRole = new HashMap<>();
            for (Person p : ds.users.values()) byRole.merge(p.getRole(), 1, Integer::sum);
            Map<String, Integer> byCategory = new TreeMap<>();
            int upcoming = 0;
            for (Event e : ds.events) {
                byCategory.merge(e.category, 1, Integer::sum);
                if (e.status.equals("UPCOMING")) upcoming++;
            }
            int regs = 0, attended = 0, doneDuties = 0;
            for (Registration r : ds.registrations) if (r.status.equals("REGISTERED")) { regs++; if (r.attended) attended++; }
            for (Duty d : ds.duties) if (d.status.equals("DONE")) doneDuties++;
            st.put("Total users", ds.users.size());
            st.put("Total events", ds.events.size());
            st.put("Upcoming events", upcoming);
            st.put("Active registrations", regs);
            st.put("Attendance marked", attended);
            st.put("Duties assigned", ds.duties.size());
            st.put("Duties done", doneDuties);
            return map("cards", st, "usersByRole", byRole, "eventsByCategory", byCategory);
        }
        if (actor instanceof Coordinator) {
            int events = 0, upcoming = 0, regs = 0, attended = 0, duties = 0, done = 0;
            Set<String> mine = new HashSet<>();
            for (Event e : ds.events) if (e.coordinatorId.equals(actor.getId())) {
                events++; mine.add(e.getId());
                if (e.status.equals("UPCOMING")) upcoming++;
            }
            for (Registration r : ds.registrations)
                if (mine.contains(r.eventId) && r.status.equals("REGISTERED")) { regs++; if (r.attended) attended++; }
            for (Duty d : ds.duties) if (mine.contains(d.eventId)) { duties++; if (d.status.equals("DONE")) done++; }
            st.put("My events", events);
            st.put("Upcoming events", upcoming);
            st.put("Registrations", regs);
            st.put("Attended", attended);
            st.put("Duties assigned", duties);
            st.put("Duties done", done);
            return map("cards", st);
        }
        // Student or Volunteer
        Map<String, Object> p = participation(actor);
        int upcomingRegs = 0;
        for (Registration r : ds.registrations)
            if (r.studentId.equals(actor.getId()) && r.status.equals("REGISTERED") && findEvent(r.eventId).status.equals("UPCOMING")) upcomingRegs++;
        st.put("Registered events", p.get("registered"));
        st.put("Upcoming events", upcomingRegs);
        st.put("Events attended", p.get("attended"));
        st.put("Attendance rate %", p.get("rate"));
        if (actor instanceof Volunteer) {
            int total = 0, pending = 0, done = 0;
            for (Duty d : ds.duties) if (d.volunteerId.equals(actor.getId())) {
                total++;
                if (d.status.equals("ASSIGNED")) pending++;
                if (d.status.equals("DONE")) done++;
            }
            st.put("My duties", total);
            st.put("Duties awaiting reply", pending);
            st.put("Duties done", done);
        }
        return map("cards", st);
    }
}

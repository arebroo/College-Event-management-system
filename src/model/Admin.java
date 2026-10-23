package model;

import java.util.*;

/** The administrator: manages users and can manage every event and duty. */
public class Admin extends Person {

    public Admin(Map<String, Object> m) { super(m); }

    @Override public String getRole() { return "ADMIN"; }

    @Override
    public Set<String> getPermissions() {
        return new HashSet<>(Arrays.asList("VIEW_EVENTS", "MANAGE_EVENTS", "MARK_ATTENDANCE",
                "ASSIGN_DUTY", "VIEW_VOLUNTEERS", "MANAGE_USERS"));
    }

    @Override public String getDashboardTitle() { return "Admin Dashboard"; }

    @Override
    public List<String> getMenu() {
        return Arrays.asList("dashboard", "users", "manageEvents", "duties", "events");
    }
}

package model;

import java.util.*;

/** Organises events, assigns volunteers and marks attendance. */
public class Coordinator extends Person {
    private String department;

    public Coordinator(Map<String, Object> m) {
        super(m);
        this.department = str(m, "department");
    }

    @Override public String getRole() { return "COORDINATOR"; }

    @Override
    public Set<String> getPermissions() {
        return new HashSet<>(Arrays.asList("VIEW_EVENTS", "MANAGE_EVENTS", "MARK_ATTENDANCE",
                "ASSIGN_DUTY", "VIEW_VOLUNTEERS"));
    }

    @Override public String getDashboardTitle() { return "Coordinator Dashboard"; }

    @Override
    public List<String> getMenu() {
        return Arrays.asList("dashboard", "manageEvents", "duties", "events");
    }

    @Override
    protected void addExtraFields(Map<String, Object> map) {
        map.put("department", department);
    }
}

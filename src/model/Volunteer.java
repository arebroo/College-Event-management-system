package model;

import java.util.*;

/**
 * A Volunteer IS-A Student (inheritance): they can do everything a student can,
 * PLUS they get duties. We reuse the parent's code with super.
 */
public class Volunteer extends Student {
    private String skills;

    public Volunteer(Map<String, Object> m) {
        super(m);
        this.skills = str(m, "skills");
    }

    public String getSkills() { return skills; }

    @Override public String getRole() { return "VOLUNTEER"; }

    @Override
    public Set<String> getPermissions() {
        Set<String> p = super.getPermissions();   // start with student permissions
        p.add("VIEW_DUTIES");
        p.add("UPDATE_OWN_DUTY");
        return p;
    }

    @Override public String getDashboardTitle() { return "Volunteer Dashboard"; }

    @Override
    public List<String> getMenu() {
        return Arrays.asList("dashboard", "myDuties", "events", "myRegistrations", "participation");
    }

    @Override
    protected void addExtraFields(Map<String, Object> map) {
        super.addExtraFields(map);
        map.put("skills", skills);
    }
}

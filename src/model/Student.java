package model;

import java.util.*;

/** A normal student: can browse events and register for them. */
public class Student extends Person {
    protected String department;
    protected int year;

    public Student(Map<String, Object> m) {
        super(m);
        this.department = str(m, "department");
        this.year = num(m, "year");
    }

    public String getDepartment() { return department; }
    public int getYear() { return year; }

    @Override public String getRole() { return "STUDENT"; }

    @Override
    public Set<String> getPermissions() {
        return new HashSet<>(Arrays.asList("VIEW_EVENTS", "REGISTER_EVENT"));
    }

    @Override public String getDashboardTitle() { return "Student Dashboard"; }

    @Override
    public List<String> getMenu() {
        return Arrays.asList("dashboard", "events", "myRegistrations", "participation");
    }

    @Override
    protected void addExtraFields(Map<String, Object> map) {
        map.put("department", department);
        map.put("year", year);
    }
}

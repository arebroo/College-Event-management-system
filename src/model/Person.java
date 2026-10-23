package model;

import java.util.*;

/**
 * ABSTRACT CLASS + INHERITANCE:
 * Person -> Student -> Volunteer,  Person -> Coordinator,  Person -> Admin.
 * getRole() and getPermissions() are abstract: each child gives its own answer (POLYMORPHISM).
 */
public abstract class Person extends BaseEntity implements DashboardProvider {
    protected String name;
    protected String username;
    protected String passwordHash;
    protected String email;

    protected Person(Map<String, Object> m) {
        super(str(m, "id"));
        this.name = str(m, "name");
        this.username = str(m, "username");
        this.passwordHash = str(m, "passwordHash");
        this.email = str(m, "email");
    }

    public abstract String getRole();
    public abstract Set<String> getPermissions();

    public boolean can(String permission) { return getPermissions().contains(permission); }

    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmail() { return email; }

    /** Hook for child classes to add their own extra fields (department, skills ...). */
    protected void addExtraFields(Map<String, Object> map) { }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("role", getRole());
        m.put("name", name);
        m.put("username", username);
        m.put("passwordHash", passwordHash);
        m.put("email", email);
        addExtraFields(m);
        return m;
    }

    /** Same as toMap() but WITHOUT the password - this is what is sent to the browser. */
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = toMap();
        m.remove("passwordHash");
        return m;
    }

    /** FACTORY: reads the "role" field and creates the matching child class (polymorphism). */
    public static Person fromMap(Map<String, Object> m) {
        String role = str(m, "role");
        switch (role) {
            case "ADMIN": return new Admin(m);
            case "COORDINATOR": return new Coordinator(m);
            case "VOLUNTEER": return new Volunteer(m);
            default: return new Student(m);
        }
    }
}

package model;

import java.util.*;

/** One student's registration for one event, plus whether they attended. */
public class Registration extends BaseEntity {
    public String eventId, studentId, status;   // status: REGISTERED / CANCELLED
    public boolean attended;

    public Registration(Map<String, Object> m) {
        super(str(m, "id"));
        eventId = str(m, "eventId");
        studentId = str(m, "studentId");
        status = str(m, "status");
        attended = bool(m, "attended");
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("eventId", eventId);
        m.put("studentId", studentId);
        m.put("status", status);
        m.put("attended", attended);
        return m;
    }
}

package model;

import java.util.*;

/** A task given to a volunteer for an event, e.g. "Registration desk". */
public class Duty extends BaseEntity {
    public String eventId, volunteerId, task, status;  // ASSIGNED / ACCEPTED / DECLINED / DONE
    public boolean attended;

    public Duty(Map<String, Object> m) {
        super(str(m, "id"));
        eventId = str(m, "eventId");
        volunteerId = str(m, "volunteerId");
        task = str(m, "task");
        status = str(m, "status");
        attended = bool(m, "attended");
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("eventId", eventId);
        m.put("volunteerId", volunteerId);
        m.put("task", task);
        m.put("status", status);
        m.put("attended", attended);
        return m;
    }
}

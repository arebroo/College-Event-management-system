package model;

import java.time.*;
import java.util.*;

/** A college event: has a venue, a date and a time slot (this is the "schedule"). */
public class Event extends BaseEntity {
    public String title, description, category, venue, date, startTime, endTime;
    public String coordinatorId, status;   // status: UPCOMING / COMPLETED / CANCELLED
    public int capacity;

    public Event(Map<String, Object> m) {
        super(str(m, "id"));
        title = str(m, "title");
        description = str(m, "description");
        category = str(m, "category");
        venue = str(m, "venue");
        date = str(m, "date");
        startTime = str(m, "startTime");
        endTime = str(m, "endTime");
        coordinatorId = str(m, "coordinatorId");
        status = str(m, "status");
        capacity = num(m, "capacity");
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("description", description);
        m.put("category", category);
        m.put("venue", venue);
        m.put("date", date);
        m.put("startTime", startTime);
        m.put("endTime", endTime);
        m.put("capacity", capacity);
        m.put("coordinatorId", coordinatorId);
        m.put("status", status);
        return m;
    }

    /** True if both events are on the same day and their time slots overlap. */
    public boolean overlapsWith(Event other) {
        if (!date.equals(other.date)) return false;
        LocalTime s1 = LocalTime.parse(startTime), e1 = LocalTime.parse(endTime);
        LocalTime s2 = LocalTime.parse(other.startTime), e2 = LocalTime.parse(other.endTime);
        return s1.isBefore(e2) && s2.isBefore(e1);
    }
}

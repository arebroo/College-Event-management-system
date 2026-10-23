package service;

import model.*;
import util.Hash;

import java.time.LocalDate;
import java.util.*;

/** Sample data created on the very first run. Dates are relative to today so events are always "upcoming". */
public class SeedData {

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private static void user(DataStore ds, String id, String role, String name, String username,
                             String password, String dept, int year, String skills) {
        Person p = Person.fromMap(m("id", id, "role", role, "name", name, "username", username,
                "passwordHash", Hash.sha256(password), "email", username + "@college.edu",
                "department", dept, "year", year, "skills", skills));
        ds.users.put(id, p);
    }

    private static void event(DataStore ds, String id, String title, String cat, String venue, int dayOffset,
                              String start, String end, int cap, String coord, String status, String desc) {
        ds.events.add(new Event(m("id", id, "title", title, "description", desc, "category", cat,
                "venue", venue, "date", LocalDate.now().plusDays(dayOffset).toString(),
                "startTime", start, "endTime", end, "capacity", cap, "coordinatorId", coord, "status", status)));
    }

    private static void reg(DataStore ds, String id, String ev, String stu, boolean attended) {
        ds.registrations.add(new Registration(m("id", id, "eventId", ev, "studentId", stu,
                "status", "REGISTERED", "attended", attended)));
    }

    private static void duty(DataStore ds, String id, String ev, String vol, String task, String status, boolean att) {
        ds.duties.add(new Duty(m("id", id, "eventId", ev, "volunteerId", vol, "task", task,
                "status", status, "attended", att)));
    }

    public static void fill(DataStore ds) {
        user(ds, "u1", "ADMIN", "Admin User", "admin", "admin123", "", 0, "");
        user(ds, "u2", "COORDINATOR", "Prof. Meera Kulkarni", "coord1", "coord123", "CSBS", 0, "");
        user(ds, "u3", "COORDINATOR", "Prof. Arjun Deshmukh", "coord2", "coord123", "Mechanical", 0, "");
        user(ds, "u4", "STUDENT", "Riya Sharma", "student1", "student123", "CSBS", 2, "");
        user(ds, "u5", "STUDENT", "Aman Patil", "student2", "student123", "CSBS", 2, "");
        user(ds, "u6", "STUDENT", "Sneha Joshi", "student3", "student123", "IT", 3, "");
        user(ds, "u7", "STUDENT", "Rahul Verma", "student4", "student123", "Civil", 1, "");
        user(ds, "u8", "VOLUNTEER", "Karan Mehta", "vol1", "vol123", "CSBS", 2, "Photography, Social media");
        user(ds, "u9", "VOLUNTEER", "Isha Nair", "vol2", "vol123", "IT", 2, "Stage management, Anchoring");
        user(ds, "u10", "VOLUNTEER", "Neha Gupta", "vol3", "vol123", "CSBS", 3, "First aid, Crowd control");

        event(ds, "e1", "Hackathon 2026", "Technical", "Main Computer Lab", 7, "09:00", "17:00", 40, "u2", "UPCOMING",
                "A 8-hour coding challenge for teams of 4.");
        event(ds, "e2", "Cultural Fest Auditions", "Cultural", "Auditorium", 10, "14:00", "17:00", 100, "u3", "UPCOMING",
                "Auditions for dance, music and drama.");
        event(ds, "e3", "Java Workshop", "Workshop", "Seminar Hall", 3, "10:00", "13:00", 3, "u2", "UPCOMING",
                "Hands-on workshop on Java collections. Only 3 seats - good for testing a full event.");
        event(ds, "e4", "Inter-College Cricket", "Sports", "College Ground", 14, "08:00", "12:00", 60, "u3", "UPCOMING",
                "Friendly cricket tournament.");
        event(ds, "e5", "Blood Donation Camp", "Social", "Health Centre", -5, "10:00", "15:00", 50, "u2", "COMPLETED",
                "Annual blood donation drive (already finished - has attendance data).");

        reg(ds, "r1", "e5", "u4", true);
        reg(ds, "r2", "e5", "u5", true);
        reg(ds, "r3", "e1", "u4", false);
        reg(ds, "r4", "e1", "u5", false);
        reg(ds, "r5", "e3", "u6", false);
        reg(ds, "r6", "e3", "u7", false);
        reg(ds, "r7", "e3", "u8", false);

        duty(ds, "d1", "e1", "u8", "Registration desk", "ASSIGNED", false);
        duty(ds, "d2", "e1", "u9", "Stage management", "ACCEPTED", false);
        duty(ds, "d3", "e5", "u10", "First-aid helper", "DONE", true);
        duty(ds, "d4", "e5", "u9", "Crowd control", "DONE", true);
    }
}

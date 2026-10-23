# Project Report

**Title:** College Event & Volunteer Management System (EventHub)
**Course:** Object-Oriented Programming with Java – Mini Project
**Programme:** BTech Computer Science & Business Systems (CSBS), Second Year
**Submitted by:** ____________________  **Roll No.:** ________  **Guide:** ____________________

---

## 1. Abstract
Colleges organise many events (technical, cultural, sports, workshops) and depend on student volunteers, but
registrations, schedules, duty lists and attendance are usually kept on paper, in chat groups and spreadsheets.
This project is a web-based system written in Java that keeps all of this in one place. Four kinds of users
(Student, Volunteer, Coordinator, Admin) log in to separate dashboards. Coordinators create and schedule events,
students register, volunteers receive duties, and attendance is recorded. Data is saved in JSON files so it survives a
restart. The project demonstrates inheritance, abstract classes, interfaces, polymorphism, collections
(`ArrayList`, `HashMap`) and file handling.

## 2. Problem statement
Manual handling of events causes: double-booked venues, overbooked events, forgotten volunteer duties, lost
attendance sheets and no history of student participation. The goal is a simple, secure, role-based system that
removes these problems.

## 3. Objectives
1. Manage events and their schedule (date, time, venue, capacity).
2. Let students register/cancel with automatic checks (capacity, clashes).
3. Assign and track volunteer duties.
4. Record participation and attendance and show statistics.
5. Provide role-based access and admin user management.
6. Apply core Java/OOP concepts and persistent file storage.

## 4. Scope
**In scope:** the four roles, event/registration/duty/attendance/user management, search & filters, dashboards,
JSON persistence, validation. **Out of scope:** e-mail/SMS notifications, payment, QR-code check-in, database server,
HTTPS (should be added for real deployment).

## 5. Technology stack
| Part | Technology | Reason |
|---|---|---|
| Backend | Java (JDK 11+), `com.sun.net.httpserver` | No external library; simple to explain |
| Frontend | HTML5, CSS3, JavaScript (fetch API) | Standard, no framework |
| Storage | JSON text files | Human-readable, meets file-handling requirement |
| Testing | Java `HttpClient` test class | No test framework needed |

## 6. System design
*See `docs/ARCHITECTURE.md` for diagrams.*
* **Three layers:** Presentation (browser) → HTTP + Service (Java) → Data (DataStore + JSON files).
* **Class design:** `BaseEntity` (abstract) → `Person` (abstract) → `Student` → `Volunteer`, `Coordinator`, `Admin`;
  `Event`, `Registration`, `Duty` extend `BaseEntity`. Interfaces `Storable` and `DashboardProvider`.
* **Security:** passwords stored as SHA-256 hashes; random session tokens; every request is authenticated and
  authorised in Java; text is HTML-escaped in the browser; static server blocks path traversal.
* **Data design:** four JSON files (users, events, registrations, duties) linked by ids.

## 7. Implementation of required Java concepts
| Concept | Implementation |
|---|---|
| Inheritance | 4-level chain `BaseEntity → Person → Student → Volunteer`; siblings `Coordinator`, `Admin` |
| Abstract classes | `BaseEntity`, `Person` with abstract `getRole()`, `getPermissions()` |
| Interfaces | `Storable`, `DashboardProvider` |
| Polymorphism | Overridden `getPermissions()`, `getMenu()`, `toMap()`; `Person.fromMap()` factory; `DataStore.saveList(Collection<? extends Storable>)` |
| ArrayList | events, registrations, duties, every returned list |
| HashMap | users by id, session tokens, JSON objects, counting statistics |
| File handling | `FileStore` (BufferedReader/Writer, temp file + rename), JSON files in `data/` |

## 8. Modules
1. **Authentication** – login, logout, student sign-up, token sessions.
2. **Event management** – create/edit/delete, categories, status, venue/time clash detection.
3. **Registration** – register, cancel, re-register, capacity and personal-clash checks.
4. **Volunteer management** – assign duty, accept/decline/done workflow, overlap check.
5. **Attendance & participation** – coordinator marks attendance; student sees history and rate.
6. **Admin** – add/edit/delete users, change role (object type changes through the factory).
7. **Dashboard & search** – per-role statistics, bar charts, text search, category/status/role filters.

## 9. Validation and business rules
Documented in `docs/ARCHITECTURE.md` §7. All rules are enforced in Java (`AppService`), so they cannot be bypassed
from the browser.

## 10. Testing
* 65 automated API tests + 4 persistence-after-restart tests (`tests/run_tests.sh`) – all pass.
* 43 browser-level checks (clicking every button for each role in a simulated browser) – all pass.
* 20 manual test cases listed in `docs/TESTING.md`.
* Verified to compile with `--release 11` and run on JDK 21.

## 11. Results
All functional requirements are implemented. Every role sees only its own dashboard and actions; invalid operations
return clear messages; data survives restarts.

## 12. Limitations
* Whole data set is kept in memory and rewritten per change – fine for a college (hundreds of users), not for millions.
* HTTP only (no HTTPS); a single server instance; SHA-256 is simple (production systems use bcrypt/Argon2).
* Sessions are lost when the server restarts (users simply log in again).

## 13. Future enhancements
E-mail notifications, QR-code attendance, certificates (PDF), calendar export, migration to MySQL/JPA and Spring Boot,
event feedback and ratings, volunteer hour tracking.

## 14. Conclusion
EventHub shows how core Java and OOP concepts build a complete, useful application: a clear class hierarchy models the
users, collections hold the data, files make it permanent, and a small HTTP server connects it to a modern browser UI.

## 15. References
Oracle Java documentation (`java.util`, `java.io`, `java.time`, `com.sun.net.httpserver`), MDN Web Docs (fetch API, DOM),
RFC 8259 (JSON).

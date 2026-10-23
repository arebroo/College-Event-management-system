# Testing instructions & sample data

## 1. Automated tests (recommended first)

Linux / macOS / Git-Bash / WSL:
```
bash tests/run_tests.sh
```
It compiles the project, starts the server on port **8099** with a temporary data folder (so your real `data/` is
untouched), runs **65 API checks**, stops the server, starts it again and runs **4 persistence checks**.
Expected last line: `ALL TESTS PASSED`.

Windows (manual): 
```
javac -d out @sources.txt            (see README for sources.txt)
javac -cp out -d tests-out tests\ApiTest.java
start /b java -cp out Main 8099 testdata
java -cp out;tests-out ApiTest http://localhost:8099 full
```
(then stop the server, start it again with the same command and run `... ApiTest http://localhost:8099 persist`).
Run the `full` test only on a **fresh** data folder – it expects the original sample data.

What the 65 checks cover: login (401), 4 roles & menus, permission denials (403) for every role boundary, event
validation (past date, end<start, short title, capacity 0), venue clash (409), ownership (other coordinator),
search + category + status filters, registration/duplicate/cancel/re-register/full event/time clash/completed
event, attendance rules, duty assignment/duplicate/non-volunteer/status flow/ownership, admin user CRUD and
validation (duplicate username, weak password, bad email, self-delete, coordinator with events), sign-up role
forcing, dashboard statistics per role, cascade delete.

## 2. Manual test cases (do these in the browser)

| # | Steps | Expected result |
|---|---|---|
| 1 | Login `admin` / wrong password | Red message "Wrong username or password." |
| 2 | Login as `student1` | "Student Dashboard", 4 menu items, no Users menu |
| 3 | Events → type `hack` in search | Only *Hackathon 2026* |
| 4 | Events → category *Sports* | Only *Inter-College Cricket* |
| 5 | Register for *Inter-College Cricket* | Green toast, button becomes *Cancel registration*, seats 1/60 |
| 6 | Java Workshop card | Shows disabled **Event full** (capacity 3, already 3 registered) |
| 7 | My Registrations → Cancel | Status becomes CANCELLED; register again works |
| 8 | Login `coord1` → Manage Events → New event with a date in the past | Error "Event date cannot be in the past." |
| 9 | Create event at venue *Seminar Hall* on the same date/time as Java Workshop | Error "Venue is already booked..." |
| 10 | Create a valid event, then Edit it and change capacity | Table updates |
| 11 | Attendance button on *Hackathon 2026* → Mark present | Badge "yes"; student1 → Participation shows attended |
| 12 | Duties → Assign duty (vol3, Hackathon, "Photography") | Row appears; repeat same → error duplicate |
| 13 | Login `vol3` → My Duties → Accept → Mark as done | Status flows ASSIGNED → ACCEPTED → DONE |
| 14 | Login `coord2` and try to open Hackathon registrations by API (or edit coord1's events) | Not listed in Manage Events (only own events); API returns 403 |
| 15 | Login `admin` → Users → Add user (role COORDINATOR) | Appears in table; new user can log in |
| 16 | Admin → edit student → role VOLUNTEER | Role changes, new user type gets *My Duties* menu |
| 17 | Admin → delete user who coordinates events | Error "coordinates events. Reassign or delete them first." |
| 18 | **Persistence:** stop server (Ctrl+C), start again, login | All changes still present; check `data/*.json` in a text editor |
| 19 | Try the URL `/api/users` in the browser address bar | `{"error":"Please log in."}` (needs token) |
| 20 | Resize browser narrow | Sidebar becomes ☰ menu |

## 3. Sample data (created automatically in `data/` on first run)

**Users (10):** `admin`; coordinators `coord1` (Prof. Meera Kulkarni), `coord2` (Prof. Arjun Deshmukh);
students `student1` Riya, `student2` Aman, `student3` Sneha, `student4` Rahul; volunteers `vol1` Karan, `vol2` Isha, `vol3` Neha.

**Events (5):** Hackathon 2026 (+7 days, Main Computer Lab, 40 seats) · Cultural Fest Auditions (+10, Auditorium) ·
Java Workshop (+3, Seminar Hall, **3 seats, full**) · Inter-College Cricket (+14, Ground) ·
Blood Donation Camp (5 days ago, **COMPLETED**, attendance already marked).

**Registrations (7)** and **Duties (4)** link them (e.g. vol1 → Hackathon "Registration desk" waiting for reply;
vol2 → Hackathon "Stage management" accepted; two finished duties at the Blood Donation Camp with attendance).

Reset: stop server → delete `data/*.json` → start again.

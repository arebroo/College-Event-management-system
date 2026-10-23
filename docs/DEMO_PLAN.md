# Demonstration plan (about 7 minutes)

**Before you start:** run `./run.sh` (or `run.bat`), open http://localhost:8080 in **two browser windows**
(one normal, one private/incognito so two users can be logged in at once). Delete `data/*.json` for a clean start.
Keep the code open in your IDE on `Person.java`, `AppService.java`, `DataStore.java`.

| Time | What to do | What to say |
|---|---|---|
| 0:00–0:45 | Show the project folder and `docs/ARCHITECTURE.md` diagram | "Three layers: browser, Java server with service layer, JSON files. No external libraries – only the JDK." |
| 0:45–1:45 | **IDE:** open `Person.java`, then `Student`, `Volunteer`, `Admin` | "`Person` is abstract with abstract `getRole()` and `getPermissions()`. `Volunteer extends Student` – inheritance – and adds duties. Each class overrides the methods: polymorphism. `Storable` and `DashboardProvider` are interfaces." |
| 1:45–2:30 | **Admin** (window 1): login → dashboard charts → Users → add a coordinator → try duplicate username | "Admin sees system-wide statistics. Validation happens in Java – duplicate username gives 409." |
| 2:30–3:30 | **Coordinator** `coord1` → Manage Events → create event (try a past date → error; try venue clash → error) → create valid event | "Scheduling rule: same venue cannot be double-booked. Coordinator sees only their own events." |
| 3:30–4:30 | **Student** `student1` (window 2): search "hack", category filter → register → show *Event full* on Java Workshop → try registering for overlapping event | "Capacity, duplicates and time clashes are all checked in `AppService.register()`." |
| 4:30–5:30 | **Coordinator**: Attendance → mark student present. **Duties** → assign `vol1` a task. **Volunteer** `vol1`: accept → mark done | "Duty workflow ASSIGNED → ACCEPTED → DONE; volunteer can only change their own duty." |
| 5:30–6:00 | **Student**: Participation page shows attended events and rate | "Attendance recorded by coordinator feeds the student's participation record." |
| 6:00–6:40 | **Permissions proof:** in student window press F12 → Console: `fetch('/api/users',{headers:{Authorization:'Bearer '+sessionStorage.token}}).then(r=>r.json()).then(console.log)` | "Even if a student calls the API directly, the Java backend answers 403 – permissions are not only in the UI." |
| 6:40–7:30 | **Persistence:** Ctrl+C the server, run again, refresh/login → data is still there; open `data/events.json` | "`DataStore` loads JSON into `ArrayList`/`HashMap` at start and saves after each change through `FileStore`." |
| 7:30+ | Optional: run `bash tests/run_tests.sh` | "65 automated tests + restart test all pass." |

**Tips:** rehearse once; keep the sample accounts on a sticky note; if something fails, delete `data/*.json` and restart.

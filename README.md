# 🎓 College Event & Volunteer Management System (EventHub)

A Java mini-project for a second-year BTech CSBS student. It manages college events, student registrations,
volunteer duties and attendance, with **four roles** (Student, Volunteer, Coordinator, Admin), each with its own
dashboard and permissions.

* **Backend:** plain Java (JDK only, no Maven, no libraries) using the built-in `com.sun.net.httpserver.HttpServer`
* **Frontend:** HTML + CSS + JavaScript (no framework), served by the same Java program
* **Storage:** JSON files in `data/` (saved after every change, loaded at start-up)

> **Zero dependencies.** You only need a **JDK 11 or newer** (tested on JDK 21; compiles with `--release 11`).
> I wrote a tiny JSON reader/writer (`util/Json.java`) so no external JSON library is needed.

## 1. Setup and run

1. Install a JDK (11+). Check: `java -version` and `javac -version` must both work.
   (Download "Temurin" or "OpenJDK" from adoptium.net if `javac` is missing.)
2. Open a terminal **inside the project folder** (the one containing `src/` and `web/`).
3. Run:
   * Linux / macOS: `./run.sh`
   * Windows: double-click `run.bat` (or run it in Command Prompt)
   * Manual: `javac -d out $(find src -name "*.java")` then `java -cp out Main`
     (Windows manual: `dir /s /b src\*.java > sources.txt`, `javac -d out @sources.txt`, `java -cp out Main`)
4. Open **http://localhost:8080** in a browser.
5. Another port / data folder: `java -cp out Main 9090 mydata`

> The program must be started **from the project folder**, because it looks for `web/` and `data/` there.

On the first run it creates sample data in `data/`. Stop with `Ctrl+C`; start again and everything is still there.
To reset to the sample data, stop the server and delete the `.json` files in `data/`.

### Demo accounts

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Coordinator | `coord1` / `coord2` | `coord123` |
| Volunteer | `vol1`, `vol2`, `vol3` | `vol123` |
| Student | `student1` … `student4` | `student123` |

The login page also has buttons that fill these in. New students can use the **Student Sign-up** tab.

## 2. What each role can do

| Feature | Student | Volunteer | Coordinator | Admin |
|---|:--:|:--:|:--:|:--:|
| Browse / search / filter events | ✅ | ✅ | ✅ | ✅ |
| Register / cancel for events | ✅ | ✅ | – | – |
| See own registrations & participation | ✅ | ✅ | – | – |
| See, accept, decline, finish own duties | – | ✅ | – | – |
| Create / edit / delete events | – | – | own events | all events |
| Mark student & volunteer attendance | – | – | own events | all events |
| Assign / remove volunteer duties | – | – | own events | all events |
| Manage users (add, edit, change role, delete) | – | – | – | ✅ |
| Dashboard statistics | personal | personal + duties | own events | system-wide + charts |

## 3. Folder structure

```
CollegeEventSystem/
├── src/
│   ├── Main.java                  start-up: load data, start web server
│   ├── model/                     DATA CLASSES (OOP concepts live here)
│   │   ├── Storable.java          interface  - "can be saved as a Map"
│   │   ├── DashboardProvider.java interface  - "has a dashboard title and menu"
│   │   ├── BaseEntity.java        abstract class - id + helpers
│   │   ├── Person.java            abstract class - common user fields
│   │   ├── Student.java           extends Person
│   │   ├── Volunteer.java         extends Student
│   │   ├── Coordinator.java       extends Person
│   │   ├── Admin.java             extends Person
│   │   ├── Event.java, Registration.java, Duty.java   extend BaseEntity
│   ├── service/                   BUSINESS LOGIC
│   │   ├── DataStore.java         ArrayList/HashMap collections + load/save JSON files
│   │   ├── SeedData.java          sample data for the first run
│   │   └── AppService.java        every rule, validation and permission check
│   ├── server/                    HTTP LAYER
│   │   ├── ApiHandler.java        /api/... routes (JSON in, JSON out)
│   │   └── StaticHandler.java     serves web/ files
│   └── util/                      Json.java, FileStore.java, Hash.java, ApiException.java
├── web/                           FRONTEND: index.html, css/style.css, js/app.js
├── data/                          JSON data files (created at first run)
├── tests/                         ApiTest.java + run_tests.sh
├── docs/                          architecture, file guide, testing, report, demo plan, viva Q&A
├── run.sh / run.bat               build + run scripts
```

## 4. Documentation index

| Document | What is inside |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture diagram, class diagrams, ER diagram, data-flow walkthrough, REST API table |
| [docs/FILE_GUIDE.md](docs/FILE_GUIDE.md) | Beginner explanation of every file + where each Java concept is used |
| [docs/TESTING.md](docs/TESTING.md) | Automated tests, manual test cases, sample data |
| [docs/REPORT.md](docs/REPORT.md) | Project report (ready to submit) |
| [docs/DEMO_PLAN.md](docs/DEMO_PLAN.md) | 5–8 minute demonstration script |
| [docs/VIVA.md](docs/VIVA.md) | 54 viva questions with answers (50 + 4 bonus) |

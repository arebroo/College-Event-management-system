# File-by-file guide (beginner friendly)

## Part A – Where each mandatory Java concept is used

| Concept | Where to point in the code | One-line explanation |
|---|---|---|
| **Inheritance** | `Volunteer extends Student extends Person extends BaseEntity`; `Event/Registration/Duty extends BaseEntity` | A child class reuses the fields and methods of its parent. A Volunteer *is a* Student, so it automatically has the student's permissions and adds its own. |
| **Abstract class** | `BaseEntity`, `Person` (`abstract getRole()`, `abstract getPermissions()`) | You can never write `new Person(...)`. It is only a template that forces every child to answer "what is your role / what may you do?". |
| **Interface** | `Storable` (`toMap()`), `DashboardProvider` (`getDashboardTitle()`, `getMenu()`) | A contract: "any class that signs this must have these methods". `DataStore.saveList()` saves *anything* that is `Storable`. |
| **Polymorphism** | `person.getPermissions()`, `person.getMenu()`, `person.toMap()` in `AppService`/`DataStore`; `Person.fromMap()` factory | One call, different behaviour depending on the real object. `person.can("MANAGE_USERS")` is true for `Admin` and false for `Student`, without any `if (role == ...)` chain. Also *method overriding* (`Volunteer.getPermissions()` calls `super` then adds more). |
| **ArrayList** | `DataStore.events`, `registrations`, `duties`; all list results in `AppService` | Ordered, growable list of objects. |
| **HashMap** | `DataStore.users` (id → Person), `AppService.sessions` (token → userId), JSON objects as `Map<String,Object>`, `stats()` counters (`byRole.merge`) | Key-value lookup, very fast by key. (`LinkedHashMap` is a HashMap that remembers insertion order.) |
| **File handling** | `util/FileStore.java`, `DataStore.load()/saveList()` | Reads/writes `data/*.json` with `BufferedReader/BufferedWriter`; writes to a temp file then renames, so a crash cannot corrupt data. |
| Exceptions | `ApiException` (custom, carries the HTTP status), `try/catch` in `ApiHandler` | Business-rule errors are thrown as exceptions and converted to JSON error messages. |
| Encapsulation | `private`/`protected` fields + getters in `model/` | Fields are protected from outside code. |

## Part B – Every file explained

### `src/Main.java`
Entry point. Reads optional arguments (port, data folder), creates `DataStore` → `load()`, creates `AppService`,
starts `HttpServer`, registers two handlers: `/api` → `ApiHandler`, `/` → `StaticHandler`. Uses a thread pool of 8
so several browser requests can be handled at once.

### `src/model/` – the "things" in our system
| File | Explanation |
|---|---|
| `Storable.java` | Interface with `toMap()`. Everything saved to JSON implements it. |
| `DashboardProvider.java` | Interface for dashboard title + menu list. Implemented by `Person`. |
| `BaseEntity.java` | Abstract parent with the `id` and small helpers (`str`, `num`, `bool`) that safely read values from a Map when loading JSON. |
| `Person.java` | Abstract parent of all users: name, username, passwordHash, email. Declares abstract `getRole()` / `getPermissions()`. `can(permission)` checks the set. `toPublicMap()` removes the password hash before sending to the browser. `fromMap()` is a **factory**: reads `"role"` and creates the right subclass. |
| `Student.java` | Adds department & year. Permissions: `VIEW_EVENTS`, `REGISTER_EVENT`. Menu: dashboard, events, myRegistrations, participation. |
| `Volunteer.java` | `extends Student`. Adds `skills`; permissions = student's + `VIEW_DUTIES`, `UPDATE_OWN_DUTY`; extra menu item *My Duties*. |
| `Coordinator.java` | Permissions: `MANAGE_EVENTS`, `MARK_ATTENDANCE`, `ASSIGN_DUTY`, `VIEW_VOLUNTEERS`, `VIEW_EVENTS`. |
| `Admin.java` | All coordinator permissions + `MANAGE_USERS`. |
| `Event.java` | Title, description, category, venue, date, start/end time, capacity, coordinatorId, status. `overlapsWith(other)` = same date and time ranges intersect (used for the scheduling rules). |
| `Registration.java` | studentId + eventId + status (REGISTERED/CANCELLED) + `attended`. |
| `Duty.java` | volunteerId + eventId + task + status + `attended`. |

### `src/service/` – the brain
| File | Explanation |
|---|---|
| `DataStore.java` | Holds the 4 collections in memory. `load()` reads JSON files into objects; `saveUsers()/saveEvents()/...` write them back. `newId("e", events)` creates ids like `e6`. If `users.json` is missing it calls `SeedData`. |
| `SeedData.java` | Creates the sample users, events, registrations and duties (event dates are relative to today so they are always in the future). |
| `AppService.java` | **All rules live here.** Groups: authentication (login/sessions), events (list/search/filter, create, update, delete, clash check), registrations, attendance, duties, users, statistics. Each method starts with `require(actor, "PERMISSION")` and ownership checks, then validates input, changes the lists and saves. Methods are `synchronized` so simultaneous requests cannot corrupt data. |

### `src/server/` – talking HTTP
| File | Explanation |
|---|---|
| `ApiHandler.java` | Reads method + URL + JSON body, finds the logged-in user from the `Authorization: Bearer <token>` header, matches the route (`is(p, "events", "*", "register")`) and calls `AppService`. Catches `ApiException` and sends `{"error": "..."}` with the right status code. |
| `StaticHandler.java` | Sends files from `web/` (HTML, CSS, JS). Blocks `../` path tricks. |

### `src/util/`
| File | Explanation |
|---|---|
| `Json.java` | Mini JSON parser/writer: JSON object ⇄ `Map`, array ⇄ `List`. `pretty()` makes readable data files. |
| `FileStore.java` | `read(path)` / `write(path, text)` with the temp-file-then-rename trick. |
| `Hash.java` | SHA-256 hashing of passwords (we never store plain passwords). |
| `ApiException.java` | `RuntimeException` carrying an HTTP status (400/401/403/404/409). |

### `web/` – the user interface
| File | Explanation |
|---|---|
| `index.html` | One page: login/sign-up card, app shell (sidebar + content area), modal and toast containers. |
| `css/style.css` | Responsive styling (sidebar collapses on phones, dark mode via `prefers-color-scheme`). |
| `js/app.js` | Section 1 helpers (`api()` wrapper using `fetch`, `esc()` against HTML injection, toast, modal) · 2 login/sign-up · 3 navigation (menu comes from the Java backend) · 4 one function per page (`VIEWS.dashboard`, `VIEWS.events`, …) · 5 `ACTIONS` object: every button has `data-action="..."` and one click listener dispatches it · 6 start-up. |

### `tests/`
`ApiTest.java` (65 + 4 automated checks using Java's `HttpClient`) and `run_tests.sh` (build → fresh data → run → restart → verify persistence).

### Root
`run.sh` / `run.bat` build and run · `data/` JSON files · `.gitignore`.

## Part C – How JSON persistence works (simple version)

```
start-up:  users.json ──Json.parse──► List<Map> ──Person.fromMap──► Student/Volunteer/... objects ──► HashMap users
change:    object.toMap() ──► List<Map> ──Json.pretty──► text ──FileStore.write──► users.json  (temp file + rename)
```

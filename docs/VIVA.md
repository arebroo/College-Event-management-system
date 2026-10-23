# Viva questions and answers (54)

## A. Project overview
**1. What does your project do?**
It manages college events: coordinators create and schedule events, students register, volunteers get duties, attendance is recorded, and admin manages users. Four roles have separate dashboards and permissions.

**2. Why did you choose Java and no framework?**
To keep it simple and explainable. The JDK already includes an HTTP server (`com.sun.net.httpserver`), so there are no dependencies, and every line is ours.

**3. Which dependencies does the project need?**
None except a JDK 11+. I even wrote a small JSON parser (`Json.java`) instead of using a library.

**4. Explain the architecture.**
Three layers: browser (HTML/CSS/JS) → Java server (HTTP layer `ApiHandler`, business layer `AppService`, data layer `DataStore`) → JSON files. Each layer has a single responsibility.

**5. How does the browser talk to Java?**
With JavaScript `fetch()` calls to URLs such as `/api/events`. The data travels as JSON. Java answers with JSON and an HTTP status code.

**6. What is a REST API? Is yours REST?**
An API that uses HTTP methods on resource URLs: GET read, POST create, PUT update, DELETE remove. Mine follows this style (e.g. `DELETE /api/events/e3`).

**7. What are HTTP status codes 400, 401, 403, 404, 409 used for in your project?**
400 invalid input, 401 not logged in, 403 logged in but not allowed, 404 not found, 409 business conflict (event full, duplicate, clash).

## B. OOP concepts
**8. What is inheritance? Show it in your project.**
A child class acquires the members of a parent. `Volunteer extends Student extends Person extends BaseEntity`.

**9. Why is Volunteer a subclass of Student?**
A volunteer is also a student: they can browse and register for events. Inheritance reuses that code; `Volunteer` only adds `skills` and duty permissions.

**10. What is an abstract class? Where do you use it?**
A class that cannot be instantiated and may contain abstract methods. `Person` and `BaseEntity`. `Person` declares abstract `getRole()` and `getPermissions()`.

**11. Why is Person abstract?**
A generic "person" without a role makes no sense in our system. Abstract forces each child to define its role and permissions.

**12. What is an interface? Which ones do you have?**
A contract of method signatures. `Storable` (`toMap()`) and `DashboardProvider` (`getDashboardTitle()`, `getMenu()`).

**13. Abstract class vs interface?**
Abstract class: can hold fields and implemented methods, single inheritance (`extends`). Interface: contract only (plus defaults), a class can implement many. We used an abstract class for shared data (`id`, name…) and interfaces for abilities.

**14. What is polymorphism? Give an example.**
One method call, different behaviour depending on the actual object. `person.getPermissions()` returns different sets for `Student`, `Coordinator`, `Admin`. `person.can("MANAGE_USERS")` needs no `if (role == "ADMIN")`.

**15. Compile-time vs runtime polymorphism?**
Compile-time = overloading, decided by the compiler. Runtime = overriding, decided by the JVM from the real object type. We use runtime polymorphism (`getMenu()`, `toMap()`).

**16. Method overriding in your code?**
`Volunteer.getPermissions()` overrides `Student.getPermissions()`, calls `super.getPermissions()` and adds `VIEW_DUTIES`. Also `getMenu()`, `getRole()`, `addExtraFields()`.

**17. What does `super` do?**
Refers to the parent class: `super(m)` calls the parent constructor; `super.getPermissions()` calls the parent's method.

**18. What is encapsulation?**
Hiding data behind `private/protected` fields with controlled access (getters, `can()`). Password hashes are never sent to the browser because `toPublicMap()` removes them.

**19. What is the factory method `Person.fromMap()`?**
It reads the `"role"` text and creates the correct subclass (`new Admin(...)`, `new Volunteer(...)`). The rest of the code just works with `Person`.

**20. What is the `@Override` annotation?**
It tells the compiler "this method overrides a parent method" and gives an error if the signature is wrong.

**21. Difference between `protected` and `private`?**
`private` – only inside the class. `protected` – the class, its subclasses and its package. Fields like `name` are protected so `Student` can use them.

**22. What is the `instanceof` operator and where is it used?**
Checks an object's type: `p instanceof Admin`, `p instanceof Volunteer` (e.g. to verify that an assigned user is really a volunteer).

**23. What is a custom exception? Which one do you have?**
`ApiException extends RuntimeException`, carries an HTTP status. The service throws it for broken rules and `ApiHandler` converts it to a JSON error.

**24. Checked vs unchecked exceptions?**
Checked (e.g. `IOException`) must be caught/declared. Unchecked (`RuntimeException` and children) need not be. `ApiException` is unchecked so the service code stays clean.

## C. Collections
**25. ArrayList vs array?**
ArrayList grows dynamically and has methods (`add`, `remove`, `removeIf`); an array has a fixed size.

**26. Why ArrayList for events but HashMap for users?**
Events are processed in order and filtered by scanning. Users are looked up by id many times (login tokens, owners), where a HashMap gives fast key lookup.

**27. How does HashMap work internally?**
It computes `hashCode()` of the key, finds a bucket, and compares with `equals()`. Average `get/put` is O(1).

**28. HashMap vs LinkedHashMap vs TreeMap?**
HashMap – no order; LinkedHashMap – insertion order (used for users so the list order is stable); TreeMap – sorted by key (used for categories in statistics).

**29. HashMap vs HashSet?**
HashMap stores key-value pairs, HashSet stores unique values. Permissions are a `HashSet<String>`.

**30. What are generics?**
Type parameters that make collections type-safe: `List<Event>` accepts only `Event`. `Collection<? extends Storable>` accepts any collection of Storable subclasses.

**31. How do you sort events?**
`list.sort(Comparator.comparing(e -> e.date).thenComparing(e -> e.startTime))`.

**32. What does `map.merge(key, 1, Integer::sum)` do?**
Adds 1 to the existing count or inserts 1 if the key is new – used to count users by role.

## D. File handling and persistence
**33. How do you make the data permanent?**
`DataStore` converts objects to Maps (`toMap()`), `Json.pretty()` makes text, `FileStore.write()` saves it into `data/*.json`. At start-up the files are read, parsed and converted back to objects.

**34. Why write to a temp file and rename?**
If the program crashes while writing, the original file would be half-written and corrupt. Renaming the finished temp file is almost instantaneous, so the data file is always complete.

**35. Which Java classes did you use for file I/O?**
`BufferedReader`, `InputStreamReader`, `FileInputStream`, `BufferedWriter`, `OutputStreamWriter`, `FileOutputStream`, `File`, `Files.move` with try-with-resources.

**36. What is try-with-resources?**
`try (Reader r = ...) { }` closes the resource automatically, even if an exception occurs.

**37. What is JSON? Why JSON instead of text/CSV/serialization?**
A lightweight text format of objects `{}`, arrays `[]`, strings, numbers, booleans. It is human readable, supports nested data, and the browser also speaks JSON, so the same format is used for storage and for the API.

**38. Why not a database?**
The requirement is file handling, and for a mini-project JSON is simpler. In the future MySQL could replace `DataStore` without changing the service/UI much.

**39. How are unique ids generated?**
`DataStore.newId("e", events)` finds the highest number in existing ids and adds 1 → `e6`.

## E. Web, security and validation
**40. How does login work?**
Username + SHA-256 hash of the password is compared with the stored hash. If correct, a random UUID token is created, kept in the `sessions` HashMap and returned. The browser sends it in `Authorization: Bearer …` with every request.

**41. Why hash passwords?**
If the file leaks, passwords are not readable. A hash is one-way. (Production systems use salted slow hashes such as bcrypt.)

**42. Where are permissions enforced – browser or Java?**
In Java. The browser only hides buttons. Even a direct API call by a student to `/api/users` gets 403.

**43. How do you stop a coordinator from editing another coordinator's event?**
`requireOwner()` compares `event.coordinatorId` with the logged-in user (admin is allowed everything) and throws 403.

**44. How is a venue/time clash detected?**
`Event.overlapsWith()`: same date and `start1 < end2 && start2 < end1`. `AppService.cleanEvent()` checks all other upcoming events at the same venue.

**45. How do you prevent two students taking the last seat?**
`AppService` methods are `synchronized`, so only one request at a time changes data; the capacity check and the add happen together.

**46. What is XSS and how did you handle it?**
Injecting HTML/JS through text fields. The frontend escapes all user-provided text with `esc()` before inserting into the page.

**47. Why can a self-registered user never become admin?**
`signUp()` ignores any `role` sent by the browser and always creates a `STUDENT`; only an admin can create other roles.

## F. Design decisions and extension
**48. What happens to registrations and duties when an event is deleted?**
They are removed too (cascade) in `deleteEvent()`, so no orphan data remains.

**49. What would you improve with more time?**
E-mail notifications, QR-code attendance, PDF certificates, database (MySQL) with JDBC/Spring Boot, HTTPS, bcrypt password hashing, pagination.

**50. How did you test the project?**
65 automated API tests (permissions, validations, workflows), a restart test to prove persistence, 43 simulated-browser checks, and 20 manual test cases. The automated tests are in `tests/ApiTest.java`.

## Bonus tricky questions
**51. Can a Volunteer register for events?** Yes – `Volunteer extends Student`, so `getPermissions()` includes `REGISTER_EVENT`. They also get `VIEW_DUTIES`.
**52. What if the admin changes a student into a coordinator?** `updateUser()` builds a new object through `Person.fromMap()` with the new role, replaces it in the HashMap and removes registrations/duties that no longer apply.
**53. What happens if two people use the app at the same moment?** The server has 8 threads; `synchronized` service methods serialise changes so lists are never corrupted.
**54. Why `Map<String,Object>` instead of a JSON library?** To keep zero dependencies and to show how HashMap/ArrayList can represent JSON data.

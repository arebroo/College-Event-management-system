package service;

import model.*;
import util.FileStore;
import util.Json;

import java.io.IOException;
import java.util.*;

/**
 * DATA LAYER: keeps everything in memory (ArrayList / HashMap) and saves it
 * to JSON files in the data/ folder whenever something changes.
 *   users.json, events.json, registrations.json, duties.json
 */
public class DataStore {
    private final String dir;

    // HashMap: look up a user quickly by id (LinkedHashMap keeps insertion order)
    public final Map<String, Person> users = new LinkedHashMap<>();
    // ArrayList: simple ordered lists
    public final List<Event> events = new ArrayList<>();
    public final List<Registration> registrations = new ArrayList<>();
    public final List<Duty> duties = new ArrayList<>();

    public DataStore(String dir) { this.dir = dir; }

    private String file(String name) { return dir + "/" + name + ".json"; }

    /** Load all files. If this is the first run, create sample data and save it. */
    public void load() {
        try {
            if (!FileStore.exists(file("users"))) {
                System.out.println("No data found - creating sample data in " + dir + "/");
                SeedData.fill(this);
                saveAll();
                return;
            }
            for (Map<String, Object> m : readList("users")) {
                Person p = Person.fromMap(m);
                users.put(p.getId(), p);
            }
            for (Map<String, Object> m : readList("events")) events.add(new Event(m));
            for (Map<String, Object> m : readList("registrations")) registrations.add(new Registration(m));
            for (Map<String, Object> m : readList("duties")) duties.add(new Duty(m));
        } catch (IOException e) {
            throw new RuntimeException("Could not read data files: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String name) throws IOException {
        if (!FileStore.exists(file(name))) return new ArrayList<>();
        return (List<Map<String, Object>>) (List<?>) Json.parse(FileStore.read(file(name)));
    }

    private void saveList(String name, Collection<? extends Storable> items) {
        List<Object> out = new ArrayList<>();
        for (Storable s : items) out.add(s.toMap());   // polymorphism: each class has its own toMap()
        try {
            FileStore.write(file(name), Json.pretty(out));
        } catch (IOException e) {
            throw new RuntimeException("Could not save " + name + ": " + e.getMessage(), e);
        }
    }

    public void saveUsers() { saveList("users", users.values()); }
    public void saveEvents() { saveList("events", events); }
    public void saveRegistrations() { saveList("registrations", registrations); }
    public void saveDuties() { saveList("duties", duties); }

    public void saveAll() { saveUsers(); saveEvents(); saveRegistrations(); saveDuties(); }

    /** Next id like "e7": highest existing number + 1. */
    public String newId(String prefix, Collection<? extends BaseEntity> items) {
        int max = 0;
        for (BaseEntity r : items) {
            try { max = Math.max(max, Integer.parseInt(r.getId().substring(prefix.length()))); }
            catch (Exception ignored) { }
        }
        return prefix + (max + 1);
    }
}

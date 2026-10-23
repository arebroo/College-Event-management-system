package model;

import java.util.Map;

/** INTERFACE: anything that can be saved to a JSON file must be able to turn itself into a Map. */
public interface Storable {
    Map<String, Object> toMap();
}

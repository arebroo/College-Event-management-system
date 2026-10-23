package model;

import java.util.List;

/** INTERFACE: every kind of user provides its own dashboard title and menu. */
public interface DashboardProvider {
    String getDashboardTitle();
    List<String> getMenu();
}

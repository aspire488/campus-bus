package com.collegebus.model;

import java.util.Map;

/**
 * ABSTRACTION: a contract for anything the system can show on screen.
 * Student, Admin, Bus, Route and Reservation each implement it differently (POLYMORPHISM),
 * so tables and the web UI can treat them all as "Displayable".
 */
public interface Displayable {
    String displayDetails();          // one-line human readable summary
    Map<String, Object> toMap();      // structured data, converted to JSON for the HTML/JS layer
}

package com.collegebus;

import com.collegebus.util.DatabaseConnection;

/** Shared start-up for the database test classes: the isolated college_bus_test database, rebuilt if a test left it damaged. */
public final class TestDb {
    private TestDb() { }

    /** Null when the test database is ready, else why it can't be used (the test classes then skip). */
    public static String prepare() {
        System.setProperty("db.name", "college_bus_test");
        try {
            try {
                DatabaseConnection.ensureDatabase();
            } catch (IllegalStateException damaged) {        // e.g. a table dropped by a test: only a *_test database is rebuilt
                DatabaseConnection.loadSchema();
            }
            return null;
        } catch (Exception e) {
            // only "no server" / "no access" skips the database tests; any other error (e.g. an upgrade bug) must fail them
            if (DatabaseConnection.isConnectionProblem(e) || DatabaseConnection.isAccessProblem(e)) return "MySQL not reachable: " + e.getMessage();
            throw new IllegalStateException("The test database could not be prepared: " + e.getMessage(), e);
        }
    }
}

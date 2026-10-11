package com.collegebus.service;

import com.collegebus.model.Student;
import com.collegebus.model.User;
import com.collegebus.util.DatabaseConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Prepares the presentation database once: the fictional demo data, an administrator, and fresh random passwords that are
 * written only to a file in the user's settings folder (never to the repository or the console).
 * It works only on the database named campus_bus_demo, and does nothing if that database already has an administrator,
 * so running it again never duplicates records or erases bookings.
 */
public final class DemoSetup {
    public static final String DATABASE = "campus_bus_demo";
    static final String[] FEATURED = {"student@college.example.com", "ananya@college.example.com", "rahul@college.example.com"};
    private static final SecureRandom RANDOM = new SecureRandom();

    private DemoSetup() { }

    /** Only the dedicated demo database may be filled with demo data. */
    public static boolean isDemoDatabase(String name) { return DATABASE.equals(name); }

    /** Easy to type, hard to guess: e.g. "seat-4821-kz". */
    public static String newPassword() {
        String[] words = {"bus", "seat", "route", "stop", "pass", "ride", "campus", "ticket"};
        String letters = "abcdefghjkmnpqrstuvwxyz";
        return words[RANDOM.nextInt(words.length)] + "-" + (1000 + RANDOM.nextInt(9000)) + "-"
            + letters.charAt(RANDOM.nextInt(letters.length())) + letters.charAt(RANDOM.nextInt(letters.length()));
    }

    /** Returns the accounts file. Throws if the configured database is not the demo database. */
    public static Path run() throws Exception {
        String db = DatabaseConnection.databaseName();
        if (!isDemoDatabase(db))
            throw new IllegalStateException("Demo setup only works on the database " + DATABASE + " (settings point to " + db + "). Nothing was changed.");
        Path accounts = DatabaseConnection.userSettingsFolder().resolve("demo-accounts.txt");
        System.setProperty("demo", "true");                      // a new, empty demo database gets the fictional data
        DatabaseConnection.ensureDatabase();
        CollegeBusSystem sys = new CollegeBusSystem();
        if (!sys.needsSetup()) {
            System.out.println("The demo database is already set up; nothing was changed. Accounts: " + accounts);
            return accounts;
        }
        String adminPassword = newPassword(), others = newPassword();
        sys.setUpInstitution("Demo College of Engineering", "demo.admin", "Demo Administrator", adminPassword);
        sys.students().setAllPasswords(User.hash(others));                      // one shared password for the other students
        StringBuilder text = new StringBuilder("CAMPUS BUS demo accounts (fictional data, database " + DATABASE + "). Keep this file private.\n\n")
            .append("Admin    (login screen > Admin):    demo.admin    password: ").append(adminPassword).append("\n\n")
            .append("Students (login screen > Student):\n");
        for (String login : FEATURED) {
            Student s = sys.students().findByLogin(login);
            if (s == null) continue;
            String pw = newPassword();
            s.setPasswordHash(User.hash(pw));
            sys.students().savePassword(s);
            text.append(String.format("  %-18s %-22s password: %s%n", s.getName(), login, pw));
        }
        text.append("  every other demo student             password: ").append(others).append("\n\n")
            .append("To start over, run reset-demo.cmd (it deletes only the ").append(DATABASE).append(" database).\n");
        Files.createDirectories(accounts.getParent());
        Files.writeString(accounts, text, StandardCharsets.UTF_8);
        System.out.println("Demo database ready. Accounts: " + accounts);
        return accounts;
    }
}

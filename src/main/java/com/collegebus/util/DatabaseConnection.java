package com.collegebus.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * JDBC entry point: every DAO asks this class for a Connection.
 * Each setting (db.host, db.name, db.user, db.password) is read from, in order:
 * a -D system property, an environment variable (CAMPUS_BUS_DB_HOST ...), db.properties.
 * db.properties is looked for in the current folder, then in the project folder the app was started from.
 * Without any of these the app refuses to guess (no silent root@localhost). No password is written in the source code.
 */
public final class DatabaseConnection {
    private static final Properties FILE = new Properties();
    private static final List<Path> LOOKED_IN = candidates(Path.of("").toAbsolutePath(), codeHome());
    private static boolean fileFound;

    static {
        for (Path p : LOOKED_IN) {
            if (!Files.isRegularFile(p)) continue;
            try (InputStream in = Files.newInputStream(p)) {
                FILE.load(in);
                fileFound = true;
                System.out.println("Database settings: " + p);
                break;
            } catch (Exception e) {
                System.out.println("Could not read " + p + ": " + e.getMessage());
            }
        }
        if (!fileFound) System.out.println("db.properties not found in " + LOOKED_IN + " - using -D options / environment variables");
    }

    /** db.properties in the working folder, then in the project folder (two levels above target/classes or target/app.jar). */
    public static List<Path> candidates(Path workingDir, Path codeHome) {
        List<Path> list = new ArrayList<>(List.of(workingDir.resolve("db.properties").normalize()));
        if (codeHome != null && codeHome.getParent() != null && codeHome.getParent().getParent() != null) {
            Path project = codeHome.getParent().getParent().resolve("db.properties").normalize();
            if (!list.contains(project)) list.add(project);
        }
        return list;
    }

    private static Path codeHome() {
        try { return Path.of(DatabaseConnection.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath(); }
        catch (Exception e) { return null; }
    }

    /** True when the user gave database settings somewhere: db.properties, -Ddb.user or CAMPUS_BUS_DB_USER. */
    static boolean configured() {
        return fileFound || System.getProperty("db.user") != null || System.getenv("CAMPUS_BUS_DB_USER") != null;
    }

    static String missingSettings() {
        return "No database settings: copy db.properties.example to db.properties in " + LOOKED_IN.get(LOOKED_IN.size() - 1).getParent()
            + " and fill in your MySQL user and password.";
    }

    private DatabaseConnection() { }

    static String setting(String key, String fallback) {
        String env = System.getenv("CAMPUS_BUS_" + key.replace('.', '_').toUpperCase());
        return System.getProperty(key, env != null ? env : FILE.getProperty(key, fallback));
    }

    public static String databaseName() { return setting("db.name", "college_bus_db"); }

    private static Connection open(String database) throws SQLException {
        if (!configured()) throw new SQLException(missingSettings(), "08001");
        String url = "jdbc:mysql://" + setting("db.host", "localhost:3306") + "/" + database + "?connectTimeout=3000";
        return DriverManager.getConnection(url, setting("db.user", "root"), setting("db.password", ""));
    }

    /** A message a normal user can act on: server down vs. wrong user/password/permissions (no stack trace, no password). */
    public static String explain(Throwable e) {
        if (!configured()) return missingSettings();
        String state = e instanceof SQLException s && s.getSQLState() != null ? s.getSQLState() : "";
        if (state.startsWith("28") || state.equals("42000"))
            return "MySQL refused access. Check db.user, db.password and db.name in db.properties.";
        return "Can't reach MySQL. Start it, then check db.properties.";
    }

    public static Connection getConnection() throws SQLException {
        return open(databaseName());
    }

    /** Creates the database from schema.sql the first time the app runs on a new computer. */
    public static void ensureDatabase() throws Exception {
        try (Connection c = open(""); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName() + "`");
        }
        try (Connection c = getConnection(); ResultSet rs = c.getMetaData().getTables(databaseName(), null, "reservations", null)) {
            if (!rs.next()) {
                boolean demo = Boolean.getBoolean("demo");               // demo rows have public passwords: only on request
                run("/schema.sql");
                if (demo) run("/demo-data.sql");
                System.out.println("Created " + databaseName() + (demo ? " with demo data." : " (empty: first-run setup will start)."));
                return;
            }
        }
        upgrade();
    }

    /**
     * Brings a database made by an older version up to date. Adds, never drops data, and every step checks before it acts,
     * so a step that failed once is simply tried again on the next launch.
     */
    static void upgrade() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS settings (name VARCHAR(40) PRIMARY KEY, value VARCHAR(120) NOT NULL)");
            if (!hasColumn(c, "buses", "return_departure")) st.execute("ALTER TABLE buses ADD COLUMN return_departure TIME NULL AFTER arrival");
            if (!hasColumn(c, "reservations", "direction"))
                st.execute("ALTER TABLE reservations ADD COLUMN direction ENUM('OUT','RETURN') NOT NULL DEFAULT 'OUT' AFTER travel_date");
            if (!hasColumn(c, "reservations", "stop")) st.execute("ALTER TABLE reservations ADD COLUMN stop VARCHAR(40) NULL AFTER seat_no");
            if (!columnType(c, "routes", "stops").equals("varchar(1500)")) st.execute("ALTER TABLE routes MODIFY stops VARCHAR(1500) NOT NULL");
            if (!columnNullable(c, "routes", "distance_km")) st.execute("ALTER TABLE routes MODIFY distance_km DECIMAL(5,1) NULL");
            for (String t : new String[]{"students", "admins"})
                if (!columnType(c, t, "password").equals("varchar(255)")) st.execute("ALTER TABLE " + t + " MODIFY password VARCHAR(255) NOT NULL");
            String seatKey = indexColumns(c, "uq_seat_per_trip"), studentKey = indexColumns(c, "uq_student_per_trip");
            if (!seatKey.equals("bus_id,travel_date,direction,seat_no,active"))
                st.execute("ALTER TABLE reservations " + (seatKey.isEmpty() ? "" : "DROP INDEX uq_seat_per_trip, ")
                        + "ADD UNIQUE KEY uq_seat_per_trip (bus_id, travel_date, direction, seat_no, active)");
            if (!studentKey.contains("direction")) {
                String drop = studentKey.isEmpty() ? "" : "DROP INDEX uq_student_per_trip, ";       // a missing key is simply added
                try {
                    st.execute("ALTER TABLE reservations " + drop + "ADD UNIQUE KEY uq_student_per_trip (student_id, travel_date, direction, active)");
                } catch (SQLException duplicates) {
                    // old data where one student holds two seats the same day (old return buses were separate buses):
                    // keep the per-bus key in the database; ReservationService still refuses new double bookings
                    st.execute("ALTER TABLE reservations " + drop + "ADD UNIQUE KEY uq_student_per_trip (student_id, bus_id, travel_date, direction, active)");
                }
            }
        }
    }

    private static String columnType(Connection c, String table, String column) throws SQLException {
        return String.valueOf(column(c, table, column, "COLUMN_TYPE"));
    }

    private static boolean columnNullable(Connection c, String table, String column) throws SQLException {
        return "YES".equals(column(c, table, column, "IS_NULLABLE"));
    }

    private static Object column(Connection c, String table, String column, String what) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + what + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            ps.setString(1, databaseName());                    // "what" is one of two fixed column names above, never user input
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(databaseName(), null, table, column)) {
            return rs.next();
        }
    }

    /** "col1,col2,..." of a reservations index, or "" if it does not exist. */
    private static String indexColumns(Connection c, String index) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'reservations' AND INDEX_NAME = ?")) {
            ps.setString(1, databaseName());
            ps.setString(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                String columns = rs.getString(1);
                return columns == null ? "" : columns;
            }
        }
    }

    /** Drops and recreates every table, then loads the demo data - for the tests and demos, never a real database. */
    public static void loadSchema() throws Exception {
        run("/schema.sql");
        run("/demo-data.sql");
    }

    /** Runs one SQL file from the classpath (database/) in the configured database. */
    private static void run(String file) throws Exception {
        String sql;
        try (InputStream in = DatabaseConnection.class.getResourceAsStream(file)) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            for (String part : sql.replaceAll("(?m)^--.*$", "").split(";\\s*\\n")) {
                String s = part.strip();
                if (!s.isEmpty() && !s.matches("(?is)(CREATE DATABASE|USE)\\s.*")) st.execute(s);   // target database is chosen above
            }
        }
    }
}

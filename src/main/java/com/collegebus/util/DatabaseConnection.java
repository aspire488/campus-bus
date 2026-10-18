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
 * db.properties is looked for in the current folder, then in the project folder the app was started from,
 * then in the user's settings folder (%APPDATA%\CampusBus on Windows, written by setup-windows.cmd).
 * Without any of these the app refuses to guess (no silent root@localhost). No password is written in the source code.
 */
public final class DatabaseConnection {
    private static final Properties FILE = new Properties();
    private static final List<Path> LOOKED_IN = withUserSettings(candidates(Path.of("").toAbsolutePath(), codeHome()));
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

    /** Where setup-windows.cmd keeps this user's settings: %APPDATA%\CampusBus, or ~/.campus-bus elsewhere. */
    public static Path userSettingsFolder() {
        String appData = System.getenv("APPDATA");
        return appData != null ? Path.of(appData, "CampusBus") : Path.of(System.getProperty("user.home"), ".campus-bus");
    }

    private static List<Path> withUserSettings(List<Path> list) {
        Path user = userSettingsFolder().resolve("db.properties");
        if (!list.contains(user)) list.add(user);
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
        return "No database settings. On Windows run setup-windows.cmd once; or copy db.properties.example to db.properties in "
            + LOOKED_IN.get(Math.max(0, LOOKED_IN.size() - 2)).getParent() + " and fill in your MySQL user and password.";
    }

    /** The app never logs in as MySQL root without a password, whatever the settings say. */
    public static void checkLogin(String user, String password) throws SQLException {
        if (user == null || user.isBlank()) throw new SQLException(missingSettings(), "08001");
        if (user.equalsIgnoreCase("root") && password.isEmpty())
            throw new SQLException("Refusing to log in as MySQL root without a password. Set db.user/db.password to a dedicated MySQL user.", "28000");
    }

    private DatabaseConnection() { }

    /** One setting (db.*, mail.*): -D option, then CAMPUS_BUS_* environment variable, then db.properties. */
    public static String setting(String key, String fallback) {
        String env = System.getenv("CAMPUS_BUS_" + key.replace('.', '_').toUpperCase());
        return System.getProperty(key, env != null ? env : FILE.getProperty(key, fallback));
    }

    public static String databaseName() { return setting("db.name", "college_bus_db"); }

    private static Connection open(String database) throws SQLException { return open(database, 15000); }

    /** {@code socketTimeoutMs}: a hung server fails after this instead of freezing the app; 0 = wait (setup and upgrades only). */
    private static Connection open(String database, int socketTimeoutMs) throws SQLException {
        if (!configured()) throw new SQLException(missingSettings(), "08001");
        String user = setting("db.user", null), password = setting("db.password", "");
        checkLogin(user, password);
        // a row lock is waited for at most 10 s (InnoDB default: 50 s), so a busy row is reported as "busy" before the 15 s socket
        // timeout would make it look like a dead server (and the abandoned statement would keep holding its locks)
        String url = "jdbc:mysql://" + setting("db.host", "localhost:3306") + "/" + database + "?connectTimeout=3000&socketTimeout=" + socketTimeoutMs
                + "&sessionVariables=innodb_lock_wait_timeout=10";
        return DriverManager.getConnection(url, user, password);
    }

    /** A message a normal user can act on: server down vs. wrong user/password/permissions (no stack trace, no password). */
    public static String explain(Throwable e) {
        if (!configured()) return missingSettings();
        if (e instanceof SQLException s && s.getMessage() != null && s.getMessage().startsWith("Refusing")) return s.getMessage();
        if (isAccessProblem(e)) return "MySQL refused access. Check db.user, db.password and db.name in db.properties.";
        if (isBusy(e)) return "The system is busy right now, so nothing was saved. Please try again in a moment.";
        if (isConnectionProblem(e)) return "Can't reach MySQL. Start it, then check db.properties.";
        return "The database refused this change, so nothing was saved. Try again; if it keeps happening, tell the transport office.";
    }

    /** Waited too long for a row another booking or edit holds (1205), or lost a deadlock (1213): nothing was saved; try again. */
    public static boolean isBusy(Throwable e) {
        return e instanceof SQLException s && (s.getErrorCode() == 1205 || s.getErrorCode() == 1213);
    }

    /** Server down, unreachable or timed out (SQLState class 08, or no state at all from the driver). */
    public static boolean isConnectionProblem(Throwable e) {
        if (!(e instanceof SQLException s)) return false;
        String state = s.getSQLState();
        return state == null || state.startsWith("08") || s instanceof SQLTimeoutException || s instanceof SQLTransientConnectionException;
    }

    /** Wrong user/password (28000, 1045), no rights on the database (1044) or no such database (1049). */
    public static boolean isAccessProblem(Throwable e) {
        if (!(e instanceof SQLException s)) return false;
        int code = s.getErrorCode();
        return (s.getSQLState() != null && s.getSQLState().startsWith("28")) || code == 1044 || code == 1045 || code == 1049;
    }

    /** Runs {@code work} in one transaction on one connection: everything is committed, or nothing (rolled back on any error). */
    public static <T> T transaction(Work<T> work) throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException rollbackFailed) {                    // keep the real cause; MySQL rolls back on disconnect anyway
                    e.addSuppressed(rollbackFailed);
                }
                throw e;
            }
        }
    }

    public interface Work<T> { T run(Connection c) throws SQLException; }

    public static Connection getConnection() throws SQLException {
        return open(databaseName());
    }

    /** Creates the database from schema.sql the first time the app runs on a new computer. */
    public static void ensureDatabase() throws Exception {
        try (Connection c = open(""); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName() + "`");
        }
        try (Connection c = open(databaseName(), 0); ResultSet rs = c.getMetaData().getTables(databaseName(), null, "reservations", null)) {
            if (!rs.next()) {
                // schema.sql starts with DROP TABLE: run it only where no table holds a single row (a new database, or a
                // first run that stopped half-way), never over someone's data
                String withData = firstTableWithRows(c);
                if (withData != null)
                    throw new IllegalStateException("The database " + databaseName() + " holds data (table " + withData + ") but has no reservations table, "
                        + "so it was not changed. Restore the reservations table from a backup, or point db.name at a new, empty database.");
                String db = databaseName();                              // demo rows have public passwords: only on request, only demo/test
                boolean demo = Boolean.getBoolean("demo") && (db.equals("campus_bus_demo") || isDisposable(db));
                if (Boolean.getBoolean("demo") && !demo)
                    System.out.println("-Ddemo=true ignored: demo data is loaded only into campus_bus_demo or a *_test database, not " + db + ".");
                run("/schema.sql");
                if (demo) run("/demo-data.sql");
                System.out.println("Created " + databaseName() + (demo ? " with demo data." : " (empty: first-run setup will start)."));
                upToDate = true;
                return;
            }
        }
        upgrade();
        upToDate = true;
    }

    private static volatile boolean upToDate;

    /** ensureDatabase() once per run: a no-op after it has succeeded (e.g. at start-up), else tried now (MySQL was down at start). */
    public static void ensureOnce() throws SQLException {
        if (upToDate) return;
        try {
            ensureDatabase();
        } catch (SQLException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("The database could not be prepared: " + e.getMessage(), e);
        }
    }

    /**
     * Brings a database made by an older version up to date. Adds, never drops data, and every step checks before it acts,
     * so a step that failed once is simply tried again on the next launch.
     */
    static void upgrade() throws SQLException {
        try (Connection c = open(databaseName(), 0); Statement st = c.createStatement()) {   // an ALTER on a big table may take long
            // but never wait forever for a table another session is using (every other PC would queue behind it):
            // after 60 s the upgrade fails, nothing is changed, and it is tried again at the next start
            st.execute("SET SESSION lock_wait_timeout = 60");
            st.execute("CREATE TABLE IF NOT EXISTS settings (name VARCHAR(40) PRIMARY KEY, value VARCHAR(120) NOT NULL)");
            if (!hasColumn(c, "buses", "return_departure")) st.execute("ALTER TABLE buses ADD COLUMN return_departure TIME NULL AFTER arrival");
            if (!hasColumn(c, "reservations", "direction"))
                st.execute("ALTER TABLE reservations ADD COLUMN direction ENUM('OUT','RETURN') NOT NULL DEFAULT 'OUT' AFTER travel_date");
            if (!hasColumn(c, "reservations", "stop")) st.execute("ALTER TABLE reservations ADD COLUMN stop VARCHAR(40) NULL AFTER seat_no");
            if (!hasColumn(c, "reservations", "email_status"))                  // the confirmation email, tracked apart from the booking
                st.execute("ALTER TABLE reservations ADD COLUMN email_status VARCHAR(16) NULL");
            if (!columnType(c, "routes", "stops").equals("varchar(1500)")) st.execute("ALTER TABLE routes MODIFY stops VARCHAR(1500) NOT NULL");
            if (!columnNullable(c, "routes", "distance_km")) st.execute("ALTER TABLE routes MODIFY distance_km DECIMAL(5,1) NULL");
            for (String t : new String[]{"students", "admins"})
                if (!columnType(c, t, "password").equals("varchar(255)")) st.execute("ALTER TABLE " + t + " MODIFY password VARCHAR(255) NOT NULL");
            String seatKey = indexColumns(c, "uq_seat_per_trip"), studentKey = indexColumns(c, "uq_student_per_trip");
            if (!seatKey.equals("bus_id,travel_date,direction,seat_no,active"))
                st.execute("ALTER TABLE reservations " + (seatKey.isEmpty() ? "" : "DROP INDEX uq_seat_per_trip, ")
                        + "ADD UNIQUE KEY uq_seat_per_trip (bus_id, travel_date, direction, seat_no, active)");
            String fallback = "student_id,bus_id,travel_date,direction,active";
            // the real key, tried at each start until it fits - but not rebuilt for nothing while old double bookings still exist
            boolean oldDoubles = studentKey.equals(fallback) && hasDoubleBookings(st);
            if (!studentKey.equals("student_id,travel_date,direction,active") && !oldDoubles) {
                String drop = studentKey.isEmpty() ? "" : "DROP INDEX uq_student_per_trip, ";       // a missing key is simply added
                try {
                    st.execute("ALTER TABLE reservations " + drop + "ADD UNIQUE KEY uq_student_per_trip (student_id, travel_date, direction, active)");
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1062) throw e;                              // only "duplicate entry" means old double bookings
                    // old data where one student holds two seats the same day (old return buses were separate buses):
                    // keep the per-bus key; ReservationService locks the student row, so no new double booking gets in
                    if (!studentKey.equals(fallback))
                        st.execute("ALTER TABLE reservations " + drop + "ADD UNIQUE KEY uq_student_per_trip (student_id, bus_id, travel_date, direction, active)");
                }
            }
        }
    }

    /** True while one student still holds two booked seats in the same direction on the same day (from before v1.0's rule). */
    private static boolean hasDoubleBookings(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM reservations WHERE active IS NOT NULL "
                + "GROUP BY student_id, travel_date, direction HAVING COUNT(*) > 1 LIMIT 1")) {
            return rs.next();
        }
    }

    /** The name of a table in this database that has at least one row, or null if every table is empty (or there are none). */
    private static String firstTableWithRows(Connection c) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'")) {
            ps.setString(1, databaseName());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) tables.add(rs.getString(1)); }
        }
        for (String t : tables)                                  // names come from the server itself; quoted as identifiers
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT 1 FROM `" + t.replace("`", "``") + "` LIMIT 1")) {
                if (rs.next()) return t;
            }
        return null;
    }

    /** Only a database whose name ends in _test may be wiped and refilled by loadSchema(). */
    public static boolean isDisposable(String database) { return database != null && database.endsWith("_test"); }

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

    /** Drops and recreates every table, then loads the demo data - for the tests only, never a real database. */
    public static void loadSchema() throws Exception {
        if (!isDisposable(databaseName()))
            throw new IllegalStateException("Refusing to wipe " + databaseName() + ": only a database whose name ends in _test can be reset.");
        run("/schema.sql");
        run("/demo-data.sql");
    }

    /** Runs one SQL file from the classpath (database/) in the configured database. */
    private static void run(String file) throws Exception {
        String sql;
        try (InputStream in = DatabaseConnection.class.getResourceAsStream(file)) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection c = open(databaseName(), 0); Statement st = c.createStatement()) {
            for (String part : sql.replaceAll("(?m)^--.*$", "").split(";\\s*\\n")) {
                String s = part.strip();
                if (!s.isEmpty() && !s.matches("(?is)(CREATE DATABASE|USE)\\s.*")) st.execute(s);   // target database is chosen above
            }
        }
    }
}

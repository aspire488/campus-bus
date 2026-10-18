package com.collegebus;

import static org.junit.jupiter.api.Assertions.*;

import com.collegebus.model.*;
import com.collegebus.service.CollegeBusSystem;
import com.collegebus.service.ReservationService;
import com.collegebus.util.DatabaseConnection;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/**
 * One test per defect found in the October 2026 audit (ids in the comments match the defect ledger).
 * Same isolated database as DatabaseTest (college_bus_test, reset before every test); skipped when MySQL is not reachable.
 */
class RegressionTest {
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private CollegeBusSystem sys;
    private ReservationService rs;
    private Admin admin;
    private static String unavailable;

    @BeforeAll static void useTestDatabase() {
        System.setProperty("db.name", "college_bus_test");
        unavailable = TestDb.prepare();
    }

    @BeforeEach void resetData() throws Exception {
        Assumptions.assumeTrue(unavailable == null, () -> unavailable);
        DatabaseConnection.loadSchema();
        sys = new CollegeBusSystem();
        sys.setUpInstitution("Test Institute", "Admin", "Transport Office", "Office#2026");
        rs = sys.manageReservations();
        admin = (Admin) sys.login(true, "admin", "Office#2026");
    }

    private Student joel() throws SQLException { return (Student) sys.login(false, "student@college.example.com", "student123"); }
    private Student ananya() throws SQLException { return (Student) sys.login(false, "ananya@college.example.com", "pass123"); }
    private Bus bus(String no) throws SQLException { return sys.searchBus("", "", TOMORROW, LocalTime.MIN, no).get(0); }

    private static int count(String sql, Object... p) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
            try (ResultSet r = ps.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }

    private static void sql(String s, Object... p) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(s)) {
            for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
            ps.executeUpdate();
        }
    }

    /** Runs a and b at the same moment; exceptions from either are expected outcomes, not failures. */
    private static void race(Callable<?> a, Callable<?> b) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> f = pool.invokeAll(List.of(() -> { start.await(); try { return a.call(); } catch (RuntimeException e) { return e; } },
                                                            () -> { start.await(); try { return b.call(); } catch (RuntimeException e) { return e; } }));
            for (Future<Object> x : f) x.get(60, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    // ---------- AB-1: the direction dropdown: every trip has the campus at one end ----------

    @Test void aStopAsDestinationFindsOnlyTheTripBack() throws SQLException {
        // Vyttila is a pick-up on the Tripunithura morning bus: that bus does not take anyone *to* Vyttila
        List<Bus> toVyttila = sys.searchBus("", "Vyttila", TOMORROW, LocalTime.MIN, "");
        assertFalse(toVyttila.isEmpty());
        assertTrue(toVyttila.stream().allMatch(Bus::isReturnTrip), "a morning (to-campus) trip was offered as a way to Vyttila");
        List<Bus> fromVyttila = sys.searchBus("Vyttila", "", TOMORROW, LocalTime.MIN, "");
        assertFalse(fromVyttila.isEmpty());
        assertTrue(fromVyttila.stream().noneMatch(Bus::isReturnTrip), "an evening trip was offered as boarding at Vyttila");
    }

    @Test void stopToStopJourneysAreNeverOffered() throws SQLException {
        assertTrue(sys.searchBus("Tripunithura", "Vyttila", TOMORROW, LocalTime.MIN, "").isEmpty());
        assertTrue(sys.searchBus("Vyttila", "Tripunithura", TOMORROW, LocalTime.MIN, "").isEmpty());
        assertTrue(sys.searchBus("Vyttila", "Aluva", TOMORROW, LocalTime.MIN, "").isEmpty());      // two different routes
        assertFalse(sys.searchBus("Vyttila", "College", TOMORROW, LocalTime.MIN, "").isEmpty());   // stop -> campus still works
        assertFalse(sys.searchBus("College", "Vyttila", TOMORROW, LocalTime.MIN, "").isEmpty());   // campus -> stop still works
    }

    // ---------- AB-2: one boarding point, different spellings on different buses ----------

    @Test void aStopMatchesWhateverWayTheScheduleSpellsIt() {
        Route r = Route.create(1, "Edapally Toll:0, Edapally H.S:5, College:30", 0);
        assertTrue(r.serves("edapally h s", ""));
        assertTrue(r.serves("EDAPALLY TOLL", "College"));
        assertFalse(r.serves("Edapally", ""));                                   // a different stop is still different
    }

    // ---------- AB-4 / EH-7, AB-5: the server checks direction and stop itself ----------

    @Test void anUnknownDirectionIsRefused() throws SQLException {
        Student a = joel();
        int cb01 = bus("CB-01").getBusId();
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, cb01, TOMORROW, "xyz", 30, "Edappally"));
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, cb01, TOMORROW, "return", 30, "Edappally"));
        assertEquals(0, count("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND seat_no=30", cb01));
    }

    @Test void aNewBookingNeedsAStop() throws SQLException {
        Student a = joel();
        int cb01 = bus("CB-01").getBusId();
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, cb01, TOMORROW, "OUT", 30, null));
        assertEquals(0, count("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND seat_no=30", cb01));
    }

    // ---------- D-1: first-run setup must never wipe a database that holds data ----------

    @Test void aMissingReservationsTableNeverWipesTheDatabase() throws Exception {
        int students = count("SELECT COUNT(*) FROM students"), buses = count("SELECT COUNT(*) FROM buses");
        sql("DROP TABLE reservations");
        try {
            assertThrows(IllegalStateException.class, DatabaseConnection::ensureDatabase);
            assertEquals(students, count("SELECT COUNT(*) FROM students"));
            assertEquals(buses, count("SELECT COUNT(*) FROM buses"));
        } finally {
            DatabaseConnection.loadSchema();             // leave the test database whole for the test classes that run next
        }
    }

    @Test void aFirstRunThatStoppedHalfWayCanFinish() throws Exception {       // I-7: only empty tables left behind
        sql("DROP TABLE reservations");
        for (String t : List.of("buses", "routes", "students", "admins", "settings")) sql("DELETE FROM " + t);
        try {
            DatabaseConnection.ensureDatabase();
            assertEquals(0, count("SELECT COUNT(*) FROM reservations"));
        } finally {
            DatabaseConnection.loadSchema();
        }
    }

    // ---------- reviewer I-8: an upgrade that waits longer than the normal 15 s limit is not an outage ----------

    @Test void aSlowUpgradeFinishesInsteadOfTimingOut() throws Exception {
        sql("ALTER TABLE reservations DROP COLUMN email_status");         // an older database: upgrade() must add it back
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try (Connection blocker = DatabaseConnection.getConnection()) {
            blocker.setAutoCommit(false);
            try (Statement st = blocker.createStatement()) { st.executeQuery("SELECT * FROM reservations LIMIT 1").close(); }  // holds the table until commit
            Future<?> upgrade = ex.submit(() -> { DatabaseConnection.ensureDatabase(); return null; });
            Thread.sleep(17_000);                                          // longer than the 15 s socket timeout of normal requests
            assertFalse(upgrade.isDone(), "the upgrade gave up while waiting (it must keep waiting)");
            blocker.commit();
            upgrade.get(60, TimeUnit.SECONDS);                             // and finishes once the table is free
        } finally {
            ex.shutdownNow();
        }
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'college_bus_test' "
                + "AND TABLE_NAME = 'reservations' AND COLUMN_NAME = 'email_status'"));
    }

    // ---------- D-3 / CF-1: capacity cut racing a booking ----------

    @Test void aCapacityCutAndABookingCannotBothWin() throws Exception {
        Student a = joel();
        int id = bus("CB-02").getBusId();
        int broken = 0;
        for (int round = 0; round < 15; round++) {
            sql("DELETE FROM reservations WHERE bus_id=?", id);
            sql("UPDATE buses SET capacity=40 WHERE bus_id=?", id);
            Bus b = bus("CB-02");
            race(() -> rs.bookSeat(a, id, TOMORROW, "OUT", 40),
                 () -> { admin.updateBus(b, b.getRoute(), b.getDeparture(), b.getReturnDeparture() != null, 30, "ACTIVE"); return null; });
            if (count("SELECT capacity FROM buses WHERE bus_id=?", id) == 30
                    && count("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND seat_no=40 AND status='BOOKED'", id) == 1) broken++;
        }
        assertEquals(0, broken, "rounds that ended with seat 40 booked on a 30-seat bus");
    }

    // ---------- D-4: removing the return trip racing a return booking ----------

    @Test void removingTheReturnTripAndABookingBackCannotBothWin() throws Exception {
        Student a = joel();
        Bus first = bus("CB-02");
        int id = first.getBusId();
        LocalTime back = first.getReturnDeparture();
        assertNotNull(back);
        sql("DELETE FROM reservations WHERE travel_date=? AND direction='RETURN'", Date.valueOf(TOMORROW));   // Joel's own seat back would block him
        int broken = 0;
        for (int round = 0; round < 15; round++) {
            sql("DELETE FROM reservations WHERE bus_id=?", id);
            sql("UPDATE buses SET return_departure=? WHERE bus_id=?", Time.valueOf(back), id);
            Bus b = bus("CB-02");
            race(() -> rs.bookSeat(a, id, TOMORROW, "RETURN", 5),
                 () -> { admin.updateBus(b, b.getRoute(), b.getDeparture(), false, b.getCapacity(), "ACTIVE"); return null; });
            if (count("SELECT COUNT(*) FROM buses WHERE bus_id=? AND return_departure IS NULL", id) == 1
                    && count("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND direction='RETURN' AND status='BOOKED'", id) == 1) broken++;
        }
        assertEquals(0, broken, "rounds that ended with a booked return trip on a one-way bus");
    }

    // ---------- D-2: one student, two buses, same moment, on a database upgraded with the per-bus key ----------

    @Test void oneStudentCannotBookTwoBusesAtOnceAfterAnUpgrade() throws Exception {
        sql("ALTER TABLE reservations DROP INDEX uq_student_per_trip, ADD UNIQUE KEY uq_student_per_trip (student_id, bus_id, travel_date, direction, active)");
        Student a = joel();
        int b1 = bus("CB-01").getBusId(), b2 = bus("CB-02").getBusId();
        int broken = 0;
        for (int round = 0; round < 15; round++) {
            sql("DELETE FROM reservations WHERE student_id=?", a.getId());
            race(() -> rs.bookSeat(a, b1, TOMORROW, "OUT", 38), () -> rs.bookSeat(a, b2, TOMORROW, "OUT", 38));
            if (count("SELECT COUNT(*) FROM reservations WHERE student_id=? AND travel_date=? AND direction='OUT' AND status='BOOKED'",
                    a.getId(), Date.valueOf(TOMORROW)) > 1) broken++;
        }
        assertEquals(0, broken, "rounds that ended with one student on two morning buses");
    }

    // ---------- D-5 / CF-4: deleting a bus must not erase trips already travelled ----------

    @Test void deletingABusNeverErasesItsTravelHistory() throws Exception {
        Bus nb = new Bus();
        nb.addBus("CB-88", sys.buses().findRoutes().get(0), LocalTime.of(10, 0), LocalTime.of(10, 40), 24);
        admin.addBus(nb);
        Bus saved = bus("CB-88");
        sql("INSERT INTO reservations (student_id, bus_id, travel_date, seat_no, status) VALUES (?, ?, CURDATE() - INTERVAL 5 DAY, 4, 'BOOKED')",
                joel().getId(), saved.getBusId());
        assertThrows(IllegalStateException.class, () -> admin.removeBus(saved));
        assertEquals(1, count("SELECT COUNT(*) FROM reservations WHERE bus_id=?", saved.getBusId()));
        assertEquals(1, count("SELECT COUNT(*) FROM buses WHERE bus_id=?", saved.getBusId()));
    }

    // ---------- CF-2: a route edit must keep the stops students are booked at ----------

    @Test void aRouteEditCannotDropAStopSomeoneIsBookedAt() throws Exception {
        Bus cb01 = bus("CB-01");
        rs.bookSeat(joel(), cb01.getBusId(), TOMORROW, "OUT", 30, "Edappally");
        Route r = cb01.getRoute();
        String without = String.join(",", java.util.Arrays.stream(r.getStops().split(",")).filter(s -> !s.startsWith("Edappally")).toList());
        assertThrows(IllegalStateException.class, () -> admin.saveRoute(Route.create(r.getRouteId(), without, r.getDistanceKm())));
        assertEquals(r.getStops(), bus("CB-01").getRoute().getStops());                       // unchanged
        Route other = sys.buses().findRoutes().stream().filter(x -> !x.stopNames().contains("Edappally")).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class,                                              // nor move the bus to a route without it
                () -> admin.updateBus(cb01, other, cb01.getDeparture(), cb01.getReturnDeparture() != null, cb01.getCapacity(), "ACTIVE"));
    }

    // ---------- reviewer I-5: a route edit racing a booking at the stop it removes ----------

    @Test void aRouteEditAndABookingAtTheRemovedStopCannotBothWin() throws Exception {
        Student a = joel();
        Bus cb01 = bus("CB-01");
        Route r = cb01.getRoute();
        String original = r.getStops();
        String without = String.join(",", java.util.Arrays.stream(original.split(",")).filter(s -> !s.startsWith("Edappally")).toList());
        int broken = 0;
        for (int round = 0; round < 15; round++) {
            sql("DELETE FROM reservations WHERE student_id=? AND travel_date=? AND direction='OUT'", a.getId(), Date.valueOf(TOMORROW));
            sql("UPDATE routes SET stops=? WHERE route_id=?", original, r.getRouteId());
            race(() -> rs.bookSeat(a, cb01.getBusId(), TOMORROW, "OUT", 30, "Edappally"),
                 () -> { admin.saveRoute(Route.create(r.getRouteId(), without, r.getDistanceKm())); return null; });
            boolean dropped = count("SELECT COUNT(*) FROM routes WHERE route_id=? AND stops NOT LIKE '%Edappally%'", r.getRouteId()) == 1;
            boolean bookedThere = count("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND stop='Edappally' AND status='BOOKED'", cb01.getBusId()) == 1;
            if (dropped && bookedThere) broken++;
        }
        assertEquals(0, broken, "rounds that ended with a booking at a stop the route no longer has");
    }

    // ---------- reviewer I-6: a spelling-only route edit keeps the stops students are booked at ----------

    @Test void aSpellingOnlyRouteEditIsAllowedWithBookings() throws Exception {
        Bus cb01 = bus("CB-01");
        rs.bookSeat(joel(), cb01.getBusId(), TOMORROW, "OUT", 30, "Edappally");
        Route r = cb01.getRoute();
        admin.saveRoute(Route.create(r.getRouteId(), r.getStops().replace("Edappally", "EDAPPALLY"), r.getDistanceKm()));
        assertTrue(bus("CB-01").getRoute().getStops().contains("EDAPPALLY"));
    }

    // ---------- D-6 / EH-5: a refused change is not "the database is down" ----------

    @Test void dataErrorsAreNotReportedAsAnOutage() {
        assertFalse(DatabaseConnection.explain(new SQLException("fk", "23000", 1452)).startsWith("Can't reach"));
        assertFalse(DatabaseConnection.explain(new SQLException("too long", "22001", 1406)).startsWith("Can't reach"));
        assertFalse(DatabaseConnection.explain(new SQLException("syntax", "42000", 1064)).startsWith("MySQL refused access"));
        assertTrue(DatabaseConnection.explain(new SQLException("link failure", "08S01", 0)).startsWith("Can't reach"));
        assertTrue(DatabaseConnection.explain(new SQLException("denied", "28000", 1045)).startsWith("MySQL refused access"));
    }

    // ---------- CF-8: a damaged stored hash is a wrong password, not a crash ----------

    @Test void aDamagedPasswordHashIsJustAWrongPassword() {
        assertFalse(new Admin(0, "x.y", "Some One", "pbkdf2$abc$AAAA$AAAA").checkPassword("anything"));
        assertFalse(new Admin(0, "x.y", "Some One", "pbkdf2$600000$***$***").checkPassword("anything"));
    }

    // ---------- I-1: the page bridge must attach under any install folder (accents, spaces, '+') ----------

    @Test void theAppRecognisesItsOwnPageUnderAnyFolderName() {
        String java = "jar:file:/C:/Users/José x/C++ apps/CAMPUS BUS/app/c.jar!/web/index.html";
        String webkit = "jar:file:///C:/Users/Jos%C3%A9%20x/C++%20apps/CAMPUS%20BUS/app/c.jar!/web/index.html";
        assertTrue(com.collegebus.gui.WebPanel.sameUrl(webkit, java));
        assertTrue(com.collegebus.gui.WebPanel.sameUrl("file:///D:/campus-bus/target/classes/web/index.html", "file:/D:/campus-bus/target/classes/web/index.html"));
        assertFalse(com.collegebus.gui.WebPanel.sameUrl("https://example.org/web/index.html", java));
        assertFalse(com.collegebus.gui.WebPanel.sameUrl(null, java));
    }

    // ---------- Phase 6: an admin can replace a placeholder email; id, password and bookings stay ----------

    @Test void anAdminCanCorrectAStudentsEmailWithoutLosingAnything() throws Exception {
        Student signedIn = joel();                                    // Joel's own window, open since before the correction
        Student s = joel();
        int bookings = rs.reservationsOf(s).size();
        s.setEmail("joel.updated@example.org");
        sys.students().updateWithEmail(s);                            // the admin's Students > Edit
        Student again = (Student) sys.login(false, "joel.updated@example.org", "student123");   // same password
        assertEquals(s.getId(), again.getId());
        assertEquals(s.getRegNo(), again.getRegNo());
        assertEquals(bookings, rs.reservationsOf(again).size());
        Student other = ananya();
        other.setEmail("joel.updated@example.org");
        assertThrows(IllegalArgumentException.class, () -> sys.students().updateWithEmail(other));   // no two students share an address

        // review DB-4: Joel then saves his profile from the window opened before the correction - the new address stays
        signedIn.updateStudent("Joel Mathew", signedIn.getDepartment(), "9876500000");
        sys.students().update(signedIn);
        assertEquals(1, count("SELECT COUNT(*) FROM students WHERE student_id = ? AND email = 'joel.updated@example.org' AND phone = '9876500000'",
                s.getId()));
    }

    // ---------- final review (October 2026 closure) ----------

    @Test void aBookingAtAStopRemovedLongAgoDoesNotBlockLaterEdits() throws Exception {      // review G-1
        Bus cb01 = bus("CB-01");
        rs.bookSeat(joel(), cb01.getBusId(), TOMORROW, "OUT", 30, "Edappally");
        Route r = cb01.getRoute();                                    // as v1.0 allowed: the stop is removed from the route anyway
        sql("UPDATE routes SET stops = ? WHERE route_id = ?",
                String.join(",", java.util.Arrays.stream(r.getStops().split(",")).filter(x -> !x.startsWith("Edappally")).toList()), r.getRouteId());
        Bus now = bus("CB-01");
        admin.updateBus(now, now.getRoute(), now.getDeparture(), now.getReturnDeparture() != null, now.getCapacity(), "MAINTENANCE");
        assertEquals("MAINTENANCE", bus("CB-01").getStatus());       // used to be refused forever
    }

    @Test void movingABusChecksTheRouteAsSavedNotAStaleCopy() throws Exception {             // review A-2
        Bus cb01 = bus("CB-01");
        rs.bookSeat(joel(), cb01.getBusId(), TOMORROW, "OUT", 30, "Edappally");
        Route target = sys.buses().findRoutes().stream().filter(x -> x.getSource().equals("Tripunithura")).findFirst().orElseThrow();
        assertTrue(target.stopNames().contains("Edappally"));         // the admin's form still shows Edappally on it ...
        sql("UPDATE routes SET stops = ? WHERE route_id = ?",          // ... but another admin removed it meanwhile
                String.join(",", java.util.Arrays.stream(target.getStops().split(",")).filter(x -> !x.startsWith("Edappally")).toList()), target.getRouteId());
        assertThrows(IllegalStateException.class,
                () -> admin.updateBus(cb01, target, cb01.getDeparture(), cb01.getReturnDeparture() != null, cb01.getCapacity(), "ACTIVE"));
        assertEquals(r(cb01), bus("CB-01").getRoute().getRouteId(), "the bus must stay on its route");
    }

    private static int r(Bus b) { return b.getRoute().getRouteId(); }

    @Test void aLockedBusMeansBusyNotAnOutage() throws Exception {                          // review A-1 / F-2
        Student a = joel();
        int id = bus("CB-02").getBusId();
        try (Connection holder = DatabaseConnection.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement ps = holder.prepareStatement("SELECT bus_id FROM buses WHERE bus_id = ? FOR UPDATE")) {
                ps.setInt(1, id);
                ps.executeQuery().close();                             // another admin edit is holding this bus
            }
            long start = System.nanoTime();
            SQLException e = assertThrows(SQLException.class, () -> rs.bookSeat(a, id, TOMORROW, "OUT", 7, "Aluva"));
            long seconds = (System.nanoTime() - start) / 1_000_000_000L;
            assertTrue(DatabaseConnection.isBusy(e), "expected a lock-wait error, got " + e.getSQLState() + "/" + e.getErrorCode());
            assertFalse(DatabaseConnection.isConnectionProblem(e), "a busy row must not look like a dead server");
            assertTrue(DatabaseConnection.explain(e).contains("busy"), DatabaseConnection.explain(e));
            assertTrue(seconds < 14, "gave up only after " + seconds + " s");
            holder.rollback();
        }
        assertEquals(0, count("SELECT COUNT(*) FROM reservations WHERE bus_id = ? AND seat_no = 7 AND travel_date = ?", id, Date.valueOf(TOMORROW)));
    }

    @Test void anAbsurdIterationCountFailsFast() {                                          // review D-6
        long start = System.nanoTime();
        assertFalse(new Admin(0, "x.y", "Some One", "pbkdf2$2000000000$AAAAAAAAAAAAAAAAAAAAAA==$AAAA").checkPassword("anything"));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000, "must not run billions of iterations");
    }

    @Test void namesInIndianScriptsAreAccepted() {                                          // review F-4
        Student s = new Student();
        s.registerStudent("SCS/10002/26", "അനു മോൾ", "anu.test@example.org", "pass1", "S3 CS2", "9876543210");
        assertEquals("അനു മോൾ", s.getName());
        new Student().registerStudent("SCS/10003/26", "José Kurian", "jose.test@example.org", "pass1", "S3 CS2", "9876543211");   // combining accent
    }

    @Test void aRouteCannotListOneStopInTwoSpellings() {                                     // review F-5
        assertThrows(IllegalArgumentException.class, () -> Route.create(0, "Aluva:0, aluva.:5, College:40", 0));
    }

    @Test void aStopWhereSomeRouteEndsIsStillOfferedWhereOthersBoard() throws Exception {   // review G-9
        sys.buses().saveRoute(Route.create(0, "Kaloor:0, Thrikkakara:20", 0));           // a route that ends at Thrikkakara ...
        assertTrue(sys.allStops().contains("Thrikkakara"), "Thrikkakara is a boarding point on the Kakkanad route");
        assertFalse(sys.allStops().contains("College"));             // ... while the campus itself is still not offered
    }
}

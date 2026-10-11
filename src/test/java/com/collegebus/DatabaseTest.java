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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

/**
 * Runs the real JDBC/MySQL code against a separate database (college_bus_test), reset from schema.sql
 * before every test. Skipped (not failed) when no MySQL server is reachable.
 */
class DatabaseTest {
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private CollegeBusSystem sys;
    private ReservationService rs;

    private static String unavailable;     // why MySQL can't be used; each test is then reported as skipped

    @BeforeAll static void useTestDatabase() {
        System.setProperty("db.name", "college_bus_test");
        try {
            DatabaseConnection.ensureDatabase();
        } catch (Exception e) {
            unavailable = "MySQL not reachable: " + e.getMessage();
        }
    }

    @BeforeEach void resetData() throws Exception {
        Assumptions.assumeTrue(unavailable == null, () -> unavailable);
        DatabaseConnection.loadSchema();
        sys = new CollegeBusSystem();
        sys.setUpInstitution("Test Institute", "Admin", "Transport Office", "Office#2026");   // first-run setup
        rs = sys.manageReservations();
    }

    private Student login(String who, String pw) throws SQLException { return (Student) sys.login(false, who, pw); }
    private Student joel() throws SQLException { return login("student@college.example.com", "student123"); }
    private Student ananya() throws SQLException { return login("ananya@college.example.com", "pass123"); }
    private Bus bus(String no) throws SQLException { return sys.searchBus("", "", TOMORROW, LocalTime.MIN, no).get(0); }

    private static int count(String sql, Object... p) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
            try (ResultSet r = ps.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }

    private static int bookedRows(int busId, int seat) throws SQLException {
        return count("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND travel_date=? AND seat_no=? AND status='BOOKED'",
                busId, Date.valueOf(TOMORROW), seat);
    }

    // ---------------- Login ----------------

    @Test void loginByEmailOrRegisterNumberIsCaseInsensitive() throws SQLException {
        assertInstanceOf(Student.class, sys.login(false, "student@college.example.com", "student123"));
        assertInstanceOf(Student.class, sys.login(false, "  STUDENT@College.example.com ", "student123"));
        assertInstanceOf(Student.class, sys.login(false, "cbr24cs001", "student123"));
        assertInstanceOf(Admin.class, sys.login(true, "admin", "Office#2026"));
    }

    @Test void oldSha256PasswordsAreUpgradedAtSignIn() throws SQLException {
        assertEquals(64, count("SELECT LENGTH(password) FROM students WHERE email='student@college.example.com'"));   // seeded with SHA2()
        assertThrows(IllegalArgumentException.class, () -> sys.login(false, "student@college.example.com", "wrong"));
        assertEquals(64, count("SELECT LENGTH(password) FROM students WHERE email='student@college.example.com'"));   // a failed sign-in changes nothing
        joel();
        assertEquals(1, count("SELECT COUNT(*) FROM students WHERE email='student@college.example.com' AND password LIKE 'pbkdf2$%'"));
        assertFalse(joel().needsRehash());                                            // and the new hash works
        assertThrows(IllegalArgumentException.class, () -> sys.login(false, "student@college.example.com", "STUDENT123"));
        assertEquals(1, count("SELECT COUNT(*) FROM admins WHERE password LIKE 'pbkdf2$%'"));                  // setup stores PBKDF2
    }

    @Test void badLoginsRefusedWithClearMessages() {
        String[][] bad = {{"student@college.example.com", "wrong"}, {"nobody@college.example.com", "student123"}, {"", "student123"},
            {"student@college.example.com", ""}, {"", ""}, {"   ", "   "}, {"' OR '1'='1", "' OR '1'='1"}};
        for (String[] b : bad)
            assertThrows(IllegalArgumentException.class, () -> sys.login(false, b[0], b[1]), b[0] + "/" + b[1]);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> sys.login(false, "  ", "x"));
        assertTrue(e.getMessage().startsWith("Enter"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> sys.login(true, "student@college.example.com", "student123"));   // student is not an admin
        assertThrows(IllegalArgumentException.class, () -> sys.login(true, "admin", "OFFICE#2026"));                    // passwords are case-sensitive
    }

    // ---------------- Registration ----------------

    @Test void registrationAndDuplicates() throws Exception {
        Student s = new Student();
        s.registerStudent("CBR24CS099", "Riya Paul", "riya@college.example.com", "Secret#91x", "Civil", "9000000000");
        sys.registerStudent(s);
        assertEquals("CBR24CS099", login("riya@college.example.com", "Secret#91x").getRegNo());

        Student sameEmail = new Student();
        sameEmail.registerStudent("CBR24CS100", "Riya Two", "RIYA@college.example.com", "Secret#91x", "Civil", "9000000001");
        assertThrows(IllegalArgumentException.class, () -> sys.registerStudent(sameEmail));
        Student sameReg = new Student();
        sameReg.registerStudent("cbr24cs099", "Riya Three", "riya3@college.example.com", "Secret#91x", "Civil", "9000000002");
        assertThrows(IllegalArgumentException.class, () -> sys.registerStudent(sameReg));
        assertEquals(151, count("SELECT COUNT(*) FROM students"));                // 150 demo students + 1
    }

    @Test void profileUpdateIsSaved() throws SQLException {
        Student s = joel();
        s.updateStudent("Joel M Mathew", "Electronics", "9000000299");
        sys.students().update(s);
        Student again = joel();
        assertEquals("Joel M Mathew", again.getName());
        assertEquals("Electronics", again.getDepartment());
    }

    // ---------------- Search ----------------

    @Test void searchFilters() throws SQLException {
        assertEquals(25, sys.searchBus("", "", TOMORROW, LocalTime.MIN, "").size());          // 13 buses out + 12 of them back
        List<Bus> fromAluva = sys.searchBus("Aluva", "College", TOMORROW, LocalTime.MIN, "");
        assertEquals(List.of("CB-02", "CB-05", "CB-08", "CB-11"), fromAluva.stream().map(Bus::getBusNo).sorted().toList());
        List<Bus> home = sys.searchBus("College", "Aluva", TOMORROW, LocalTime.MIN, "");          // same buses, way back (CB-08 is one way)
        assertEquals(List.of("CB-02", "CB-05", "CB-11"), home.stream().map(Bus::getBusNo).sorted().toList());
        assertTrue(home.stream().allMatch(Bus::isReturnTrip));
        assertTrue(sys.searchBus("", "", TOMORROW, LocalTime.of(12, 0), "").stream().allMatch(b -> b.getDeparture().isAfter(LocalTime.NOON)));
        assertEquals("CB-03", sys.searchBus("", "", TOMORROW, LocalTime.MIN, "cb-03").get(0).getBusNo());
        assertEquals("CB-03", sys.searchBus("", "", TOMORROW, LocalTime.MIN, "03").get(0).getBusNo());     // partial bus number
        assertTrue(sys.searchBus("Atlantis", "", TOMORROW, LocalTime.MIN, "").isEmpty());
        assertTrue(sys.searchBus("College", "College", TOMORROW, LocalTime.MIN, "").isEmpty());
        assertTrue(sys.searchBus("", "", TOMORROW, LocalTime.MIN, "ZZ-99").isEmpty());
        assertTrue(sys.searchBus("", "", TOMORROW, LocalTime.MIN, "' OR 1=1 --").isEmpty());
    }

    @Test void availabilityCountsComeFromTheDatabase() throws SQLException {
        Bus cb01 = bus("CB-01");                                   // demo data: seats 3, 4, 8, 21 booked tomorrow
        assertEquals(36, cb01.checkAvailability());
        assertEquals(List.of(3, 4, 8, 21), rs.bookedSeats(cb01.getBusId(), TOMORROW, "OUT").stream().sorted().toList());
    }

    // ---------------- Reservation integrity ----------------

    @Test void seatTwelveCanOnlyBeHeldByOneStudentAtATime() throws SQLException {
        Student a = joel(), b = login("arjun@college.example.com", "pass123");
        int busId = bus("CB-01").getBusId();
        Reservation ra = rs.bookSeat(a, busId, TOMORROW, 12);
        IllegalStateException clash = assertThrows(IllegalStateException.class, () -> rs.bookSeat(b, busId, TOMORROW, 12));
        assertTrue(clash.getMessage().contains("taken"), clash.getMessage());
        assertEquals(1, bookedRows(busId, 12));
        assertEquals(a.getId(), count("SELECT student_id FROM reservations WHERE res_id=?", ra.getResId()));

        rs.cancelBooking(a, ra.getResId());
        assertEquals(0, bookedRows(busId, 12));
        Reservation rb = rs.bookSeat(b, busId, TOMORROW, 12);
        assertEquals(1, bookedRows(busId, 12));
        assertEquals(b.getId(), count("SELECT student_id FROM reservations WHERE res_id=?", rb.getResId()));
        assertEquals("CANCELLED", rs.find(a, ra.getResId()).getStatus());
    }

    @Test void oneSeatPerStudentEachWayEachDay() throws SQLException {
        Student a = joel();                                                     // demo: Joel already rides CB-01 back tomorrow
        int busId = bus("CB-01").getBusId();
        rs.bookSeat(a, busId, TOMORROW, 12);
        assertThrows(IllegalStateException.class, () -> rs.bookSeat(a, busId, TOMORROW, 13));
        assertThrows(IllegalStateException.class, () -> rs.bookSeat(a, busId, TOMORROW, 12));
        // every morning bus arrives at the same time, so a second bus that morning is refused too
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> rs.bookSeat(a, bus("CB-02").getBusId(), TOMORROW, 13));
        assertTrue(e.getMessage().contains("CB-01"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> rs.bookSeat(a, bus("CB-02").getBusId(), TOMORROW, "RETURN", 13));   // already going back on CB-01
        rs.bookSeat(a, busId, TOMORROW.plusDays(1), 12);                       // another date is fine
        // the database itself refuses a second same-direction seat on another bus (if two requests slip past the check together)
        assertThrows(SQLException.class, () -> {
            try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO reservations (student_id, bus_id, travel_date, direction, seat_no) VALUES (?, ?, ?, 'OUT', 30)")) {
                ps.setInt(1, a.getId()); ps.setInt(2, bus("CB-03").getBusId()); ps.setDate(3, Date.valueOf(TOMORROW));
                ps.executeUpdate();
            }
        });
    }

    @Test void invalidBookingsRefused() throws SQLException {
        Student a = joel();
        int busId = bus("CB-01").getBusId();
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, busId, TOMORROW, 0));
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, busId, TOMORROW, 41));
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, busId, LocalDate.now().minusDays(1), 5));
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, busId, LocalDate.now().plusDays(8), 5));
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, 99999, TOMORROW, 5));
        assertThrows(IllegalStateException.class, () -> rs.bookSeat(a, bus("CB-09").getBusId(), TOMORROW, 1));   // maintenance
        assertEquals(0, count("SELECT COUNT(*) FROM reservations WHERE student_id=? AND travel_date=? AND bus_id=? AND direction='OUT'", a.getId(), Date.valueOf(TOMORROW), busId));
    }

    @Test void fullBusRefusesBookings() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        Bus small = new Bus();
        small.addBus("CB-50", sys.buses().findRoutes().get(0), LocalTime.of(10, 0), LocalTime.of(10, 40), 8);
        admin.addBus(small);
        int busId = bus("CB-50").getBusId();
        List<Student> all = new java.util.ArrayList<>();                         // students with no morning seat tomorrow yet
        for (Student st : sys.students().findAll()) if (all.size() < 9 && rs.heldOn(st, TOMORROW, "OUT") == null) all.add(st);
        for (int seat = 1; seat <= 8; seat++) rs.bookSeat(all.get(seat - 1), busId, TOMORROW, seat);
        assertEquals(0, rs.getBus(busId, TOMORROW).checkAvailability());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> rs.bookSeat(all.get(8), busId, TOMORROW, 1));
        assertTrue(e.getMessage().contains("full"), e.getMessage());
    }

    @Test void cancellationRules() throws SQLException {
        Student a = joel(), b = ananya();
        Reservation r = rs.bookSeat(a, bus("CB-01").getBusId(), TOMORROW, 12);
        assertThrows(IllegalStateException.class, () -> rs.cancelBooking(b, r.getResId()));        // not hers
        assertThrows(IllegalStateException.class, () -> rs.find(b, r.getResId()));                 // cannot open her pass either
        rs.cancelBooking(a, r.getResId());
        assertThrows(IllegalStateException.class, () -> rs.cancelBooking(a, r.getResId()));        // already cancelled
        assertThrows(IllegalArgumentException.class, () -> rs.cancelBooking(a, 999999));            // no such reservation
        assertEquals(1, count("SELECT COUNT(*) FROM reservations WHERE res_id=? AND status='CANCELLED'", r.getResId()));
    }

    @Test void pastReservationCannotBeCancelled() throws SQLException {
        Reservation past = rs.reservationsOf(joel()).stream()
                .filter(r -> r.getTravelDate().isBefore(LocalDate.now()) && r.getStatus().equals("BOOKED")).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> rs.cancelBooking(null, past.getResId()));
    }

    @Test void twoStudentsClickingTheSameSeatAtOnceGetOneBooking() throws Exception {
        int busId = bus("CB-01").getBusId();
        List<Student> students = sys.students().findAll();
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> jobs = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Student s = students.get(i);
            jobs.add(pool.submit(() -> {
                start.await();
                try { new ReservationService().bookSeat(s, busId, TOMORROW, 30); ok.incrementAndGet(); } catch (IllegalStateException ignored) { }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : jobs) f.get(20, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(1, ok.get());
        assertEquals(1, bookedRows(busId, 30));
    }

    @Test void concurrentCancelsSucceedOnce() throws Exception {
        Student a = joel();
        Reservation r = rs.bookSeat(a, bus("CB-01").getBusId(), TOMORROW, 12);
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> jobs = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> {
            start.await();
            try { new ReservationService().cancelBooking(a, r.getResId()); ok.incrementAndGet(); } catch (IllegalStateException ignored) { }
            return null;
        }));
        start.countDown();
        for (Future<?> f : jobs) f.get(20, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(1, ok.get());
    }

    @Test void removedStudentGetsAMeaningfulError() throws Exception {
        Student s = new Student();
        s.registerStudent("CBR24CS099", "Riya Paul", "riya@college.example.com", "Secret#91x", "Civil", "9000000000");
        sys.registerStudent(s);
        Student riya = login("riya@college.example.com", "Secret#91x");
        sys.students().delete(riya.getId());
        Exception e = assertThrows(IllegalStateException.class, () -> rs.bookSeat(riya, bus("CB-01").getBusId(), TOMORROW, 12));
        assertFalse(e.getMessage().contains("taken"), e.getMessage());
    }

    // ---------------- Admin ----------------

    @Test void adminBusLifecycle() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        Route route = sys.buses().findRoutes().get(0);
        Bus nb = new Bus();
        nb.addBus("cb-77", route, LocalTime.of(10, 0), LocalTime.of(10, 40), 24);
        admin.addBus(nb);
        Bus saved = bus("CB-77");
        assertEquals(24, saved.getCapacity());

        Bus dup = new Bus();
        dup.addBus("CB-77", route, LocalTime.of(11, 0), LocalTime.of(11, 40), 24);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> admin.addBus(dup));
        assertTrue(e.getMessage().contains("CB-77"), e.getMessage());

        admin.manageSchedule(new Timetable(LocalTime.of(9, 30), LocalTime.of(9, 0), LocalTime.of(9, 20), LocalTime.of(17, 0)));
        assertEquals(LocalTime.of(10, 0), bus("CB-77").getDeparture());                         // pick-up times are never moved
        assertNull(bus("CB-77").getReturnDeparture());                                          // one-way buses stay one way

        Reservation onNew = rs.bookSeat(joel(), saved.getBusId(), TOMORROW, 20);
        assertThrows(IllegalArgumentException.class, () -> admin.updateBus(bus("CB-77"), route, LocalTime.of(10, 0), false, 12, "ACTIVE"));
        assertThrows(IllegalStateException.class, () -> admin.removeBus(saved));
        rs.cancelBooking(null, onNew.getResId());
        admin.removeBus(saved);
        assertTrue(sys.searchBus("", "", TOMORROW, LocalTime.MIN, "CB-77").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> admin.removeBus(saved));                 // already gone
    }

    @Test void routeRules() throws Exception {
        Route used = sys.buses().findRoutes().get(0);
        assertThrows(IllegalStateException.class, () -> sys.buses().deleteRoute(used.getRouteId()));
        sys.buses().saveRoute(Route.create(0, "Edappally:0, Kaloor:15, College:35", 12));
        Route added = sys.buses().findRoutes().stream().filter(r -> r.getSource().equals("Edappally")).findFirst().orElseThrow();
        sys.buses().saveRoute(Route.create(added.getRouteId(), "Edappally:0, Kaloor:18, College:38", 12.5));
        assertEquals(1, count("SELECT COUNT(*) FROM routes WHERE stops='Edappally:0,Kaloor:18,College:38'"));
        sys.buses().deleteRoute(added.getRouteId());
        assertEquals(5, sys.buses().findRoutes().size());
        assertTrue(sys.allStops().contains("HMT Junction"));
    }

    @Test void removingAStudentRemovesTheirReservations() throws SQLException {
        Student a = ananya();
        assertTrue(count("SELECT COUNT(*) FROM reservations WHERE student_id=?", a.getId()) > 0);
        sys.students().delete(a.getId());
        assertEquals(0, count("SELECT COUNT(*) FROM reservations WHERE student_id=?", a.getId()));
        assertThrows(IllegalArgumentException.class, () -> ananya());
    }

    // ---------------- One bus, both ways ----------------

    @Test void returnTripIsTheSameBusWithItsOwnSeats() throws Exception {
        Bus out = bus("CB-01");
        Bus back = rs.getTrip(out.getBusId(), TOMORROW, "RETURN");
        assertEquals("College → Vyttila", back.getRoute().getName());
        assertEquals(LocalTime.of(16, 30), back.getDeparture());
        assertEquals(LocalTime.of(17, 15), back.getArrival());                      // same 45 minute trip
        assertEquals(List.of(9, 18, 19), rs.bookedSeats(out.getBusId(), TOMORROW, "RETURN").stream().sorted().toList());

        Student joel = joel();                                                     // holds seat 9 on the way back tomorrow
        Reservation morning = rs.bookSeat(joel, out.getBusId(), TOMORROW, "OUT", 9);  // seat 9 in the morning is a different seat
        assertEquals("OUT", morning.getBus().getDirection());
        assertThrows(IllegalStateException.class, () -> rs.bookSeat(joel, out.getBusId(), TOMORROW, "RETURN", 30));
        assertThrows(IllegalStateException.class, () -> rs.bookSeat(ananya(), out.getBusId(), TOMORROW, "RETURN", 18));
        Reservation evening = rs.bookSeat(ananya(), out.getBusId(), TOMORROW, "RETURN", 30);
        Reservation stored = rs.find(null, evening.getResId());
        assertTrue(stored.getBus().isReturnTrip());
        assertEquals(LocalTime.of(16, 30), stored.getBus().getDeparture());

        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(joel, bus("CB-08").getBusId(), TOMORROW, "RETURN", 1));   // one way only
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        Route route = out.getRoute();
        Exception e = assertThrows(IllegalStateException.class,
                () -> admin.updateBus(bus("CB-01"), route, out.getDeparture(), false, out.getCapacity(), "ACTIVE"));
        assertTrue(e.getMessage().contains("return-trip"), e.getMessage());
        admin.manageSchedule(new Timetable(LocalTime.of(9, 0), LocalTime.of(8, 30), LocalTime.of(8, 45), LocalTime.of(17, 0)));
        assertEquals(LocalTime.of(17, 0), rs.getTrip(out.getBusId(), TOMORROW, "RETURN").getDeparture());
    }

    // ---------------- Timetable: arrive before classes, leave together ----------------

    @Test void timetableNeverMovesPickUpTimes() throws Exception {
        Bus cb01 = bus("CB-01");
        assertEquals(LocalTime.of(8, 22), cb01.timeAt("Edappally"));                // Vyttila 08:00 + 22 min, every day
        assertEquals(LocalTime.of(8, 45), cb01.getArrival());                       // this demo route prints its campus time
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        java.util.Map<String, LocalTime> before = new java.util.HashMap<>();
        for (Bus b : sys.buses().findAll(TOMORROW)) before.put(b.getBusNo(), b.getDeparture());

        admin.manageSchedule(new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 20), LocalTime.of(8, 40), LocalTime.of(16, 0)));
        assertEquals(1, count("SELECT COUNT(*) FROM settings WHERE name='arrive_from' AND value='08:20'"));
        for (Bus b : sys.buses().findAll(TOMORROW)) {
            assertEquals(before.get(b.getBusNo()), b.getDeparture(), b.getBusNo());  // pick-ups untouched
            if (b.returnTrip() != null) assertEquals(LocalTime.of(16, 0), b.returnTrip().getDeparture(), b.getBusNo());
        }
        assertNull(bus("CB-08").getReturnDeparture());                              // one way stays one way

        // a route that prints no campus time gets the window's end as a target, flagged as such
        Route printed = Route.create(0, "Vyttila:0, Edappally:22, Kalamassery, SCMS Campus", 0);
        admin.saveRoute(printed);
        Route saved = sys.buses().findRoutes().stream().filter(r -> r.getDestination().equals("SCMS Campus")).findFirst().orElseThrow();
        Bus nb = new Bus();
        nb.addBus("41", saved, LocalTime.of(8, 0), LocalTime.of(8, 40), 49);
        sys.timetable().applyTo(nb, saved, LocalTime.of(8, 0), true, 49, "ACTIVE");
        admin.addBus(nb);
        Bus b41 = bus("41");
        assertEquals(LocalTime.of(8, 40), b41.getArrival());
        assertTrue(b41.isArrivalTarget());
        assertEquals(List.of("published", "published", "estimated", "target"), b41.stopTimes().stream().map(s -> s.get("kind")).toList());

        assertThrows(IllegalArgumentException.class, () -> new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 40), LocalTime.of(8, 20), LocalTime.of(16, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 20), LocalTime.of(8, 50), LocalTime.of(16, 0)));
        assertThrows(IllegalArgumentException.class,                                // bus 41 still picks up at 08:22
                () -> admin.manageSchedule(new Timetable(LocalTime.of(8, 25), LocalTime.of(8, 0), LocalTime.of(8, 15), LocalTime.of(16, 0))));
        assertEquals(LocalTime.of(8, 40), bus("41").getArrival());                  // refused change left every bus untouched
    }

    // ---------------- First-run setup and admin accounts ----------------

    @Test void setupRunsOnlyOnceAndAdminsAreManagedInside() throws Exception {
        assertFalse(sys.needsSetup());
        assertEquals("Test Institute", sys.institutionName());
        IllegalStateException again = assertThrows(IllegalStateException.class,
                () -> sys.setUpInstitution("Evil School", "intruder", "Intruder Person", "Head#Teach7"));
        assertTrue(again.getMessage().contains("already exists"), again.getMessage());
        assertThrows(IllegalArgumentException.class, () -> sys.login(true, "intruder", "Head#Teach7"));

        Admin me = (Admin) sys.login(true, " ADMIN ", "Office#2026");
        me.addAdmin(Admin.create("Driver.Desk", "Desk Officer", "Desk#Pass77"));
        assertThrows(IllegalArgumentException.class, () -> me.addAdmin(Admin.create("driver.desk", "Someone Else", "Other#Pass7")));
        Admin desk = (Admin) sys.login(true, "driver.desk", "Desk#Pass77");
        assertEquals(2, me.viewAdmins().size());
        assertThrows(IllegalStateException.class, () -> me.removeAdmin(me));                  // not yourself
        desk.removeAdmin(me);
        assertThrows(IllegalStateException.class, () -> desk.removeAdmin(desk));
        assertThrows(IllegalArgumentException.class, () -> sys.login(true, "admin", "Office#2026"));
        assertEquals(1, me.viewAdmins().size());
    }

    @Test void freshDatabaseNeedsSetupAndAdminLoginSaysSo() throws Exception {
        DatabaseConnection.loadSchema();
        assertTrue(sys.needsSetup());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> sys.login(true, "admin", "admin123"));
        assertTrue(e.getMessage().startsWith("No administrator"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> sys.setUpInstitution(" ", "boss", "Head Teacher", "Head#Teach7"));
        assertTrue(sys.needsSetup());                                                            // nothing half-saved
    }

    @Test void removedStudentCannotSignInAndTheirSeatsAreFree() throws Exception {
        Student a = ananya();
        int bus1 = bus("CB-01").getBusId();
        assertTrue(rs.bookedSeats(bus1, TOMORROW, "OUT").contains(3));
        assertTrue(sys.students().countUpcoming(a.getId()) >= 1);          // tomorrow's seat 3 on CB-01
        sys.students().delete(a.getId());
        assertFalse(rs.bookedSeats(bus1, TOMORROW, "OUT").contains(3));
        assertThrows(IllegalArgumentException.class, () -> ananya());
        assertThrows(IllegalArgumentException.class, () -> sys.students().delete(a.getId()));   // already gone
        rs.bookSeat(joel(), bus1, TOMORROW, 3);                                                    // the freed seat can be booked
    }

    @Test void olderDatabaseIsUpgradedWithoutLosingData() throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {   // the old shape, half upgraded
            st.execute("DELETE FROM reservations");
            st.execute("ALTER TABLE reservations DROP INDEX uq_seat_per_trip, DROP INDEX uq_student_per_trip, DROP COLUMN direction, "
                    + "ADD UNIQUE KEY uq_seat_per_trip (bus_id, travel_date, seat_no, active), "
                    + "ADD UNIQUE KEY uq_student_per_trip (student_id, bus_id, travel_date, active)");
            // old versions let one student hold seats on two buses the same day (the way back was a separate bus)
            st.execute("INSERT INTO reservations (student_id, bus_id, travel_date, seat_no) VALUES (1, 1, CURDATE() + INTERVAL 1 DAY, 5), (1, 7, CURDATE() + INTERVAL 1 DAY, 6)");
        }
        DatabaseConnection.ensureDatabase();
        DatabaseConnection.ensureDatabase();                                    // safe to run again
        assertEquals(2, count("SELECT COUNT(*) FROM reservations WHERE student_id = 1 AND direction = 'OUT'"));   // nothing lost
        assertEquals(2, rs.reservationsOf(joel()).size());
        assertThrows(IllegalStateException.class, () -> rs.bookSeat(joel(), bus("CB-02").getBusId(), TOMORROW, 9));   // Java still refuses a third
        rs.bookSeat(ananya(), bus("CB-01").getBusId(), TOMORROW, "RETURN", 5);   // seat 5 back is a different seat from seat 5 out
    }

    @Test void bookingsStayReadableAfterABusStopsRunningBack() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        Bus cb10 = bus("CB-10");
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {   // only past and cancelled trips back remain
            st.execute("UPDATE reservations SET status = 'CANCELLED' WHERE bus_id = " + cb10.getBusId() + " AND direction = 'RETURN' AND travel_date >= CURDATE()");
        }
        admin.updateBus(cb10, cb10.getRoute(), cb10.getDeparture(), false, cb10.getCapacity(), "ACTIVE");
        Student rider = sys.students().findAll().stream()
                .filter(s -> { try { return count("SELECT COUNT(*) FROM reservations WHERE student_id = ? AND bus_id = ? AND direction = 'RETURN'", s.getId(), cb10.getBusId()) > 0; }
                               catch (SQLException e) { throw new RuntimeException(e); } }).findFirst().orElseThrow();
        for (Reservation r : rs.reservationsOf(rider)) assertNotNull(r.toMap().get("phase"));           // used to crash
        assertTrue(admin.viewReservations(LocalDate.now().minusDays(7), TOMORROW).stream().anyMatch(r -> r.getBus().isReturnTrip()));
    }

    @Test void removedAdminLosesAccessAndOneAlwaysRemains() throws Exception {
        Admin me = (Admin) sys.login(true, "admin", "Office#2026");
        me.addAdmin(Admin.create("second", "Second Admin", "Second#Pass7"));
        Admin second = (Admin) sys.login(true, "second", "Second#Pass7");
        second.removeAdmin(me);
        assertFalse(me.isStillAdmin());                                        // the open console checks this before every action
        assertTrue(second.isStillAdmin());
        assertThrows(IllegalStateException.class, () -> me.removeAdmin(second));   // never zero admins
        assertEquals(1, count("SELECT COUNT(*) FROM admins"));
    }

    // ---------------- The SCMS schedule (database/scms-schedule-2025-08-18.txt, from the PDF) ----------------

    @Test void scmsScheduleImportsExactlyAndOnlyOnce() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        admin.manageSchedule(new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 20), LocalTime.of(8, 40), LocalTime.of(16, 0)));
        List<String> file = java.nio.file.Files.readAllLines(java.nio.file.Path.of("database/scms-schedule-2025-08-18.txt"));
        com.collegebus.service.ScheduleImport first = new com.collegebus.service.ScheduleImport(sys, admin);
        first.run(file);
        assertEquals(21, first.inserted, first.report.toString());
        assertEquals(0, first.failed, first.report.toString());
        assertEquals(21, first.verified);

        com.collegebus.service.ScheduleImport again = new com.collegebus.service.ScheduleImport(sys, admin);
        again.run(file);                                                       // idempotent
        assertEquals(0, again.inserted);
        assertEquals(21, again.unchanged);
        assertEquals(21, count("SELECT COUNT(*) FROM buses WHERE bus_no REGEXP '^[0-9]+$'"));

        Bus b05 = bus("05");                                                   // first and last printed times kept exactly
        assertEquals(LocalTime.of(6, 50), b05.getDeparture());
        assertEquals(LocalTime.of(7, 45), b05.timeAt("HMT"));
        assertEquals(LocalTime.of(7, 35), b05.timeAt("Kakkanad Civil Station"));
        assertEquals("SCMS Campus Karukutty", b05.getRoute().getDestination());
        assertEquals(LocalTime.of(8, 40), b05.getArrival());                   // campus: the configured target, flagged as one
        assertTrue(b05.isArrivalTarget());
        assertEquals("estimated", b05.stopTimes().stream().filter(s -> s.get("name").equals("Eachamukku")).findFirst().orElseThrow().get("kind"));
        assertEquals(20, b05.getRoute().stopNames().size());                   // 19 boarding points + campus
        assertEquals(49, b05.getCapacity());
        assertEquals(LocalTime.of(16, 0), b05.getReturnDeparture());
        Bus b28 = bus("28");
        assertEquals("KMK", b28.getRoute().getSource());
        assertEquals(LocalTime.of(8, 5), b28.timeAt("Pulikkakadavu"));
        assertEquals(List.of("15", "16", "24"), sys.searchBus("", "", TOMORROW, LocalTime.MIN, "").stream()
                .filter(b -> b.getRoute().getSource().matches("Trichur|Thrissur") && !b.isReturnTrip()).map(Bus::getBusNo).sorted().toList());
        assertEquals(List.of("21", "27"), sys.searchBus("Telk", "SCMS Campus Karukutty", TOMORROW, LocalTime.MIN, "").stream().map(Bus::getBusNo).sorted().toList());
        assertTrue(first.report.stream().anyMatch(r -> r.startsWith("CHECK bus 10")), first.report.toString());   // 08:10 last pick-up

        Reservation r = rs.bookSeat(joel(), bus("27").getBusId(), TOMORROW, "OUT", 49);   // a 49-seater's last seat
        assertEquals("SCMS Campus Karukutty", r.getBus().getRoute().getDestination());
        Student free = sys.students().findAll().stream().filter(st -> { try { return rs.heldOn(st, TOMORROW, "OUT") == null; }
                catch (SQLException e) { throw new RuntimeException(e); } }).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(free, bus("27").getBusId(), TOMORROW, "OUT", 50));   // no seat 50
    }

    @Test void riderStopIsStoredAndMustBeOnTheRoute() throws Exception {
        int cb01 = bus("CB-01").getBusId();                                     // Vyttila, Palarivattom, Edappally, Kalamassery, College
        Student a = sys.students().findAll().stream().filter(st -> { try { return rs.heldOn(st, TOMORROW, "OUT") == null && rs.heldOn(st, TOMORROW, "RETURN") == null; }
                catch (SQLException e) { throw new RuntimeException(e); } }).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, cb01, TOMORROW, "OUT", 30, "College"));    // the campus is not a pick-up
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, cb01, TOMORROW, "OUT", 30, "Aluva"));      // not on this route
        Reservation morning = rs.bookSeat(a, cb01, TOMORROW, "OUT", 30, "Edappally");
        assertEquals("Edappally", rs.find(a, morning.getResId()).getStop());
        assertEquals(1, count("SELECT COUNT(*) FROM reservations WHERE res_id = ? AND stop = 'Edappally'", morning.getResId()));
        assertThrows(IllegalArgumentException.class, () -> rs.bookSeat(a, cb01, TOMORROW, "RETURN", 30, "College")); // campus again, on the way back
        Reservation evening = rs.bookSeat(a, cb01, TOMORROW, "RETURN", 30, "Kalamassery");                          // gets off at Kalamassery
        assertEquals("Kalamassery", rs.find(a, evening.getResId()).getStop());
        assertEquals("", rs.find(null, bus1Old()).toMap().get("stop"));                // bookings made before stops were recorded still read fine
    }

    private int bus1Old() throws SQLException {
        return count("SELECT MIN(res_id) FROM reservations WHERE stop IS NULL");
    }

    @Test void correctedScheduleUpdatesBusesAndDropsTheReplacedRoute() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        List<String> file = java.nio.file.Files.readAllLines(java.nio.file.Path.of("database/scms-schedule-2025-08-18.txt"));
        List<String> asPrinted = file.stream().map(l -> l.startsWith("15 |") || l.startsWith("16 |") ? l.replaceFirst("Thrissur", "Trichur") : l).toList();
        new com.collegebus.service.ScheduleImport(sys, admin).run(asPrinted);
        assertEquals("Trichur", bus("15").getRoute().getSource());
        int routes = sys.buses().findRoutes().size();
        com.collegebus.service.ScheduleImport fix = new com.collegebus.service.ScheduleImport(sys, admin);
        fix.run(file);
        assertEquals(2, fix.updated, fix.report.toString());
        assertEquals(19, fix.unchanged);
        assertEquals("Thrissur", bus("15").getRoute().getSource());
        assertEquals("Thrissur", bus("16").getRoute().getSource());
        assertEquals(routes, sys.buses().findRoutes().size());                 // two new routes, two replaced ones removed
        assertTrue(sys.buses().findRoutes().stream().noneMatch(r -> r.getSource().equals("Trichur")));
    }

    @Test void brokenScheduleLinesAreReportedNotGuessed() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        com.collegebus.service.ScheduleImport imp = new com.collegebus.service.ScheduleImport(sys, admin);
        imp.run(List.of("campus: Main Campus", "61 | Alpha 07:00; Beta 06:50", "62 | Gamma; Delta 07:10", "63 Epsilon 07:00",
                "64 | <b>Bad</b> 07:00; Zeta 07:05", "65 | Alpha 07:00; Beta 07:10", "65 | Alpha 07:00; Beta 07:20"));
        assertEquals(1, imp.inserted, imp.report.toString());                   // only the first 65
        assertEquals(5, imp.failed, imp.report.toString());
        assertEquals(1, count("SELECT COUNT(*) FROM buses WHERE bus_no IN ('61','62','63','64','65')"));
    }

    @Test void routeEditsNeverLockBusesOut() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        Route aluva = bus("CB-02").getRoute();                                  // Aluva:0 ... College:40, CB-02 leaves 08:05
        admin.saveRoute(Route.create(aluva.getRouteId(), "Aluva:0, Kalamassery:14, HMT Junction:25, College:45", 17));
        assertEquals(LocalTime.of(8, 50), bus("CB-02").getArrival());           // the printed campus time moved, and every bus still loads
        assertFalse(sys.searchBus("", "", TOMORROW, LocalTime.MIN, "").isEmpty());

        Route target = Route.create(0, "Alpha:0, Beta:10, Campus", 0);          // a route whose campus time is the office's target (08:45)
        admin.saveRoute(target);
        Route saved = sys.buses().findRoutes().stream().filter(r -> r.getSource().equals("Alpha")).findFirst().orElseThrow();
        admin.addBus(sys.timetable().newBus("71", saved, LocalTime.of(8, 0), false, 40, "ACTIVE"));
        assertThrows(IllegalArgumentException.class,                            // last pick-up would be 08:50, after the 08:45 target
                () -> admin.saveRoute(Route.create(saved.getRouteId(), "Alpha:0, Beta:50, Campus", 0)));
        assertEquals("Alpha:0,Beta:10,Campus", sys.buses().findRoutes().stream().filter(r -> r.getRouteId() == saved.getRouteId()).findFirst().orElseThrow().getStops());
    }

    @Test void aRemovedAdminCannotChangeAnything() throws Exception {
        Admin me = (Admin) sys.login(true, "admin", "Office#2026");
        me.addAdmin(Admin.create("other", "Other Admin", "Other#Pass7"));
        Admin other = (Admin) sys.login(true, "other", "Other#Pass7");
        other.removeAdmin(me);
        assertThrows(IllegalStateException.class, () -> me.addAdmin(Admin.create("sneaky", "Sneaky Person", "Sneaky#Pass7")));   // dialog opened before removal
        assertThrows(IllegalStateException.class, () -> me.manageSchedule(Timetable.DEFAULT));
        assertThrows(IllegalStateException.class, () -> me.removeBus(bus("CB-01")));
        assertEquals(0, count("SELECT COUNT(*) FROM admins WHERE username='sneaky'"));
    }

    @Test void importMatchesBusNumbersAsStored() throws Exception {
        Admin admin = (Admin) sys.login(true, "admin", "Office#2026");
        List<String> file = List.of("campus: Main Campus", "cb-61 | Alpha 07:00; Beta 07:10");
        new com.collegebus.service.ScheduleImport(sys, admin).run(file);
        com.collegebus.service.ScheduleImport again = new com.collegebus.service.ScheduleImport(sys, admin);
        again.run(file);
        assertEquals(1, again.unchanged, again.report.toString());
        assertEquals(1, again.verified);
    }

    @Test void studentsSignInWithTheirAdmissionNumber() throws Exception {
        Student s = new Student();
        s.registerStudent("scs/10001/26", "Test Student Delta", "delta@scms.test", "Secret#912x", "S1 CS1", "9000000104");
        sys.registerStudent(s);
        assertEquals("S1 CS1", ((Student) sys.login(false, "SCS/10001/26", "Secret#912x")).getDepartment());
        assertEquals("S1 CS1", ((Student) sys.login(false, " scs/10001/26 ", "Secret#912x")).getDepartment());   // as typed, any case
        Reservation r = rs.bookSeat((Student) sys.login(false, "SCS/10001/26", "Secret#912x"), bus("CB-02").getBusId(), TOMORROW, "OUT", 40, "Aluva");
        assertEquals("CS", Student.departmentOf(rs.find(null, r.getResId()).getStudentClass()));      // the admin's department filter
    }

    @Test void demoSetupRefusesAnyOtherDatabase() throws Exception {
        int students = count("SELECT COUNT(*) FROM students");
        assertThrows(IllegalStateException.class, com.collegebus.service.DemoSetup::run);       // settings point to college_bus_test
        assertEquals(students, count("SELECT COUNT(*) FROM students"));
        assertFalse(sys.needsSetup());
    }

    @Test void overviewNumbersMatchTheDatabase() throws SQLException {
        var d = sys.displayDetails();
        assertEquals(count("SELECT COUNT(*) FROM reservations x JOIN buses b ON b.bus_id=x.bus_id WHERE travel_date=CURDATE() AND x.status='BOOKED' AND b.status='ACTIVE'"),
                d.get("bookedToday"));
        assertEquals(150, d.get("students"));                                  // a realistic but light demo
        assertEquals(12, d.get("activeBuses"));
        assertTrue(((Admin) sys.login(true, "admin", "Office#2026")).viewReservations(LocalDate.now().minusDays(30), LocalDate.now().plusDays(7)).size() >= 700);
    }
}

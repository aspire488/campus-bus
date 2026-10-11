package com.collegebus;

import static org.junit.jupiter.api.Assertions.*;

import com.collegebus.model.*;
import com.collegebus.util.Json;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Validation rules inside the model classes - no database needed. */
class ModelTest {
    private static final Route ROUTE = Route.create(1, "Aluva:0, Kalamassery:14, College:40", 17);

    private static Student student(String reg, String name, String email, String pw, String phone) {
        Student s = new Student();
        s.registerStudent(reg, name, email, pw, "Civil", phone);
        return s;
    }

    private static Bus bus(int capacity) {
        Bus b = new Bus();
        b.addBus("CB-10", ROUTE, LocalTime.of(8, 0), LocalTime.of(8, 40), capacity);
        return b;
    }

    // ---------------- Student registration ----------------

    @Test void validStudentIsNormalised() {
        Student s = student(" cbr24cs099 ", "  Riya  Paul ", " Riya.Paul@College.COM ", "Secret#91x", "9000000000");
        assertEquals("CBR24CS099", s.getRegNo());
        assertEquals("riya.paul@college.com", s.getEmail());
        assertEquals("Riya  Paul".trim(), s.getName());
        assertTrue(s.checkPassword("Secret#91x"));
        assertFalse(s.checkPassword("Secret#92x"));
    }

    @Test void namesWithApostropheDotAndHyphenAreAllowed() {
        assertEquals("D'Souza-Nair K.", student("CBR24CS099", "D'Souza-Nair K.", "d@college.com", "Secret#91x", "9000000000").getName());
    }

    @ParameterizedTest @ValueSource(strings = {"", "   ", "Al", "R2D2 Robot", "<html><b>x</b>", "Name;DROP TABLE"})
    void badNamesRejected(String name) {
        assertThrows(IllegalArgumentException.class, () -> student("CBR24CS099", name, "a@college.com", "Secret#91x", "9000000000"));
    }

    @Test void overlongNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> student("CBR24CS099", "A".repeat(81), "a@college.com", "Secret#91x", "9000000000"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "   ", "plain", "a@b", "a@@college.com", "a b@college.com", "@college.com"})
    void badEmailsRejected(String email) {
        assertThrows(IllegalArgumentException.class, () -> student("CBR24CS099", "Riya Paul", email, "Secret#91x", "9000000000"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "     ", "      ", "abc12", "\t\t\t\t\t\t"})
    void weakPasswordsRejected(String pw) {
        assertThrows(IllegalArgumentException.class, () -> student("CBR24CS099", "Riya Paul", "a@college.com", pw, "9000000000"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "12345", "CBR 24CS1", "/SCS/10001", "SCS/ABC/XY", "ABCDEFGHIJKLMNOPQRSTU", "SCS/10001/26/12345678"})   // 21 characters
    void badRegisterNumbersRejected(String reg) {
        assertThrows(IllegalArgumentException.class, () -> student(reg, "Riya Paul", "a@college.com", "Secret#91x", "9000000000"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "123", "90000000001", "98470abcde", "          "})
    void badPhonesRejected(String phone) {
        assertThrows(IllegalArgumentException.class, () -> student("CBR24CS099", "Riya Paul", "a@college.com", "Secret#91x", phone));
    }

    @Test void blankDepartmentRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Student().registerStudent("CBR24CS099", "Riya Paul", "a@college.com", "Secret#91x", " ", "9000000000"));
    }

    // ---------------- Bus ----------------

    @ParameterizedTest @ValueSource(ints = {8, 40, 60})
    void capacityBoundariesAccepted(int capacity) { assertEquals(capacity, bus(capacity).checkAvailability()); }

    @ParameterizedTest @ValueSource(ints = {-5, 0, 7, 61})
    void capacityOutsideRangeRejected(int capacity) { assertThrows(IllegalArgumentException.class, () -> bus(capacity)); }

    @ParameterizedTest @ValueSource(strings = {"", "   ", "CB_01", "BUS-12345", "12345", "CB--1"})   // "12" alone is valid: schedules print "05"
    void badBusNumbersRejected(String no) {
        assertThrows(IllegalArgumentException.class, () -> new Bus().addBus(no, ROUTE, LocalTime.of(8, 0), LocalTime.of(9, 0), 40));
    }

    @Test void busNumberIsNormalised() {
        Bus b = new Bus();
        b.addBus(" cb-7 ", ROUTE, LocalTime.of(8, 0), LocalTime.of(9, 0), 40);
        assertEquals("CB-7", b.getBusNo());
    }

    @Test void timingAndRouteRulesEnforced() {
        Bus b = bus(40);
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(ROUTE, LocalTime.of(9, 0), LocalTime.of(9, 0), 40, "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(ROUTE, LocalTime.of(9, 0), LocalTime.of(8, 0), 40, "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(null, LocalTime.of(8, 0), LocalTime.of(9, 0), 40, "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(ROUTE, null, LocalTime.of(9, 0), 40, "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(ROUTE, LocalTime.of(8, 0), LocalTime.of(9, 0), 40, "BROKEN"));
    }

    @Test void capacityCannotDropBelowBookedSeat() {
        Bus b = bus(40);
        b.setBookedSeats(30);
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(ROUTE, b.getDeparture(), b.getArrival(), 20, "ACTIVE"));
        b.updateBus(ROUTE, b.getDeparture(), b.getArrival(), 30, "ACTIVE");
        assertEquals(0, b.checkAvailability());
    }

    @Test void maintenanceBusHasNoFreeSeats() {
        Bus b = bus(40);
        b.updateBus(ROUTE, b.getDeparture(), b.getArrival(), 40, "MAINTENANCE");
        assertEquals(0, b.checkAvailability());
        assertFalse(b.isActive());
    }

    // ---------------- Route ----------------

    @Test void routeParsesStopsAndTimes() {
        assertEquals(List.of("Aluva", "Kalamassery", "College"), ROUTE.stopNames());
        assertEquals(List.of(0, 14, 40), ROUTE.stopMinutes());
        assertTrue(ROUTE.serves("Aluva", "College"));
        assertTrue(ROUTE.serves("", "Kalamassery"));
        assertFalse(ROUTE.serves("College", "Aluva"));            // wrong direction
        assertFalse(ROUTE.serves("Aluva", "Aluva"));
        assertFalse(ROUTE.serves("Nowhere", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Aluva:0", "Aluva:0, College:x", "Aluva:5, College:40", "Aluva:0, College:40, Aluva:50",
        "Aluva:0, Kalamassery:30, College:20", "Aluva:0, :10, College:40", "Aluva, College:40", "Al:uva:0, College:40"})
    void badStopListsRejectedWithReadableMessage(String stops) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Route.create(0, stops, 10));
        assertFalse(e.getMessage().startsWith("For input string"), e.getMessage());
    }

    @Test void printedSchedulesWithUntimedAndSharedTimes() {
        Route r = Route.create(0, "Poothotta:0, Chakkulam:0, Karaparambu:2, Pipeline, Vyshali, EMC:12, Campus", 0);   // as a PDF prints it
        assertEquals(java.util.Arrays.asList(0, 0, 2, null, null, 12, null), r.stopMinutes());
        assertEquals(List.of(0, 0, 2, 5, 8, 12, 50), r.resolvedMinutes(50));        // gaps estimated, campus at the target
        assertFalse(r.lastStopTimed());
        assertEquals(12, r.lastTimedMinutes());
        assertEquals("Poothotta:0,Chakkulam:0,Karaparambu:2,Pipeline,Vyshali,EMC:12,Campus", r.getStops());
        assertEquals(0, r.getDistanceKm());                                         // distance not published
    }

    @Test void routeDistanceAndLengthLimits() {
        assertThrows(IllegalArgumentException.class, () -> Route.create(0, "Aluva:0, College:40", -3));
        assertThrows(IllegalArgumentException.class, () -> Route.create(0, "A".repeat(41) + ":0, College:40", 5));
        assertThrows(IllegalArgumentException.class, () -> Route.create(0, "<html><b>x</b>:0, College:40", 5));      // never rendered as HTML
        assertEquals("St. Mary's Gate (North)", Route.create(0, "St. Mary's Gate (North):0, Main Road / Bus Stand:12, Campus:30", 9).getSource());
        StringBuilder many = new StringBuilder("Stop0:0");
        for (int i = 1; i < 100; i++) many.append(", Stop number ").append(i).append(':').append(i);
        assertThrows(IllegalArgumentException.class, () -> Route.create(0, many.toString(), 5));
    }

    // ---------------- Reservation ----------------

    private static Reservation reservation(Bus bus, LocalDate date, int seat) {
        return new Reservation(0, 1, "Riya Paul", bus, date, seat, "NEW", null);
    }

    @Test void seatNumberMustExistOnBus() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertThrows(IllegalArgumentException.class, () -> reservation(bus(40), tomorrow, 0).bookSeat());
        assertThrows(IllegalArgumentException.class, () -> reservation(bus(40), tomorrow, 41).bookSeat());
        Reservation ok = reservation(bus(40), tomorrow, 40);
        ok.bookSeat();
        assertEquals("BOOKED", ok.getStatus());
    }

    @Test void bookingWindowAndDepartureRules() {
        assertThrows(IllegalArgumentException.class, () -> reservation(bus(40), LocalDate.now().plusDays(8), 1).bookSeat());
        assertThrows(IllegalStateException.class, () -> reservation(bus(40), LocalDate.now().minusDays(1), 1).bookSeat());
        reservation(bus(40), LocalDate.now().plusDays(7), 1).bookSeat();          // last bookable day
    }

    @Test void cancelOnlyOnce() {
        Reservation r = reservation(bus(40), LocalDate.now().plusDays(1), 3);
        r.bookSeat();
        r.cancelBooking();
        assertEquals("CANCELLED", r.getPhase());
        assertThrows(IllegalStateException.class, r::cancelBooking);
    }

    // ---------------- Where's my bus (timetable estimate) ----------------

    @Test void positionFollowsTheTimetable() {
        Bus b = bus(40);                                     // 08:00 from Aluva, Kalamassery at +14, College at +40
        LocalDate d = LocalDate.of(2026, 1, 5);
        Map<String, Object> before = b.estimatePosition(d, d.atTime(7, 50));
        assertEquals("WAITING", before.get("state"));
        assertEquals(10L, before.get("minutesToNext"));
        Map<String, Object> mid = b.estimatePosition(d, d.atTime(8, 7));
        assertEquals("ON_ROUTE", mid.get("state"));
        assertEquals("Aluva", mid.get("lastStop"));
        assertEquals("Kalamassery", mid.get("nextStop"));
        assertEquals("08:14", mid.get("nextTime"));
        assertEquals(7L, mid.get("minutesToNext"));
        assertEquals(0.25, (Double) mid.get("progress"), 0.001);           // halfway to stop 2 of 3
        Map<String, Object> later = b.estimatePosition(d, d.atTime(8, 14));
        assertEquals("Kalamassery", later.get("lastStop"));
        assertEquals(0.5, (Double) later.get("progress"), 0.001);
        assertEquals("ARRIVED", b.estimatePosition(d, d.atTime(8, 40)).get("state"));
        assertEquals("ARRIVED", b.estimatePosition(d, d.plusDays(1).atTime(7, 0)).get("state"));
        b.updateBus(ROUTE, LocalTime.of(8, 0), LocalTime.of(8, 40), 40, "MAINTENANCE");
        assertEquals("NOT_RUNNING", b.estimatePosition(d, d.atTime(8, 7)).get("state"));
    }

    @Test void returnTripReversesTheRoute() {
        Bus b = bus(40);
        assertNull(b.returnTrip());                                                 // one way until a return time is set
        b.updateBus(ROUTE, LocalTime.of(8, 0), LocalTime.of(8, 40), LocalTime.of(16, 0), 40, "ACTIVE");
        Bus back = b.returnTrip();
        assertEquals("College → Aluva", back.getRoute().getName());
        assertEquals(List.of("College", "Kalamassery", "Aluva"), back.getRoute().stopNames());
        assertEquals(List.of("16:00", "16:26", "16:40"), back.stopTimes().stream().map(s -> s.get("time")).toList());
        assertTrue(back.stopTimes().stream().allMatch(s -> s.get("kind").equals("estimated")));   // nothing on the way back is printed
        assertEquals(LocalTime.of(16, 40), back.getArrival());
        assertEquals("RETURN", back.toMap().get("direction"));
        LocalDate d = LocalDate.of(2026, 1, 5);
        assertEquals("Kalamassery", back.estimatePosition(d, d.atTime(16, 30)).get("lastStop"));
        assertThrows(IllegalStateException.class, () -> back.updateBus(ROUTE, LocalTime.of(8, 0), LocalTime.of(8, 40), null, 40, "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(ROUTE, LocalTime.of(8, 0), LocalTime.of(8, 40), LocalTime.of(8, 30), 40, "ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> b.updateBus(ROUTE, LocalTime.of(8, 0), LocalTime.of(8, 40), LocalTime.of(23, 50), 40, "ACTIVE"));
    }

    @Test void timetableKeepsPublishedTimesAndSetsTargets() {
        Timetable t = new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 20), LocalTime.of(8, 40), LocalTime.of(16, 0));
        Bus b = bus(40);
        t.applyTo(b, ROUTE, LocalTime.of(8, 0), true, 40, "ACTIVE");               // ROUTE prints College at +40
        assertEquals(LocalTime.of(8, 0), b.getDeparture());
        assertEquals(LocalTime.of(8, 40), b.getArrival());
        assertEquals(LocalTime.of(16, 0), b.returnTrip().getDeparture());
        assertEquals(LocalTime.of(8, 14), b.timeAt("Kalamassery"));
        assertEquals(LocalTime.of(16, 26), b.returnTrip().timeAt("Kalamassery"));   // 26 min after leaving College
        t.applyTo(b, ROUTE, LocalTime.of(8, 0), false, 40, "ACTIVE");
        assertNull(b.returnTrip());

        Route late = Route.create(0, "Kothakulangara:0, Karayamparambu:0, Companypady:0, Campus", 0);   // last pick-up 08:10
        Bus b10 = new Bus();
        b10.addBus("10", late, LocalTime.of(8, 10), LocalTime.of(8, 40), 49);
        t.applyTo(b10, late, LocalTime.of(8, 10), true, 49, "ACTIVE");
        assertEquals(LocalTime.of(8, 40), b10.getArrival());                       // the target, never a made-up travel time
        assertTrue(t.needsCheck(b10));                                              // only 10 minutes before the window opens
        Bus early = new Bus();
        early.addBus("11", late, LocalTime.of(7, 30), LocalTime.of(8, 40), 49);
        t.applyTo(early, late, LocalTime.of(7, 30), true, 49, "ACTIVE");
        assertFalse(t.needsCheck(early));
        assertFalse(t.needsCheck(b));                                               // printed campus time: nothing to check

        assertThrows(IllegalArgumentException.class, () -> new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 40), LocalTime.of(8, 20), LocalTime.of(16, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 20), LocalTime.of(8, 50), LocalTime.of(16, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Timetable(LocalTime.of(8, 45), LocalTime.of(8, 20), LocalTime.of(8, 40), LocalTime.of(8, 45)));
        assertThrows(IllegalArgumentException.class, () -> new Timetable(null, LocalTime.of(8, 20), LocalTime.of(8, 40), LocalTime.of(16, 0)));
        assertThrows(IllegalArgumentException.class, () -> t.applyTo(new Bus(), late, LocalTime.of(8, 50), true, 49, "ACTIVE"));   // leaves after the target
    }

    @Test void passwordsAreSaltedPbkdf2AndOldHashesStillWork() {
        String h1 = User.hash("Secret#91x"), h2 = User.hash("Secret#91x");
        assertTrue(h1.startsWith("pbkdf2$600000$"), h1);
        assertNotEquals(h1, h2);                                                    // random salt: same password, different hash
        Student s = new Student(1, "REG000001", "Riya Paul", "r@x.com", h1, "Civil", "9000000000");
        assertTrue(s.checkPassword("Secret#91x"));
        assertFalse(s.checkPassword("Secret#92x"));
        assertFalse(s.needsRehash());
        Student old = new Student(2, "REG000002", "Old Account", "o@x.com",         // SHA-256 of "student123", as older versions stored it
                sha256("student123"), "Civil", "9000000000");
        assertTrue(old.checkPassword("student123"));
        assertFalse(old.checkPassword("Student123"));
        assertTrue(old.needsRehash());
    }

    @Test void cheaperPbkdf2HashesAreUpgradedToo() throws Exception {
        byte[] salt = new byte[16];
        byte[] key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new javax.crypto.spec.PBEKeySpec("Secret#91x".toCharArray(), salt, 1000, 256)).getEncoded();
        String old = "pbkdf2$1000$" + java.util.Base64.getEncoder().encodeToString(salt) + "$" + java.util.Base64.getEncoder().encodeToString(key);
        Student s = new Student(3, "REG000003", "Low Cost", "l@x.com", old, "Civil", "9000000000");
        assertTrue(s.checkPassword("Secret#91x"));                                     // still accepted ...
        assertTrue(s.needsRehash());                                                // ... and re-hashed at sign-in
        assertTrue(User.hash("x").startsWith("pbkdf2$600000$"));
    }

    private static String sha256(String s) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test void busNumbersAsPrinted() {
        Bus b = new Bus();
        b.addBus("05", ROUTE, LocalTime.of(8, 0), LocalTime.of(8, 40), 49);
        assertEquals("05", b.getBusNo());
    }

    // ---------------- Administrators ----------------

    @Test void adminAccountsAreValidated() {
        Admin a = Admin.create("  Office.Head ", " Mary Jacob ", "Long#pass91");
        assertEquals("office.head", a.getUsername());
        assertEquals("Mary Jacob", a.getName());
        assertTrue(a.checkPassword("Long#pass91"));
        assertThrows(IllegalArgumentException.class, () -> Admin.create("ab", "Mary Jacob", "Long#pass91"));
        assertThrows(IllegalArgumentException.class, () -> Admin.create("bad name", "Mary Jacob", "Long#pass91"));
        assertThrows(IllegalArgumentException.class, () -> Admin.create("office", "<b>x</b>", "Long#pass91"));
        assertThrows(IllegalArgumentException.class, () -> Admin.create("office", "Mary Jacob", "short"));
        assertThrows(IllegalArgumentException.class, () -> Admin.create("office", "Mary Jacob", "        "));
    }

    @ParameterizedTest @ValueSource(strings = {"123", "abcdefgh", "ABCDEFG1!", "abcdefg1!", "Abcdefgh!", "Abcdefg12", "Password#1", "Admin#2026",
            "Aa1!Aa1!Aa1!", "Kochi#1234", "        ", "Aa1!xyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzx"})
    void weakPasswordsAreRejected(String pw) {
        assertThrows(IllegalArgumentException.class, () -> User.checkNewPassword(pw));
        assertThrows(IllegalArgumentException.class, () -> student("SCS/10001/26", "Riya Paul", "r@college.com", pw, "9000000000"));
        assertThrows(IllegalArgumentException.class, () -> Admin.create("office", "Mary Jacob", pw));
    }

    @Test void strongPasswordsAreAccepted() {
        for (String pw : new String[]{"Secret#91x", "Kochi-Bus7", "My.Seat_42"}) assertDoesNotThrow(() -> User.checkNewPassword(pw));
    }

    @Test void admissionNumbersAsIssued() {
        Student s = student(" scs/10001/26 ", "Riya Paul", "riya@college.com", "Secret#91x", "9000000000");
        assertEquals("SCS/10001/26", s.getRegNo());
        assertEquals("CBR24-CS01", student("CBR24-CS01", "Riya Paul", "r2@college.com", "Secret#91x", "9000000000").getRegNo());
    }

    @Test void semestersAreOneToEight() {
        Student s = new Student();
        s.registerStudent("SCS/10001/26", "Asha Nair", "asha@college.com", "Secret#91x", "S8 EC2", "9000000000");
        assertEquals("EC", s.getDepartmentCode());
        assertThrows(IllegalArgumentException.class, () -> s.updateStudent("Asha Nair", "S9 CS1", "9000000000"));
        assertThrows(IllegalArgumentException.class, () -> s.updateStudent("Asha Nair", "S0 CS1", "9000000000"));
    }

    @Test void departmentComesFromTheClassCode() {
        Student s = new Student(9, "REG000009", "Asha Nair", "a@x.com", "x", "S3 CS2", "9000000000");
        assertEquals("CS", s.getDepartmentCode());
        assertEquals("CT", new Student(9, "R", "Asha Nair", "a@x.com", "x", "s5ct1", "9000000000").getDepartmentCode());
        assertEquals("Grade 11 (Science)", new Student(9, "R", "Asha Nair", "a@x.com", "x", "Grade 11 (Science)", "9000000000").getDepartmentCode());
    }

    @Test void anyDepartmentOrClassNameWorks() {
        Student s = new Student();
        s.registerStudent("STU123456", "Lee Wong", "lee@school.edu", "Secret#91x", "  Grade 11 (Science) ", "9000000000");
        assertEquals("Grade 11 (Science)", s.getDepartment());
        assertThrows(IllegalArgumentException.class, () -> s.updateStudent("Lee Wong", "<script>", "9000000000"));
        assertThrows(IllegalArgumentException.class, () -> s.updateStudent("Lee Wong", "x".repeat(41), "9000000000"));
    }

    // ---------------- Polymorphism + JSON ----------------

    @Test void usersReportTheirOwnRole() {
        User s = new Student(), a = new Admin(1, "admin", "Transport Office", "x");
        assertEquals("Student", s.getRole());
        assertEquals("Admin", a.getRole());
        assertTrue(a.displayDetails().startsWith("Admin"));
    }

    @Test void jsonEscapesTextSafely() {
        String json = Json.of(Map.of("t", "a\"b\\c\nd\te\u0001"));
        assertEquals("{\"t\":\"a\\\"b\\\\c\\nd\\te\\u0001\"}", json);
        assertEquals("[1,true,null]", Json.of(java.util.Arrays.asList(1, true, null)));
    }
}

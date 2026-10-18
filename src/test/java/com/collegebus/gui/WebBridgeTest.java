package com.collegebus.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.collegebus.TestDb;
import com.collegebus.model.*;
import com.collegebus.service.CollegeBusSystem;
import com.collegebus.util.DatabaseConnection;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.*;

/**
 * The JavaScript -> Java bridge as the pages call it (reviewer finding I-9). Uses the isolated college_bus_test database;
 * email is switched off for the whole test JVM (campus.bus.mail.off), so nothing is ever sent.
 */
class WebBridgeTest {
    private static final String TOMORROW = LocalDate.now().plusDays(1).toString();
    private static String unavailable;
    private CollegeBusSystem sys;
    private Quiet window;

    /** A dashboard that is never shown: the bridge only needs it for toasts. */
    static class Quiet extends Dashboard {
        Quiet(User u, CollegeBusSystem s) { super(u, s, new String[]{"Home"}); }
        @Override protected void open(String page) { }
    }

    @BeforeAll static void setUp() {
        System.setProperty("campus.bus.mail.off", "true");          // never cleared: a late background send must stay off
        unavailable = TestDb.prepare();
    }


    @BeforeEach void reset() throws Exception {
        Assumptions.assumeTrue(unavailable == null, () -> unavailable);
        DatabaseConnection.loadSchema();
        sys = new CollegeBusSystem();
        sys.setUpInstitution("Test Institute", "Admin", "Transport Office", "Office#2026");
    }

    @AfterEach void close() { if (window != null) window.dispose(); }

    private WebBridge bridge() throws SQLException {
        Student joel = (Student) sys.login(false, "student@college.example.com", "student123");
        window = new Quiet(joel, sys);
        return new WebBridge(window, sys, joel);
    }

    private String cb01() throws SQLException {
        return String.valueOf(sys.searchBus("", "", LocalDate.now().plusDays(1), LocalTime.MIN, "CB-01").get(0).getBusId());
    }

    private static int count(String sql) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    @Test void aSavedBookingIsReportedAsBookedEvenIfShowingItFails() throws Exception {
        WebBridge b = bridge();
        String bus = cb01();
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE settings SET value = 'not a time' WHERE name = 'arrive_by'");   // breaks only the pass details, after the commit
        }
        String json = b.book(bus, TOMORROW, "OUT", "30", "Edappally");
        assertTrue(json.startsWith("{\"ok\":true"), "the seat is booked, so the page must not show an error: " + json);
        assertTrue(json.contains("\"seat\":30"), json);
        assertEquals(1, count("SELECT COUNT(*) FROM reservations WHERE student_id = 1 AND seat_no = 30 AND status = 'BOOKED'"));
    }

    @Test void aBugInARequestIsRefusedAndItsTraceIsLogged() throws Exception {
        WebBridge b = bridge();
        String bus = cb01();
        PrintStream err = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        String json;
        try {
            json = b.book(bus, TOMORROW, "OUT", "30", null);              // a null stop: NullPointerException inside book()
        } finally {
            System.setErr(err);
        }
        assertTrue(json.startsWith("{\"ok\":false") && json.contains("Something went wrong"), json);
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("java.lang.NullPointerException"), "the stack trace must be logged");
        assertEquals(0, count("SELECT COUNT(*) FROM reservations WHERE student_id = 1 AND seat_no = 30"));
    }
}

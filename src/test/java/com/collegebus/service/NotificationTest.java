package com.collegebus.service;

import static org.junit.jupiter.api.Assertions.*;

import com.collegebus.model.*;
import com.collegebus.util.DatabaseConnection;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;

/**
 * The confirmation email and the pass: the booking always stands, the email result is stored on its own, nothing real is ever sent
 * (a fake mail provider; the test JVM also runs with campus.bus.mail.off=true). Uses the isolated college_bus_test database.
 */
class NotificationTest {
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static String unavailable;
    private CollegeBusSystem sys;
    private ReservationService rs;

    /** A mail provider that records what it was asked to send, and fails on request. */
    static class Fake implements Notifier.Channel {
        final boolean configured; boolean fail;
        final List<String> sent = new ArrayList<>(), addresses = new ArrayList<>();
        byte[] attachment;
        Fake(boolean configured, boolean fail) { this.configured = configured; this.fail = fail; }
        public boolean configured() { return configured; }
        public void send(Student to, Reservation r, String institution, byte[] pdf) throws Exception {
            if (fail) throw new IOException("provider down");
            assertTrue(pdf.length > 1000 && new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"));
            sent.add(r.getCode());
            addresses.add(to.getEmail());
            attachment = pdf;
        }
    }

    @BeforeAll static void useTestDatabase() {
        System.setProperty("db.name", "college_bus_test");
        unavailable = com.collegebus.TestDb.prepare();
    }

    @BeforeEach void reset() throws Exception {
        if (unavailable != null) return;                 // tests without the database still run; the others skip in joel()
        DatabaseConnection.loadSchema();
        sys = new CollegeBusSystem();
        sys.setUpInstitution("Test Institute", "Admin", "Transport Office", "Office#2026");
        rs = sys.manageReservations();
    }

    private Student joel() throws SQLException {
        Assumptions.assumeTrue(unavailable == null, () -> unavailable);
        return (Student) sys.login(false, "student@college.example.com", "student123");
    }

    private Reservation book(Student s) throws SQLException {
        Bus cb01 = sys.searchBus("", "", TOMORROW, LocalTime.MIN, "CB-01").get(0);
        return rs.bookSeat(s, cb01.getBusId(), TOMORROW, "OUT", 30, "Edappally");
    }

    private Reservation reload(Reservation r) throws SQLException { return rs.find(null, r.getResId()); }

    @Test void theEmailResultIsStoredAndTheBookingAlwaysStands() throws Exception {
        Student s = joel();
        Reservation r = book(s);
        Fake down = new Fake(true, true);
        new Notifier(down).bookingConfirmed(s, r.getResId(), "Test Institute").get(30, TimeUnit.SECONDS);
        Reservation after = reload(r);
        assertEquals("BOOKED", after.getStatus());                      // a failed email never touches the booking
        assertEquals(Notifier.FAILED, after.getEmailStatus());
        Fake up = new Fake(true, false);
        new Notifier(up).bookingConfirmed(s, r.getResId(), "Test Institute").get(30, TimeUnit.SECONDS);   // "Send again"
        assertEquals(Notifier.SENT, reload(r).getEmailStatus());
        assertEquals(List.of(r.getCode()), up.sent);
    }

    @Test void anUnconfiguredProviderIsRecordedAsSuch() throws Exception {
        Student s = joel();
        Reservation r = book(s);
        new Notifier(new Fake(false, false)).bookingConfirmed(s, r.getResId(), "T").get(30, TimeUnit.SECONDS);
        Reservation after = reload(r);
        assertEquals(Notifier.NOT_CONFIGURED, after.getEmailStatus());
        assertEquals("BOOKED", after.getStatus());
    }

    @Test void aRetryNeverSendsTwiceNorCreatesABooking() throws Exception {
        Student s = joel();
        Reservation r = book(s);
        Fake email = new Fake(true, false);
        Notifier n = new Notifier(email);
        int rows = countRows();
        n.bookingConfirmed(s, r.getResId(), "T").get(30, TimeUnit.SECONDS);
        n.bookingConfirmed(s, r.getResId(), "T").get(30, TimeUnit.SECONDS);     // "Send again" pressed after it went out
        assertEquals(1, email.sent.size());
        assertEquals(rows, countRows(), "a retry must never create another reservation");
    }

    private static int countRows() throws SQLException {
        try (var c = DatabaseConnection.getConnection(); var st = c.createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM reservations")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String pdfText(byte[] pdf) throws IOException {
        try (var doc = Loader.loadPDF(pdf)) { return new PDFTextStripper().getText(doc); }
    }

    private MimeMessage email(Student s, Reservation r, String institution) throws Exception {
        return new SmtpMail().message(Session.getInstance(new Properties()), s, r, institution, PassPdf.create(r, institution));
    }

    private static String part(MimeMessage m, int i) throws Exception {
        MimeMultipart alt = (MimeMultipart) ((MimeMultipart) m.getContent()).getBodyPart(0).getContent();
        return (String) alt.getBodyPart(i).getContent();
    }

    @Test void theConfirmationGoesToTheAddressStoredNow() throws Exception {       // reviewer I-4
        Student signedIn = joel();                                    // the object the student's window holds since login
        Reservation r = book(signedIn);
        Student corrected = joel();
        corrected.setEmail("joel.corrected@example.org");             // an admin fixes the placeholder address meanwhile
        sys.students().updateWithEmail(corrected);                 // the admin's Students > Edit
        Fake email = new Fake(true, false);
        new Notifier(email).bookingConfirmed(signedIn, r.getResId(), "T").get(30, TimeUnit.SECONDS);
        assertEquals(List.of("joel.corrected@example.org"), email.addresses);
    }

    @Test void aConfirmationLeftPendingByAClosedAppCanBeSentAgain() throws Exception {   // reviewer I-4
        Student s = joel();
        Reservation r = book(s);
        rs.find(null, r.getResId());
        try (var c = DatabaseConnection.getConnection(); var st = c.createStatement()) {
            st.executeUpdate("UPDATE reservations SET email_status = 'PENDING' WHERE res_id = " + r.getResId());   // app closed mid-send
        }
        Fake email = new Fake(true, false);
        new Notifier(email).bookingConfirmed(s, r.getResId(), "T").get(30, TimeUnit.SECONDS);   // "Send again"
        assertEquals(1, email.sent.size());
        assertEquals(Notifier.SENT, reload(r).getEmailStatus());
    }

    @Test void aCancelledBookingGetsNoConfirmation() throws Exception {
        Student s = joel();
        Reservation r = book(s);
        rs.cancelBooking(s, r.getResId());
        Fake email = new Fake(true, false);
        new Notifier(email).bookingConfirmed(s, r.getResId(), "T").get(30, TimeUnit.SECONDS);
        assertTrue(email.sent.isEmpty());
        assertNull(reload(r).getEmailStatus());
    }

    @Test void thePassPdfCarriesEveryTripDetailOnOnePage() throws Exception {
        Reservation r = reload(book(joel()));
        byte[] pdf = PassPdf.create(r, "Test Institute");
        String text = pdfText(pdf);
        String date = TOMORROW.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH));
        for (String expected : new String[]{r.getCode(), r.getStudentName(), "CB-01", "To campus", "Edappally to College", date,
                "departs 08:22", "seat 30", "BOOKED"})
            assertTrue(text.contains(expected), "pass is missing " + expected + ":\n" + text);
        assertFalse(text.contains("?") || text.contains("..."), "caption damaged or cut:\n" + text);
        try (var doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            var images = doc.getPage(0).getResources().getXObjectNames();
            assertTrue(images.iterator().hasNext(), "the pass picture is missing");
            var img = (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) doc.getPage(0).getResources().getXObject(images.iterator().next());
            assertTrue(img.getWidth() >= 1200, "the pass picture is too small to print sharply: " + img.getWidth());   // drawn at 2x
        }
        Reservation back = reload(rs.bookSeat(joel(), r.getBus().getBusId(), TOMORROW.plusDays(1), "RETURN", 7, "Kalamassery"));
        assertEquals("College", back.boardingPoint());               // evening: on at campus, off at the student's stop
        assertEquals("Kalamassery", back.destination());
        assertTrue(pdfText(PassPdf.create(back, "Test Institute")).contains("From campus, College to Kalamassery"));
    }

    @Test void theSameBookingAlwaysGivesTheSamePass() throws Exception {
        Student s = joel();
        Reservation r = book(s);
        Fake email = new Fake(true, false);
        new Notifier(email).bookingConfirmed(s, r.getResId(), "Test Institute").get(60, TimeUnit.SECONDS);
        byte[] saved = PassPdf.create(reload(r), "Test Institute");                  // what "Save pass (PDF)" makes
        assertArrayEquals(saved, email.attachment, "the emailed pass differs from the pass saved in the app");
    }

    @Test void longNamesAndMissingFieldsStayOnThePage() throws Exception {
        Student s = joel();
        String longName = "Abcdefghijklmnopqrstuvwxyzabcdefghij Klmnopqrstuvwxyzabcdefghij Lmnopqrstuvwxyz";   // the 80-character limit
        try (var c = DatabaseConnection.getConnection(); var st = c.prepareStatement("UPDATE students SET name = ? WHERE student_id = ?")) {
            st.setString(1, longName); st.setInt(2, s.getId()); st.executeUpdate();
        }
        // Joel's demo booking back tomorrow has no stop saved (an older booking): the pass must still be complete
        Reservation old = rs.reservationsOf(s).stream().filter(x -> x.getStop() == null && "BOOKED".equals(x.getStatus())).findFirst().orElseThrow();
        byte[] pdf = PassPdf.create(reload(old), "");
        try (var doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            float pageWidth = doc.getPage(0).getMediaBox().getWidth();
            float[] right = {0};
            new PDFTextStripper() {
                @Override protected void writeString(String t, java.util.List<org.apache.pdfbox.text.TextPosition> ps) {
                    for (var p : ps) right[0] = Math.max(right[0], p.getXDirAdj() + p.getWidthDirAdj());
                }
            }.getText(doc);
            assertTrue(right[0] <= pageWidth - 20, "text runs off the page: right edge " + right[0] + " of " + pageWidth);
        }
        String text = pdfText(pdf);
        assertTrue(text.contains(old.getCode()) && text.contains("Abcdefghij"), text);
    }

    @Test void theConfirmationEmailShowsTheSavedTrip() throws Exception {
        Student s = joel();
        Reservation r = reload(book(s));
        s.setEmail("someone.test@example.org");                      // never a real inbox in automated tests
        MimeMessage m = email(s, r, "Test Institute");
        assertEquals("Your Campus Bus pass is confirmed · " + r.getCode(), m.getSubject());
        assertEquals("someone.test@example.org", m.getAllRecipients()[0].toString());   // used exactly as stored, nothing appended
        String date = TOMORROW.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH));
        for (String body : new String[]{part(m, 0), part(m, 1)})
            for (String expected : new String[]{"Hi Joel", r.getCode(), "BOOKING CONFIRMED".substring(0, 7), "Edappally", "College", "CB-01",
                    date, "08:22", "30", "To campus", "campus-bus-pass-" + r.getCode() + ".pdf", "My reservations"})
                assertTrue(body.toUpperCase().contains(expected.toUpperCase()), "email is missing " + expected);
        assertTrue(part(m, 1).contains("BOOKING CONFIRMED") && part(m, 1).startsWith("<!DOCTYPE html>"));
        MimeMultipart mixed = (MimeMultipart) m.getContent();
        assertTrue(mixed.getBodyPart(1).isMimeType("application/pdf"));
        assertEquals("campus-bus-pass-" + r.getCode() + ".pdf", mixed.getBodyPart(1).getFileName());
        try (var in = mixed.getBodyPart(1).getInputStream()) {
            assertArrayEquals(PassPdf.create(r, "Test Institute"), in.readAllBytes());   // a valid PDF, and the app's own pass
        }
    }

    @Test void studentTextCannotInjectMarkupIntoTheEmail() throws Exception {
        Student s = joel();
        Reservation r = book(s);
        String evil = "Joel <script>alert(1)</script> & \"Co\" <img src=x onerror=alert(2)>";
        try (var c = DatabaseConnection.getConnection(); var st = c.prepareStatement("UPDATE students SET name = ? WHERE student_id = ?")) {
            st.setString(1, evil); st.setInt(2, s.getId()); st.executeUpdate();
        }
        s.setName(evil);
        MimeMessage m = email(s, reload(r), "<b>Institute</b>");
        String html = part(m, 1);
        assertFalse(html.contains("<script") || html.contains("<img") || html.contains("<b>Institute"), "raw markup in the email");
        assertTrue(html.contains("&lt;script&gt;") && html.contains("&lt;b&gt;Institute"), "names are shown escaped, not dropped");
        assertEquals("Your Campus Bus pass is confirmed · " + r.getCode(), m.getSubject());       // no student text in the headers
    }

    @Test void theEmailHasTextHtmlAndThePassAttached() throws Exception {
        Student s = joel();
        Reservation r = reload(book(s));
        s.setEmail("someone.test@example.org");
        MimeMessage m = email(s, r, "Test Institute");
        MimeMultipart mixed = (MimeMultipart) m.getContent();
        MimeMultipart alt = (MimeMultipart) mixed.getBodyPart(0).getContent();
        assertTrue(alt.getBodyPart(0).isMimeType("text/plain") && alt.getBodyPart(1).isMimeType("text/html"));
        assertTrue(mixed.getBodyPart(1).isMimeType("application/pdf"));
    }

    @Test void mailIsOffWhenSwitchedOffOrNotSetUp() {
        String[] keys = {"campus.bus.mail.off", "mail.user", "mail.password"};
        String[] before = new String[keys.length];
        for (int i = 0; i < keys.length; i++) before[i] = System.getProperty(keys[i]);
        try {
            System.setProperty("campus.bus.mail.off", "true");          // the kill-switch every test run uses
            System.setProperty("mail.user", "someone@example.org");
            System.setProperty("mail.password", "not-a-real-password");
            assertFalse(new SmtpMail().configured(), "mail.off must win over any settings");
            System.setProperty("campus.bus.mail.off", "false");
            System.setProperty("mail.password", "");
            assertFalse(new SmtpMail().configured(), "no password: not set up");
            System.setProperty("mail.user", "");
            System.setProperty("mail.password", "x");
            assertFalse(new SmtpMail().configured(), "no sender: not set up");
        } finally {                                                       // restore, never clear: a cleared key would fall back to db.properties
            for (int i = 0; i < keys.length; i++)
                if (before[i] == null) System.clearProperty(keys[i]); else System.setProperty(keys[i], before[i]);
        }
    }

    @Test void onlyTestDatabasesMayBeWiped() {
        assertTrue(DatabaseConnection.isDisposable("college_bus_test"));
        assertFalse(DatabaseConnection.isDisposable("college_bus_db"));
        assertFalse(DatabaseConnection.isDisposable("campus_bus_demo"));
        assertFalse(DatabaseConnection.isDisposable(null));
    }
}

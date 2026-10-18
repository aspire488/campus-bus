package com.collegebus.service;

import com.collegebus.dao.ReservationDAO;
import com.collegebus.dao.StudentDAO;
import com.collegebus.model.Reservation;
import com.collegebus.model.Student;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The booking confirmation email (with the PDF pass). Runs only after the booking is committed, on its own background
 * thread (never the Swing or JavaFX thread, never inside a database transaction). A failed or missing mail provider
 * never touches the booking itself: the email's result is stored separately on the reservation.
 */
public class Notifier {
    public static final String SENT = "SENT", PENDING = "PENDING", FAILED = "FAILED", NOT_CONFIGURED = "NOT_CONFIGURED";

    /** How the confirmation is delivered (SmtpMail in the app, a fake in the tests). */
    public interface Channel {
        boolean configured();
        /** Throws on any failure; returning normally means the provider accepted the message. */
        void send(Student to, Reservation r, String institution, byte[] passPdf) throws Exception;
    }

    private final ReservationDAO reservations = new ReservationDAO();
    private final StudentDAO students = new StudentDAO();
    private final Channel email;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "booking-confirmation");
        t.setDaemon(true);              // never keeps the app open after the window closes
        return t;
    });

    public Notifier(Channel email) { this.email = email; }

    /** Gmail SMTP, configured by the mail.* settings (see db.properties.example). */
    public static Notifier fromSettings() { return new Notifier(new SmtpMail()); }

    /** Queues the confirmation for a booking that is already committed; returns at once. One email at a time, in order. */
    public Future<?> bookingConfirmed(Student to, int resId, String institution) {
        return worker.submit(() -> deliver(to, resId, institution));
    }

    void deliver(Student to, int resId, String institution) {
        try {
            Reservation r = reservations.findById(resId);
            if (!"BOOKED".equals(r.getStatus())) return;            // cancelled meanwhile: no confirmation for it
            if (SENT.equals(r.getEmailStatus())) return;            // "Send again" never sends a second copy
            if (!email.configured()) { reservations.setEmailStatus(resId, NOT_CONFIGURED); return; }
            Student now = students.findByLogin(to.getRegNo());       // the address as stored now (an admin may have corrected it)
            if (now != null) to = now;
            reservations.setEmailStatus(resId, PENDING);
            try {
                byte[] pass = PassPdf.create(r, institution);
                if (!"BOOKED".equals(reservations.findById(resId).getStatus())) {   // cancelled while the pass was drawn: no "confirmed" email
                    reservations.setEmailStatus(resId, null);
                    return;
                }
                email.send(to, r, institution, pass);
            } catch (Exception e) {
                reservations.setEmailStatus(resId, FAILED);
                // no address or secret in the log: just which booking and the error type
                System.out.println("Confirmation email for CBR" + resId + " failed: " + e.getClass().getSimpleName());
                return;
            }
            // outside the try: Gmail has accepted it, so a database hiccup here must never turn into FAILED and a second copy
            reservations.setEmailStatus(resId, SENT);
        } catch (Exception e) {
            System.out.println("Confirmation for CBR" + resId + " not sent: " + e.getClass().getSimpleName());
        }
    }
}

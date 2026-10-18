package com.collegebus.service;

import com.collegebus.model.Reservation;
import com.collegebus.model.Student;
import com.collegebus.util.DatabaseConnection;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.util.Properties;

/**
 * Confirmation email over SMTP with STARTTLS - Gmail by default (smtp.gmail.com:587, the full Gmail address as mail.user
 * and a Google App Password as mail.password). Settings come from -D options, CAMPUS_BUS_MAIL_* environment variables or
 * db.properties, like the database settings; nothing is hard-coded and the password is never logged.
 */
public class SmtpMail implements Notifier.Channel {

    private static String setting(String key, String fallback) { return DatabaseConnection.setting(key, fallback); }

    /** Set up = a sender and an App Password; -Dcampus.bus.mail.off=true switches email off whatever the settings say (all tests run so). */
    @Override public boolean configured() {
        if (Boolean.getBoolean("campus.bus.mail.off")) return false;
        return !setting("mail.user", "").isBlank() && !setting("mail.password", "").isBlank();
    }

    @Override public void send(Student to, Reservation r, String institution, byte[] passPdf) throws Exception {
        Properties p = new Properties();
        p.put("mail.smtp.host", setting("mail.host", "smtp.gmail.com"));
        p.put("mail.smtp.port", setting("mail.port", "587"));
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.starttls.required", "true");
        p.put("mail.smtp.ssl.checkserveridentity", "true");            // the certificate must belong to the mail server
        p.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        Session session = Session.getInstance(p);
        try (Transport t = session.getTransport("smtp")) {
            t.connect(setting("mail.user", ""), setting("mail.password", ""));
            MimeMessage m = message(session, to, r, institution, passPdf);
            t.sendMessage(m, m.getAllRecipients());
        }
    }

    /** The email itself: plain text and HTML alternatives, plus the pass as a PDF attachment. */
    MimeMessage message(Session session, Student to, Reservation r, String institution, byte[] passPdf) throws Exception {
        MimeMessage m = new MimeMessage(session);
        m.setFrom(new InternetAddress(setting("mail.from", setting("mail.user", "")), "CAMPUS BUS", "UTF-8"));
        m.setRecipient(Message.RecipientType.TO, new InternetAddress(to.getEmail(), true));
        PassPdf.Trip t = PassPdf.Trip.of(r, institution);
        m.setSubject("Your Campus Bus pass is confirmed · " + t.code(), "UTF-8");     // only the generated booking code: no user text in headers

        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart plain = new MimeBodyPart(), rich = new MimeBodyPart();
        plain.setText(plainText(t, firstName(to, t)), "UTF-8");
        rich.setContent(html(t, firstName(to, t)), "text/html; charset=UTF-8");
        alternative.addBodyPart(plain);
        alternative.addBodyPart(rich);
        MimeBodyPart body = new MimeBodyPart();
        body.setContent(alternative);
        MimeBodyPart pass = new MimeBodyPart();
        pass.setDataHandler(new jakarta.activation.DataHandler(new ByteArrayDataSource(passPdf, "application/pdf")));
        pass.setFileName("campus-bus-pass-" + r.getCode() + ".pdf");
        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(body);
        mixed.addBodyPart(pass);
        m.setContent(mixed);
        m.saveChanges();
        return m;
    }

    private static String firstName(Student to, PassPdf.Trip t) {
        String n = to.getName() != null && !to.getName().isBlank() ? to.getName() : t.student();
        return n == null || n.isBlank() || n.equals("Student") ? "" : n.trim().split("\\s+")[0];
    }

    // ---------------- the confirmation, as plain text and as HTML (same content) ----------------

    static String plainText(PassPdf.Trip t, String first) {
        String rule = "------------------------------------------------------------\n";
        return "CAMPUS BUS - BOOKING CONFIRMED\n" + rule + "\n"
            + "Hi " + (first.isEmpty() ? "there" : first) + ",\n\n"
            + "You're all set. Your seat is reserved, and your Campus Bus travel pass is attached.\n\n"
            + "Booking reference: " + t.code() + "\n\n"
            + "YOUR TRIP (" + t.direction() + ", " + t.tripName() + ")\n"
            + "  " + t.origin() + "  ->  " + t.destination() + "\n\n"
            + row("Direction", t.direction()) + row("From", t.origin()) + row("To", t.destination()) + row("Boarding point", t.boarding())
            + row("Bus", t.busNo()) + row("Bus route", t.route()) + row("Travel date", t.date()) + row("Departure", t.departsText())
            + row("Seat", t.seatText()) + row("Passenger", t.student() + (t.regNo().isEmpty() ? "" : " (" + t.regNo() + ")"))
            + row("Booking reference", t.code()) + "\n"
            + "Your official travel pass is attached as a PDF (campus-bus-pass-" + t.code() + ".pdf).\n"
            + "Keep it accessible and show it when boarding.\n\n"
            + "Need to change plans? Open CAMPUS BUS and go to My reservations: you can view or save\n"
            + "this pass there, and cancel the seat until the bus leaves. For anything else, contact\n"
            + "your institution's transport office.\n\n" + rule
            + "This is an automated message from CAMPUS BUS" + (t.institution().isEmpty() ? "" : " (" + t.institution() + ")") + ",\n"
            + "sent because this seat was booked with this email address.\n";
    }

    private static String row(String label, String value) { return String.format("  %-18s %s%n", label + ":", value); }

    static String html(PassPdf.Trip t, String first) {
        String font = "font-family:'Segoe UI',Roboto,'Helvetica Neue',Helvetica,Arial,sans-serif;";
        String mono = "font-family:Consolas,'SFMono-Regular',Menlo,'Courier New',monospace;";
        StringBuilder rows = new StringBuilder();
        String[][] details = {{"Direction", t.direction()}, {"Boarding point", t.boarding()}, {"Bus", t.busNo()}, {"Travel date", t.date()},
                {"Departure", t.departsText()}, {"Seat", t.seatText()}, {"Passenger", t.student() + (t.regNo().isEmpty() ? "" : " · " + t.regNo())},
                {"Booking reference", t.code()}};
        for (String[] d : details)
            rows.append("<tr><td style=\"padding:11px 0;border-top:1px solid #EDF0F5;font-size:13px;color:#5B6475;\">").append(esc(d[0]))
                .append("</td><td align=\"right\" style=\"padding:11px 0;border-top:1px solid #EDF0F5;font-size:14px;font-weight:600;color:#111827;\">")
                .append(esc(d[1])).append("</td></tr>");
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<meta name=\"color-scheme\" content=\"light\"><title>Booking confirmed " + esc(t.code()) + "</title></head>"
            + "<body style=\"margin:0;padding:0;background:#EEF1F6;\">"
            // preview text in the inbox list
            + "<div style=\"display:none;max-height:0;overflow:hidden;\">Seat " + esc(t.seatText()) + " on " + esc(t.busNo()) + ", " + esc(t.date())
            + ", departs " + esc(t.departsText()) + " from " + esc(t.origin()) + ". Ref " + esc(t.code()) + ".</div>"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#EEF1F6;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;background:#FFFFFF;border:1px solid #E3E7EE;"
            + "border-radius:14px;overflow:hidden;" + font + "color:#111827;\">"
            // header
            + "<tr><td style=\"background:#0F1B2D;padding:22px 28px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
            + "<td style=\"" + font + "color:#FFFFFF;font-size:20px;font-weight:800;letter-spacing:1px;\">CAMPUS BUS<span style=\"color:#E8431B;\">&nbsp;&#9679;</span></td>"
            + "<td align=\"right\" style=\"" + font + "color:#9FB0CC;font-size:12px;letter-spacing:1px;\">TRAVEL PASS</td></tr></table>"
            + (t.institution().isEmpty() ? "" : "<div style=\"" + font + "color:#9FB0CC;font-size:12px;margin-top:6px;\">" + esc(t.institution()) + "</div>")
            + "</td></tr>"
            // greeting
            + "<tr><td style=\"padding:28px 28px 4px;\">"
            + "<span style=\"display:inline-block;background:#E7F6EC;color:#15803D;font-size:12px;font-weight:700;letter-spacing:1.2px;padding:7px 14px;border-radius:999px;\">"
            + "&#10003;&nbsp; BOOKING CONFIRMED</span>"
            + "<h1 style=\"margin:20px 0 8px;font-size:22px;line-height:1.3;color:#0F1B2D;\">Hi " + (first.isEmpty() ? "there" : esc(first)) + ",</h1>"
            + "<p style=\"margin:0;font-size:15px;line-height:1.6;color:#374151;\">You&#39;re all set. Your seat is reserved, and your Campus Bus travel pass is attached.</p>"
            + "</td></tr>"
            // booking reference
            + "<tr><td style=\"padding:20px 28px 0;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F4F7FD;border-left:4px solid #2563EB;border-radius:8px;\">"
            + "<tr><td style=\"padding:14px 18px;\"><div style=\"font-size:11px;letter-spacing:1.4px;color:#5B6475;font-weight:700;\">BOOKING REFERENCE</div>"
            + "<div style=\"" + mono + "font-size:28px;font-weight:700;color:#0F1B2D;letter-spacing:1px;margin-top:4px;\">" + esc(t.code()) + "</div></td>"
            + "<td align=\"right\" style=\"padding:14px 18px;\"><div style=\"font-size:11px;letter-spacing:1.4px;color:#5B6475;font-weight:700;\">SEAT</div>"
            + "<div style=\"" + mono + "font-size:28px;font-weight:700;color:#2563EB;margin-top:4px;\">" + esc(t.seatText()) + "</div></td></tr></table></td></tr>"
            // trip card
            + "<tr><td style=\"padding:16px 28px 0;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid #E3E7EE;border-radius:12px;\">"
            + "<tr><td style=\"padding:16px 18px 4px;font-size:11px;letter-spacing:1.4px;color:#2563EB;font-weight:700;\">"
            + esc(t.direction().toUpperCase(java.util.Locale.ENGLISH)) + " &middot; " + esc(t.tripName().toUpperCase(java.util.Locale.ENGLISH)) + "</td></tr>"
            + "<tr><td style=\"padding:6px 18px 14px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
            + "<td width=\"44%\" valign=\"top\"><div style=\"font-size:11px;color:#5B6475;\">From</div>"
            + "<div style=\"font-size:18px;font-weight:700;color:#111827;line-height:1.3;\">" + esc(t.origin()) + "</div>"
            + "<div style=\"font-size:13px;color:#5B6475;margin-top:2px;\">Departs " + esc(t.departsText()) + "</div></td>"
            + "<td width=\"12%\" align=\"center\" style=\"font-size:20px;color:#2563EB;\">&#8594;</td>"
            + "<td width=\"44%\" valign=\"top\" align=\"right\"><div style=\"font-size:11px;color:#5B6475;\">To</div>"
            + "<div style=\"font-size:18px;font-weight:700;color:#111827;line-height:1.3;\">" + esc(t.destination()) + "</div>"
            + "<div style=\"font-size:13px;color:#5B6475;margin-top:2px;\">" + esc(t.date()) + "</div></td>"
            + "</tr></table></td></tr>"
            + "<tr><td style=\"padding:0 18px 6px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + rows + "</table></td></tr>"
            + "</table></td></tr>"
            // attachment
            + "<tr><td style=\"padding:16px 28px 0;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F8FAFC;border:1px dashed #C9D3E3;border-radius:10px;\">"
            + "<tr><td style=\"padding:14px 16px;font-size:14px;line-height:1.55;color:#374151;\"><strong style=\"color:#0F1B2D;\">Your official travel pass is attached as a PDF.</strong> "
            + "Keep it accessible and show it when boarding.<div style=\"font-size:12px;color:#5B6475;margin-top:4px;\">campus-bus-pass-" + esc(t.code()) + ".pdf</div></td></tr></table></td></tr>"
            // help
            + "<tr><td style=\"padding:18px 28px 26px;font-size:13px;line-height:1.6;color:#5B6475;\">Need to change plans? Open CAMPUS BUS and go to "
            + "<strong style=\"color:#374151;\">My reservations</strong>: you can view or save this pass there, and cancel the seat until the bus leaves. "
            + "For anything else, contact your institution&#39;s transport office.</td></tr>"
            // footer
            + "<tr><td style=\"background:#F4F6FA;border-top:1px solid #E3E7EE;padding:16px 28px;font-size:12px;line-height:1.5;color:#7A8394;\">"
            + "This is an automated message from CAMPUS BUS" + (t.institution().isEmpty() ? "" : " for " + esc(t.institution()))
            + ", sent because this seat was booked with this email address. Your booking&#39;s current status is always shown in the app.</td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    /** HTML-escapes everything that came from a person (names, institution, stops): no markup can get into the email. */
    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}

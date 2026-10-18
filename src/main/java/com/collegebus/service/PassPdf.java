package com.collegebus.service;

import com.collegebus.dao.AdminDAO;
import com.collegebus.model.Reservation;
import com.collegebus.model.Timetable;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * The travel pass as a PDF: the one implementation behind both the in-app "Save pass (PDF)" and the confirmation-email
 * attachment. Its picture is the in-app pass itself (PassImage), drawn from the reservation as saved in the database;
 * a line of selectable text under it repeats the key details for search, copy and screen readers.
 */
public final class PassPdf {
    private PassPdf() { }

    /** The pass details, formatted once from the saved reservation (also used by the confirmation email). */
    public record Trip(String code, String student, String regNo, String institution, String busNo, boolean toCampus,
                       String origin, String destination, String boarding, String route, String date, String departs,
                       boolean departsEstimated, int seat, String status) {

        public static Trip of(Reservation r, String institution) {
            var bus = r.getBus();
            String boarding = r.boardingPoint();
            int at = bus.getRoute().indexOf(boarding);                 // same stop matching as the app (case, dots, spaces)
            String departs;
            boolean estimated;
            if (at < 0) {                                              // the route was edited after booking
                departs = "time not available (stop no longer on the route)";
                estimated = false;
            } else {
                departs = bus.timeAt(boarding).toString();
                // the trip back leaves the campus at its set time; every other unpublished time is an estimate
                estimated = !(bus.isReturnTrip() && at == 0) && !"published".equals(bus.stopTimes().get(at).get("kind"));
            }
            return new Trip(r.getCode(), blank(r.getStudentName(), "Student"), blank(r.getStudentRegNo(), ""), blank(institution, ""),
                    bus.getBusNo(), !bus.isReturnTrip(), boarding, r.destination(), boarding,
                    bus.getRoute().getSource() + " to " + bus.getRoute().getDestination(),
                    r.getTravelDate().format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)),
                    departs, estimated, r.getSeatNo(), blank(r.getStatus(), "BOOKED"));
        }

        public String direction() { return toCampus ? "To campus" : "From campus"; }
        public String tripName() { return toCampus ? "morning trip" : "evening trip"; }
        public String seatText() { return String.format("%02d", seat); }
        public String departsText() { return (departsEstimated ? "about " : "") + departs; }

        private static String blank(String s, String instead) { return s == null || s.isBlank() ? instead : s.trim(); }
    }

    private static final float W = PDRectangle.A5.getHeight(), H = PDRectangle.A5.getWidth();   // A5 landscape, 595 x 420 pt
    private static final float MARGIN = 24;

    /** The pass for a saved reservation. Draws on the JavaFX thread, so call it from a background thread. */
    public static byte[] create(Reservation r, String institution) throws IOException {
        Timetable t;
        try {
            t = new AdminDAO().timetable();
        } catch (SQLException e) {
            throw new IOException("Could not read the timetable for the pass.", e);
        }
        BufferedImage picture = PassImage.render(PassImage.data(r, institution, t.getArriveFrom() + "–" + t.getArriveBy()));
        return create(r, institution, picture);
    }

    /** The PDF page around an already drawn pass picture. */
    static byte[] create(Reservation r, String institution, BufferedImage picture) throws IOException {
        Trip trip = Trip.of(r, institution);
        try (PDDocument doc = new PDDocument(); InputStream fontFile = PassPdf.class.getResourceAsStream("/web/assets/fonts/Overpass-Regular.ttf")) {
            if (fontFile == null) throw new IOException("The pass font is missing from the application.");
            PDType0Font font = PDType0Font.load(doc, fontFile);
            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("CAMPUS BUS travel pass " + trip.code());
            info.setCreator("CAMPUS BUS");
            PDImageXObject image = LosslessFactory.createFromImage(doc, picture);

            float maxW = W - 2 * MARGIN, maxH = H - 2 * MARGIN - 46;            // room for three caption lines
            float scale = Math.min(maxW / picture.getWidth(), maxH / picture.getHeight());
            float iw = picture.getWidth() * scale, ih = picture.getHeight() * scale;
            float ix = (W - iw) / 2, iy = H - MARGIN - ih;
            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                c.drawImage(image, ix, iy, iw, ih);
                Color ink = new Color(0x3A3A36);
                String who = trip.code() + "  ·  " + trip.student() + (trip.regNo().isEmpty() ? "" : " (" + trip.regNo() + ")") + "  ·  Bus " + trip.busNo();
                String when = trip.direction() + ", " + trip.origin() + " to " + trip.destination() + "  ·  " + trip.date()
                        + "  ·  departs " + trip.departsText() + "  ·  seat " + trip.seatText() + "  ·  " + trip.status();
                text(c, font, 8, ink, ix, iy - 15, fitText(font, 8, who, iw));
                text(c, font, 8, ink, ix, iy - 26, fitText(font, 8, when, iw));
                text(c, font, 7.5f, new Color(0x6E6A63), ix, iy - 38,
                        fitText(font, 7.5f, "Show this pass when boarding. Valid only for the trip, date and seat shown, for the named passenger.", iw));
            }
            // a fixed document ID from the booking, so the same reservation always gives the same file
            byte[] id = ("CAMPUS-BUS-PASS-" + trip.code()).getBytes(StandardCharsets.US_ASCII);
            COSArray ids = new COSArray();
            ids.add(new COSString(id));
            ids.add(new COSString(id));
            doc.getDocument().getTrailer().setItem(COSName.ID, ids);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void text(PDPageContentStream c, PDType0Font font, float size, Color col, float x, float y, String s) throws IOException {
        c.beginText();
        c.setNonStrokingColor(col);
        c.setFont(font, size);
        c.newLineAtOffset(x, y);
        c.showText(s);
        c.endText();
    }

    static float width(PDType0Font font, float size, String s) throws IOException {
        return font.getStringWidth(printable(font, s)) / 1000 * size;
    }

    /** The text in characters the font has, shortened with "..." if it does not fit {@code max} points. */
    static String fitText(PDType0Font font, float size, String s, float max) throws IOException {
        String p = printable(font, s);
        if (width(font, size, p) <= max) return p;
        while (p.length() > 1 && width(font, size, p + "...") > max) p = p.substring(0, p.length() - 1);
        return p.stripTrailing() + "...";
    }

    /** ponytail: the caption font is Latin-only; other scripts print as '?' there (the pass picture itself shows them as the app does). */
    static String printable(PDType0Font font, String s) {
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            try {
                font.encode(c);                                        // asks the font itself whether it can show this character
                b.append(c);
            } catch (IOException | IllegalArgumentException missing) {
                b.append('?');
            }
        });
        return b.toString();
    }
}

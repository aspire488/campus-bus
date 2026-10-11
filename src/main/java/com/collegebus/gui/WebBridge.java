package com.collegebus.gui;

import com.collegebus.model.*;
import com.collegebus.service.CollegeBusSystem;
import com.collegebus.service.ReservationService;
import com.collegebus.util.Json;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * JavaScript -> Java bridge. JS calls e.g. app.book("3", "2026-10-09", "12");
 * every rule runs here in Java, and the answer goes back as JSON text: {"ok":true,"data":...} or {"ok":false,"error":"..."}.
 */
public class WebBridge {
    private final Dashboard dashboard;
    private final CollegeBusSystem system;
    private final User user;
    private final ReservationService reservations;
    private final String institution;
    private WebPanel web;

    public WebBridge(Dashboard dashboard, CollegeBusSystem system, User user) {
        this.dashboard = dashboard;
        this.system = system;
        this.user = user;
        this.reservations = system.manageReservations();
        this.institution = system.institutionName();
    }

    void setWebPanel(WebPanel web) { this.web = web; }

    /** Student-only bridge calls refuse to run in an admin window (and vice versa below). */
    private Student student() {
        if (!(user instanceof Student s)) throw new IllegalStateException("Only students can do this.");
        return s;
    }

    private String reply(Callable<Object> work) {
        try {
            return "{\"ok\":true,\"data\":" + Json.of(work.call()) + "}";       // fixed order: "ok" always first
        } catch (SQLException e) {
            return "{\"ok\":false,\"db\":true,\"error\":" + Json.of("Can't reach the database. Check that MySQL is running.") + "}";
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":" + Json.of(String.valueOf(e.getMessage())) + "}";
        }
    }

    public String stops() { return reply(system::allStops); }

    /** Student home: next reservation, today's numbers and recent activity. */
    public String home() {
        return reply(() -> {
            List<Reservation> mine = reservations.reservationsOf(student());
            Reservation next = mine.stream().filter(r -> !r.getPhase().equals("CANCELLED") && !r.getPhase().equals("COMPLETED"))
                    .min(Comparator.comparing(r -> r.getTravelDate().atTime(r.getBus().getDeparture()))).orElse(null);
            List<Bus> today = system.searchBus("", "", LocalDate.now(), LocalTime.MIN, "");
            Set<Integer> running = new HashSet<>();
            int free = 0;
            for (Bus b : today) if (b.isActive()) { running.add(b.getBusId()); free += b.checkAvailability(); }
            List<Reservation> recent = new ArrayList<>(mine);
            recent.sort(Comparator.comparing(Reservation::getBookedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("me", user); m.put("next", next == null ? null : withStops(next));
            m.put("running", running.size()); m.put("free", free);
            m.put("upcoming", mine.stream().filter(r -> r.getPhase().equals("UPCOMING")).count());
            m.put("recent", recent.subList(0, Math.min(4, recent.size())));
            return m;
        });
    }

    private Map<String, Object> withStops(Reservation r) throws SQLException {
        Map<String, Object> m = r.toMap();
        m.put("stops", r.getBus().stopTimes());
        m.put("live", r.getBus().estimatePosition(r.getTravelDate(), java.time.LocalDateTime.now()));
        m.put("institution", institution);
        m.put("window", window());
        return m;
    }

    /** "08:20–08:40": the campus arrival window, shown next to targets so nobody reads them as printed times. */
    private String window() throws SQLException {
        Timetable t = system.timetable();
        return t.getArriveFrom() + "–" + t.getArriveBy();
    }

    public String search(String from, String to, String date, String after, String busNo) {
        return reply(() -> system.searchBus(from, to, LocalDate.parse(date), after.isBlank() ? LocalTime.MIN : LocalTime.parse(after), busNo));
    }

    /** Seat map data for one trip ("OUT" or "RETURN"): the bus, its route with stop times, taken seats and this student's seat. */
    public String bus(String busId, String date, String direction) {
        return reply(() -> {
            int id = Integer.parseInt(busId);
            LocalDate d = LocalDate.parse(date);
            Bus bus = reservations.getTrip(id, d, direction);
            Map<String, Object> m = new LinkedHashMap<>(bus.toMap());
            m.put("date", date);
            m.put("stops", bus.stopTimes());
            m.put("window", window());
            m.put("taken", reservations.bookedSeats(id, d, bus.getDirection()));
            Object[] held = user instanceof Student s ? reservations.heldOn(s, d, bus.getDirection()) : null;
            boolean here = held != null && ((Number) held[1]).intValue() == id;
            m.put("mine", here ? held[0] : 0);
            m.put("elsewhere", held != null && !here ? held[2] + " (seat " + held[0] + ")" : "");     // already on another bus at this time
            if (user instanceof Admin a && a.isStillAdmin()) {   // only a current transport-office account sees who sits where
                List<Map<String, Object>> list = new ArrayList<>();
                for (Object[] p : reservations.passengers(id, d, bus.getDirection()))
                    list.add(Map.of("seat", p[0], "name", p[1], "regNo", p[2], "stop", p[3] == null ? "" : p[3]));
                m.put("passengers", list);
            }
            m.put("departed", d.atTime(bus.getDeparture()).isBefore(java.time.LocalDateTime.now()));
            return m;
        });
    }

    public String book(String busId, String date, String direction, String seat, String stop) {
        String json = reply(() -> withStops(reservations.bookSeat(student(), Integer.parseInt(busId), LocalDate.parse(date), direction,
                Integer.parseInt(seat), stop.isBlank() ? null : stop)));
        boolean ok = json.startsWith("{\"ok\":true");
        dashboard.toast(ok ? "Reservation confirmed" : "Seat not booked", ok ? "Seat " + seat + " is yours." : "See the message on the seat map.", ok);
        return json;
    }

    public String cancel(String resId) {
        String json = reply(() -> reservations.cancelBooking(student(), Integer.parseInt(resId)));
        boolean ok = json.startsWith("{\"ok\":true");
        dashboard.toast(ok ? "Reservation cancelled" : "Not cancelled", ok ? "Your seat is available to others again." : "See the message on screen.", ok);
        return json;
    }

    public String reservations() { return reply(() -> reservations.reservationsOf(student())); }

    public String reservation(String resId) { return reply(() -> withStops(reservations.find(student(), Integer.parseInt(resId)))); }

    /** Admin overview analytics - all numbers come from MySQL through CollegeBusSystem. */
    public String overview() {
        return reply(() -> {
            if (!(user instanceof Admin a) || !a.isStillAdmin()) throw new IllegalStateException("Only the transport office can see this.");
            return system.displayDetails();
        });
    }

    public String print() { return web.print(); }

    /** Admin seat map "back" link: returns to a Swing page of the console. */
    public void openMenu(String menuItem) {
        if (user instanceof Admin) javax.swing.SwingUtilities.invokeLater(() -> dashboard.select(menuItem));
    }

    /** JS tells Swing which page is showing so the route-line menu stays in sync. */
    public void navigated(String menuItem) { javax.swing.SwingUtilities.invokeLater(() -> dashboard.highlight(menuItem)); }

    void notice(String message) { dashboard.toast("Notice", message, false); }

    public void log(String message) { System.out.println("[web] " + message); }
}

package com.collegebus.service;

import com.collegebus.model.Admin;
import com.collegebus.model.Bus;
import com.collegebus.model.Route;
import com.collegebus.model.Timetable;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a printed bus schedule (format: database/scms-schedule-2025-08-18.txt) into MySQL.
 * Safe to run again: buses are matched by number and routes by their stops, so nothing is duplicated.
 * Every change goes through the signed-in Admin, and the result is checked by reading the database back.
 */
public class ScheduleImport {
    private static final Pattern TIMED = Pattern.compile("(.*\\S)\\s+(\\d{1,2}):(\\d{2})");

    private final CollegeBusSystem system;
    private final Admin admin;
    public final List<String> report = new ArrayList<>();
    public int inserted, updated, unchanged, failed, verified, stops, untimed;

    public ScheduleImport(CollegeBusSystem system, Admin admin) {
        this.system = system;
        this.admin = admin;
    }

    /** One bus line from the file: number, first pick-up time and the route (campus appended, untimed). */
    record Line(int lineNo, String busNo, LocalTime start, Route route) { }

    public void run(List<String> lines) throws SQLException {
        String campus = null, institution = null;
        int capacity = 49;
        List<Line> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            try {
                if (l.startsWith("campus:")) campus = l.substring(7).strip();
                else if (l.startsWith("institution:")) institution = l.substring(12).strip();
                else if (l.startsWith("capacity:")) capacity = Integer.parseInt(l.substring(9).strip());
                else {
                    if (campus == null) throw new IllegalArgumentException("the file needs a \"campus:\" line before the buses");
                    Line row = parse(i + 1, l, campus);
                    if (!seen.add(row.busNo())) throw new IllegalArgumentException("bus " + row.busNo() + " is listed twice");
                    rows.add(row);
                }
            } catch (RuntimeException e) {
                failed++;
                report.add("FAILED line " + (i + 1) + ": " + e.getMessage());
            }
        }
        if (institution != null && !institution.equals(system.institutionName()))
            report.add("NOTE the schedule is for \"" + institution + "\"; this installation is named \"" + system.institutionName() + "\".");

        Timetable t = system.timetable();
        for (Line row : rows) {
            try {
                save(row, capacity, t);
            } catch (RuntimeException e) {
                failed++;
                report.add("FAILED bus " + row.busNo() + " (line " + row.lineNo() + "): " + e.getMessage());
            }
        }
        // read everything back from MySQL: a bus counts as imported only if what is stored matches the file
        Map<String, Bus> stored = new HashMap<>();
        for (Bus b : system.buses().findAll(LocalDate.now())) stored.put(b.getBusNo(), b);
        for (Line row : rows) {
            Bus b = stored.get(row.busNo());
            if (b == null) continue;
            if (b.getRoute().getStops().equals(row.route().getStops()) && b.getDeparture().equals(row.start())) verified++;
            else report.add("MISMATCH bus " + row.busNo() + ": the database does not hold what the file says.");
            if (t.needsCheck(b)) report.add("CHECK bus " + row.busNo() + ": last printed pick-up " + b.lastPickUp() + " is less than "
                    + Timetable.VERIFY_GAP_MINUTES + " min before the arrival window opens (" + t.getArriveFrom() + "). Confirm with the transport office.");
        }
    }

    /** "05 | Poothotta 06:50; Chakkulam 06:50; Eachamukku; ..." */
    static Line parse(int lineNo, String line, String campus) {
        String[] parts = line.split("\\|");
        if (parts.length != 2) throw new IllegalArgumentException("write it as: bus number | stops separated by ;");
        String busNo = parts[0].strip().toUpperCase();                         // stored upper-case, so compare it that way
        LocalTime start = null;
        List<String> stops = new ArrayList<>();
        for (String token : parts[1].split(";")) {
            String s = token.strip();
            if (s.isEmpty()) continue;
            Matcher m = TIMED.matcher(s);
            if (m.matches()) {
                LocalTime time = LocalTime.of(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                if (start == null) start = time;
                long minutes = Duration.between(start, time).toMinutes();
                if (minutes < 0) throw new IllegalArgumentException(m.group(1) + " at " + time + " is earlier than the first stop (" + start + ")");
                stops.add(m.group(1) + ":" + minutes);
            } else {
                if (start == null) throw new IllegalArgumentException("the first stop needs a time");
                stops.add(s);
            }
        }
        stops.add(campus);
        return new Line(lineNo, busNo, start, Route.create(0, String.join(", ", stops), 0));
    }

    /** Reuses a route with exactly these stops, or saves it first; then adds or updates the bus. */
    private void save(Line row, int capacity, Timetable t) {
        try {
            Route route = findRoute(row.route().getStops());
            if (route == null) {
                admin.saveRoute(row.route());
                route = findRoute(row.route().getStops());
            }
            if (route == null) throw new IllegalStateException("the route was not saved");
            saveBus(row, route, capacity, t);
        } catch (SQLException e) {
            throw new IllegalStateException("database error: " + e.getMessage());
        }
    }

    private Route findRoute(String stopList) throws SQLException {
        for (Route r : system.buses().findRoutes()) if (r.getStops().equals(stopList)) return r;
        return null;
    }

    private void saveBus(Line row, Route route, int capacity, Timetable t) throws SQLException {
        stops += route.stopNames().size() - 1;                                   // the campus is not a boarding point
        untimed += (int) route.stopMinutes().stream().filter(Objects::isNull).count() - 1;
        Bus existing = system.buses().findAll(LocalDate.now()).stream().filter(b -> b.getBusNo().equals(row.busNo())).findFirst().orElse(null);
        if (existing == null) {
            admin.addBus(t.newBus(row.busNo(), route, row.start(), true, capacity, "ACTIVE"));
            inserted++;
        } else if (existing.getRoute().getRouteId() == route.getRouteId() && existing.getDeparture().equals(row.start())) {
            unchanged++;
        } else {                                                                  // keep the office's capacity, status and evening choice
            Route old = existing.getRoute();
            admin.updateBus(existing, route, row.start(), existing.getReturnDeparture() != null, existing.getCapacity(), existing.getStatus());
            updated++;
            report.add("UPDATED bus " + row.busNo() + " to the schedule's route and start time.");
            if (old.getRouteId() != route.getRouteId() && system.buses().findAll(LocalDate.now()).stream().noneMatch(b -> b.getRoute().getRouteId() == old.getRouteId())) {
                system.buses().deleteRoute(old.getRouteId());                   // the replaced route has no bus left
                report.add("REMOVED the old route " + old.getName() + " (no bus uses it any more).");
            }
        }
    }

    public String summary() {
        return inserted + " added, " + updated + " updated, " + unchanged + " unchanged, " + failed + " failed; "
                + verified + " verified in the database; " + stops + " boarding points (" + untimed + " without a printed time).";
    }
}

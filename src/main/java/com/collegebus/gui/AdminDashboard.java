package com.collegebus.gui;

import com.collegebus.model.*;
import com.collegebus.service.CollegeBusSystem;
import java.time.LocalDate;
import java.time.LocalTime;
import javax.swing.*;

/** Operations console: Overview is an HTML analytics page; the other pages are Swing tables with forms. */
public class AdminDashboard extends Dashboard {
    private final Admin admin;
    private final WebPanel web;
    private final TablePanel<Bus> buses = new TablePanel<Bus>("Buses",
            new String[]{"Bus", "From", "Departure", "Campus arrival", "Return trip", "Booked today", "Status"},
            b -> new Object[]{b.getBusNo(), b.getRoute().getSource() + " " + via(b.getRoute(), 1), b.getDeparture().toString(), arrival(b),
                b.returnTrip() == null ? "One way" : b.returnTrip().getDeparture() + " → ~" + b.returnTrip().getArrival(),
                b.getBookedBothWays() + " / " + b.getCapacity() * (b.returnTrip() == null ? 1 : 2), b.getStatus()})
            .withFilter(6, "All statuses", "ACTIVE", "MAINTENANCE");
    private final TablePanel<Bus> schedule = new TablePanel<Bus>("Schedules",
            new String[]{"Departure", "Bus", "Trip", "From", "To", "Arrival", "Minutes"},
            b -> new Object[]{b.getDeparture().toString(), b.getBusNo(), b.isReturnTrip() ? "Return" : "Outbound", b.getRoute().getSource(),
                b.getRoute().getDestination(), b.isReturnTrip() ? "~" + b.getArrival() : arrival(b), (int) b.getDurationMinutes()})
            .withFilter(2, "Both directions", "Outbound", "Return");
    private final TablePanel<Route> routes = new TablePanel<Route>("Routes",
            new String[]{"Route", "Via", "Buses", "Stops", "With a printed time", "Distance (km)"},       // full list: Edit, or Schedules > Stop times
            r -> new Object[]{r.getName(), via(r, 3), this.busesOn.getOrDefault(r.getRouteId(), "none"), r.stopNames().size(), r.stopMinutes().stream().filter(java.util.Objects::nonNull).count() + " of " + r.stopNames().size(),
                r.getDistanceKm() > 0 ? r.getDistanceKm() : "—"});
    private final TablePanel<Student> students = new TablePanel<Student>("Students",
            new String[]{"Admission no.", "Name", "Class", "Department", "Email", "Phone", "Bookings"},
            s -> new Object[]{s.getRegNo(), s.getName(), s.getDepartment(), s.getDepartmentCode(), s.getEmail(), s.getPhone(), s.getReservationCount()})
            .withFilter(3, "All departments");
    private final TablePanel<Reservation> reservations = new TablePanel<Reservation>("Reservations",
            new String[]{"Code", "Travel date", "Bus", "Departure", "Seat", "Gets on / off at", "Student", "Dept", "Status"},
            r -> new Object[]{r.getCode(), r.getTravelDate().format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")), r.getBus().getBusNo() + (r.getBus().isReturnTrip() ? " back" : ""),
                r.getBus().getDeparture().toString(), r.getSeatNo(), r.getStop() == null ? "—" : r.getStop(), r.getStudentName(), Student.departmentOf(r.getStudentClass()), r.getPhase()})
            .withFilter(7, "All departments").withFilter(8, "All statuses", "UPCOMING", "CURRENT", "COMPLETED", "CANCELLED");
    private final TablePanel<Admin> admins = new TablePanel<Admin>("Administrators",
            new String[]{"Username", "Name", "Account"},
            a -> new Object[]{a.getUsername(), a.getName(), a.getId() == user.getId() ? "You" : ""});

    /** Reservations are loaded one period at a time: hundreds of students book every day. */
    private static final String[] PERIODS = {"Today and next 7 days", "Today", "Tomorrow", "Last 7 days", "Last 30 days"};

    private Timetable timetable = Timetable.DEFAULT;            // refreshed whenever a page loads
    private final java.util.Map<Integer, String> busesOn = new java.util.HashMap<>();   // route id -> "15, 16": routes can share a name

    /** "08:45" when printed; "by 08:40 · target" when it is the office's target, "· check" if the route should be confirmed. */
    private String arrival(Bus b) {
        if (!b.isArrivalTarget()) return b.getArrival().toString();
        return "by " + b.getArrival() + (timetable.needsCheck(b) ? " · check" : " · target");
    }

    /** "via Amballoor, Chalakkudy (under passage) +3": tells apart routes that share a name, e.g. three from Thrissur. */
    private static String via(Route r, int shown) {
        java.util.List<String> mid = r.stopNames().subList(1, r.stopNames().size() - 1);
        if (mid.isEmpty()) return "direct";
        String list = String.join(", ", mid.subList(0, Math.min(shown, mid.size())));
        return "via " + list + (mid.size() > shown ? " +" + (mid.size() - shown) : "");
    }

    public AdminDashboard(Admin admin, CollegeBusSystem system) {
        super(admin, system, new String[]{"Overview", "Buses", "Routes", "Schedules", "Students", "Reservations", "Admins"});
        this.admin = admin;
        this.web = new WebPanel("admin", new WebBridge(this, system, admin), "overview");
        buses.withButton("Edit", false, guard(() -> { if (pick(buses.selected())) editBus(buses.selected()); })).withButton("Remove", false, guard(() -> removeBus(buses.selected(), "Buses")))
             .withButton("Add bus", true, guard(() -> editBus(null)));
        schedule.withButton("Remove", false, guard(() -> removeBus(schedule.selected(), "Schedules")))
                .withButton("Seat map", false, guard(this::seatMap)).withButton("Stop times", false, guard(this::stopTimes)).withButton("Timetable", true, guard(this::editTimetable));
        routes.withButton("Edit", false, guard(() -> { if (pick(routes.selected())) editRoute(routes.selected()); })).withButton("Remove", false, guard(this::removeRoute))
              .withButton("Import schedule…", false, guard(this::importSchedule)).withButton("Add route", true, guard(() -> editRoute(null)));
        students.withButton("Edit", false, guard(this::editStudent)).withButton("Remove", false, guard(this::removeStudent));
        reservations.withChooser(PERIODS, () -> open("Reservations")).withButton("Cancel reservation", true, guard(this::cancelReservation));
        admins.withButton("Institution name", false, guard(this::renameInstitution)).withButton("Remove", false, guard(this::removeAdmin))
              .withButton("Add admin", true, guard(this::addAdmin));
    }

    /**
     * Every button runs through here: if another admin removed this account meanwhile, the console closes instead of acting.
     * (A database error is left to the action itself, which reports it.)
     */
    private Runnable guard(Runnable action) {
        return () -> {
            try {
                if (!admin.isStillAdmin()) { signedOut(); return; }
            } catch (java.sql.SQLException ignored) { }
            action.run();
        };
    }

    private void signedOut() {
        JOptionPane.showMessageDialog(this, "Your administrator account was removed by another admin, so you have been signed out.",
                "Signed out", JOptionPane.PLAIN_MESSAGE);
        dispose();
        new LoginFrame(system).setVisible(true);
    }

    @Override
    protected void open(String page) {
        try {
            if (!admin.isStillAdmin()) { signedOut(); return; }
            timetable = system.timetable();
            switch (page) {
                case "Buses" -> { buses.setItems(system.buses().findAll(LocalDate.now())); showPage(page, buses); }
                case "Schedules" -> {
                    schedule.setItems(system.buses().findTrips(LocalDate.now()));
                    schedule.setCaption(timetable.displayDetails());
                    showPage(page, schedule);
                }
                case "Routes" -> {
                    busesOn.clear();
                    for (Bus b : system.buses().findAll(LocalDate.now())) busesOn.merge(b.getRoute().getRouteId(), b.getBusNo(), (x, y) -> x + ", " + y);
                    routes.setItems(system.buses().findRoutes());
                    showPage(page, routes);
                }
                case "Students" -> { students.setItems(system.students().findAll()); showPage(page, students); }
                case "Reservations" -> {
                    LocalDate today = LocalDate.now();
                    LocalDate[] range = switch (reservations.chosen()) {
                        case "Today" -> new LocalDate[]{today, today};
                        case "Tomorrow" -> new LocalDate[]{today.plusDays(1), today.plusDays(1)};
                        case "Last 7 days" -> new LocalDate[]{today.minusDays(7), today.minusDays(1)};
                        case "Last 30 days" -> new LocalDate[]{today.minusDays(30), today.minusDays(1)};
                        default -> new LocalDate[]{today, today.plusDays(7)};          // Today and the next 7 days
                    };
                    reservations.setItems(admin.viewReservations(range[0], range[1]));
                    showPage(page, reservations);
                }
                case "Admins" -> { admins.setItems(admin.viewAdmins()); showPage(page, admins); }
                default -> { showPage("web", web); web.show("overview"); }
            }
        } catch (Exception e) {
            toast("Database unavailable", "Check that MySQL is running, then try again.", false);
        }
    }

    /** Runs an admin action; shows its rule message (e.g. "has 3 upcoming reservations") if it is refused. */
    private void run(String done, String page, FormDialog.SaveAction action) {
        try {
            action.save();
            toast(done, "Saved to the database.", true);
        } catch (java.sql.SQLException e) {
            toast("Not changed", "The database is unavailable. Check that MySQL is running.", false);
        } catch (Exception e) {
            toast("Not changed", e.getMessage(), false);
        }
        open(page);                                           // always show what MySQL now holds
    }

    private boolean pick(Object o) {
        if (o == null) toast("Select a row first", "Click a row in the table, then try again.", false);
        return o != null;
    }

    private void editBus(Bus bus) {
        try {
            FormDialog f = new FormDialog(this, bus == null ? "Add a bus" : "Edit " + bus.getBusNo());
            JTextField no = f.add("Bus number", new JTextField(bus == null ? "" : bus.getBusNo()));
            no.setEditable(bus == null);
            JComboBox<Route> route = f.add("Route", new JComboBox<>(system.buses().findRoutes().toArray(new Route[0])));
            if (bus != null) for (int i = 0; i < route.getItemCount(); i++) if (route.getItemAt(i).getRouteId() == bus.getRoute().getRouteId()) route.setSelectedIndex(i);
            f.note("Enter the first pick-up time as published. Campus arrival and the evening trip follow Schedules > Timetable.");
            JTextField dep = f.add("First pick-up (HH:MM)", new JTextField(bus == null ? "07:30" : bus.getDeparture().toString()));
            JComboBox<String> back = f.add("Evening trip back", new JComboBox<>(new String[]{"Yes, runs back in the evening", "No, mornings only"}));
            if (bus != null && bus.getReturnDeparture() == null) back.setSelectedIndex(1);
            JTextField cap = f.add("Seat capacity (8-60)", new JTextField(String.valueOf(bus == null ? 40 : bus.getCapacity())));
            JComboBox<String> status = f.add("Status", new JComboBox<>(new String[]{"ACTIVE", "MAINTENANCE"}));
            if (bus != null) status.setSelectedItem(bus.getStatus());
            boolean saved = f.open(bus == null ? "Add bus" : "Save changes", () -> {
                Route r = (Route) route.getSelectedItem();
                if (r == null) throw new IllegalArgumentException("Add a route first (Routes > Add route).");
                boolean runsBack = back.getSelectedIndex() == 0;
                int seats = Integer.parseInt(cap.getText().trim());
                if (bus == null) {
                    admin.addBus(system.timetable().newBus(no.getText(), r, LocalTime.parse(dep.getText().trim()), runsBack, seats,
                            (String) status.getSelectedItem()));
                } else {
                    admin.updateBus(bus, r, LocalTime.parse(dep.getText().trim()), runsBack, seats, (String) status.getSelectedItem());
                }
            });
            open("Buses");                                        // reload from MySQL either way - no stale rows
            if (saved) toast("Bus saved", no.getText().trim().toUpperCase() + " is in the timetable.", true);
        } catch (Exception e) {
            toast("Database unavailable", "Check that MySQL is running.", false);
        }
    }

    /** The institution's timetable: class start, how early buses arrive, and the one evening departure for every bus. */
    private void editTimetable() {
        try {
            Timetable now = system.timetable();
            FormDialog f = new FormDialog(this, "Timetable");
            f.note("Published pick-up times never change here. Routes without a printed campus time get the window's end as their target.");
            JTextField start = f.add("Classes start (HH:MM)", new JTextField(now.getClassStart().toString()));
            JTextField evening = f.add("Evening departure from campus (HH:MM)", new JTextField(now.getEvening().toString()));
            JTextField from = f.add("Campus arrival window from (HH:MM)", new JTextField(now.getArriveFrom().toString()));
            JTextField by = f.add("Campus arrival window to (HH:MM)", new JTextField(now.getArriveBy().toString()));
            if (f.open("Save timetable", () -> admin.manageSchedule(new Timetable(LocalTime.parse(start.getText().trim()),
                    LocalTime.parse(from.getText().trim()), LocalTime.parse(by.getText().trim()), LocalTime.parse(evening.getText().trim())))))
                toast("Timetable saved", system.timetable().displayDetails() + ".", true);
        } catch (java.sql.SQLException e) {
            toast("Database unavailable", "Check that MySQL is running, then try again.", false);
        }
        open("Schedules");
    }

    /** The selected trip's seats, drawn like the inside of the bus (the same HTML page students book on, read-only here). */
    private void seatMap() {
        Bus trip = schedule.selected();
        if (!pick(trip)) return;
        showPage("web", web);
        web.show("seats", com.collegebus.util.Json.of(java.util.Map.of("id", trip.getBusId(), "dir", trip.getDirection(), "date", LocalDate.now().toString())));
    }

    /** The expected time at every stop for the selected trip - the same every day. */
    private void stopTimes() {
        Bus trip = schedule.selected();
        if (!pick(trip)) return;
        StringBuilder sb = new StringBuilder("<html><table cellpadding=3>");
        for (var stop : trip.stopTimes()) {
            boolean printed = "published".equals(stop.get("kind"));
            sb.append("<tr><td>").append(printed ? "<b>" + stop.get("time") + "</b>" : ("target".equals(stop.get("kind")) ? "by " : "~") + stop.get("time")).append("</td><td>")
              .append(escape(String.valueOf(stop.get("name")))).append("</td><td><font color=#6E6A63>")
              .append(printed ? "" : "target".equals(stop.get("kind")) ? "campus arrival target" : "estimated").append("</font></td></tr>");
        }
        JOptionPane.showMessageDialog(this, sb.append("</table></html>").toString(),
                trip.getBusNo() + (trip.isReturnTrip() ? " - evening trip" : " - morning trip"), JOptionPane.PLAIN_MESSAGE);
    }

    /** Swing renders <html> labels, so stop names typed by an admin are escaped. */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void removeBus(Bus bus, String page) {
        if (pick(bus) && confirm("Remove " + bus.getBusNo() + "? It is removed in both directions, with its past reservations."))
            run("Bus removed", page, () -> admin.removeBus(bus));
    }

    private void editRoute(Route old) {
        FormDialog f = new FormDialog(this, old == null ? "Add a route" : "Edit " + old.getName());
        JTextField stops = f.add("Stops as Name:minutes (or just Name if no time is published), first stop is 0", new JTextField(old == null ? "Main Gate:0, Market Road:12, Campus:30" : old.getStops().replace(",", ", "), 34));
        JTextField km = f.add("Distance in km (blank if not published)", new JTextField(old == null || old.getDistanceKm() == 0 ? "" : String.valueOf(old.getDistanceKm())));
        if (f.open("Save route", () -> admin.saveRoute(Route.create(old == null ? 0 : old.getRouteId(), stops.getText(), km.getText().isBlank() ? 0 : Double.parseDouble(km.getText().trim())))))
            { toast("Route saved", "Buses on it were re-timed to the timetable.", true); open("Routes"); }
    }

    /** Loads a printed schedule file (see database/scms-schedule-2025-08-18.txt) and shows exactly what happened. */
    private void importSchedule() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(new java.io.File("database"));
        chooser.setDialogTitle("Import a bus schedule");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Bus schedule (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        try {
            com.collegebus.service.ScheduleImport imp = new com.collegebus.service.ScheduleImport(system, admin);
            imp.run(java.nio.file.Files.readAllLines(chooser.getSelectedFile().toPath()));
            JTextArea text = new JTextArea(imp.summary() + "\n\n" + String.join("\n", imp.report), 14, 70);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setFont(Theme.body(13));
            JOptionPane.showMessageDialog(this, new JScrollPane(text), "Schedule import", JOptionPane.PLAIN_MESSAGE);
            toast(imp.failed == 0 ? "Schedule imported" : "Imported with problems", imp.summary(), imp.failed == 0);
        } catch (java.io.IOException e) {
            toast("Not imported", "That file could not be read.", false);
        } catch (java.sql.SQLException e) {
            toast("Not imported", "The database is unavailable. Check that MySQL is running.", false);
        }
        open("Routes");
    }

    private void removeRoute() {
        Route r = routes.selected();
        if (pick(r) && confirm("Remove the route " + r.getName() + "?")) run("Route removed", "Routes", () -> { admin.requireActive(); system.buses().deleteRoute(r.getRouteId()); });
    }

    private void editStudent() {
        Student s = students.selected();
        if (!pick(s)) return;
        FormDialog f = new FormDialog(this, "Edit " + s.getRegNo());
        JTextField name = f.add("Full name", new JTextField(s.getName())), phone = f.add("Phone", new JTextField(s.getPhone()));
        JComboBox<String> dept = f.add("Class, e.g. S3 CS2", LoginFrame.departments());
        dept.setSelectedItem(s.getDepartment());
        if (f.open("Save changes", () -> { admin.requireActive(); s.updateStudent(name.getText(), LoginFrame.typed(dept), phone.getText()); system.students().update(s); }))
            { toast("Student saved", s.displayStudent(), true); open("Students"); }
    }

    /** Admin only: deletes the account; the database cascades its reservations, so upcoming seats become free again. */
    private void removeStudent() {
        Student s = students.selected();
        if (!pick(s)) return;
        int upcoming;
        try {
            upcoming = system.students().countUpcoming(s.getId());
        } catch (java.sql.SQLException e) {
            toast("Database unavailable", "Check that MySQL is running, then try again.", false);
            return;
        }
        String seats = upcoming == 0 ? "They have no upcoming reservations." : upcoming + (upcoming == 1 ? " upcoming seat is" : " upcoming seats are")
                + " released for other students.";
        if (confirm("Remove " + s.getName() + " (" + s.getRegNo() + ")?\n\nThey will no longer be able to sign in. " + seats
                + "\nAll of their reservation history is deleted too. This cannot be undone."))
            run("Student removed", "Students", () -> { admin.requireActive(); system.students().delete(s.getId()); });
    }

    private void renameInstitution() {
        FormDialog f = new FormDialog(this, "Institution name");
        f.note("Shown on the login screen, every dashboard and every bus pass.");
        JTextField name = f.add("Name", new JTextField(system.institutionName(), 34));
        if (f.open("Save name", () -> admin.renameInstitution(name.getText()))) {
            setInstitution(system.institutionName());
            toast("Name saved", system.institutionName(), true);
        }
    }

    private void addAdmin() {
        FormDialog f = new FormDialog(this, "Add an administrator");
        JTextField name = f.add("Full name", new JTextField()), user = f.add("Username", new JTextField());
        JPasswordField pw = f.add("Password (8+: A-Z, a-z, 0-9 and a symbol)", new JPasswordField()), again = f.add("Repeat password", new JPasswordField());
        if (f.open("Add admin", () -> {
            String p = new String(pw.getPassword());
            if (!p.equals(new String(again.getPassword()))) throw new IllegalArgumentException("The two passwords are different.");
            admin.addAdmin(Admin.create(user.getText(), name.getText(), p));
        })) { toast("Administrator added", user.getText().trim().toLowerCase() + " can now sign in as admin.", true); open("Admins"); }
    }

    private void removeAdmin() {
        Admin a = admins.selected();
        if (pick(a) && confirm("Remove the administrator " + a.getUsername() + " (" + a.getName() + ")? They will no longer be able to sign in."))
            run("Administrator removed", "Admins", () -> admin.removeAdmin(a));
    }

    private void cancelReservation() {
        Reservation r = reservations.selected();
        if (pick(r) && confirm("Cancel " + r.getCode() + " (seat " + r.getSeatNo() + " on " + r.getBus().getBusNo() + ")?"))
            run("Reservation cancelled", "Reservations", () -> { admin.requireActive(); system.manageReservations().cancelBooking(null, r.getResId()); });
    }

    private boolean confirm(String question) {
        String[] options = {"Yes, go ahead", "Keep it"};
        return JOptionPane.showOptionDialog(this, question, "Please confirm", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[1]) == 0;
    }
}

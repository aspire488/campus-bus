package com.collegebus.model;

import com.collegebus.dao.AdminDAO;
import com.collegebus.dao.BusDAO;
import com.collegebus.dao.ReservationDAO;
import com.collegebus.gui.AdminDashboard;
import com.collegebus.service.CollegeBusSystem;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;

/** INHERITANCE: an Admin IS-A User who manages buses, schedules and reservations. */
public class Admin extends User {
    private String username;
    private final BusDAO busDAO = new BusDAO();
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final AdminDAO adminDAO = new AdminDAO();

    public Admin(int id, String username, String name, String passwordHash) {
        super(id, name, username, passwordHash);
        this.username = username;
    }

    /** Validates a new administrator account (first-run setup or "Add admin"); the username is stored in lower case. */
    public static Admin create(String username, String name, String password) {
        String u = username.trim().toLowerCase(), n = name.trim();
        if (!u.matches("[a-z0-9._-]{3,40}")) throw new IllegalArgumentException("Username needs 3-40 letters, digits or . _ -");
        if (n.length() < 3 || n.length() > 80 || !n.matches("[\\p{L}\\p{M} .'-]+")) throw new IllegalArgumentException("Name needs 3-80 letters (spaces and . ' - allowed).");
        checkNewPassword(password);
        return new Admin(0, u, n, hash(password));
    }

    /** Another admin can be added only from inside the console, by a signed-in admin. */
    public void addAdmin(Admin other) throws SQLException {
        requireActive();
        adminDAO.insert(other);
    }

    /** An admin cannot remove their own account, so the console is never left without someone signed in who can fix it. */
    public void removeAdmin(Admin other) throws SQLException {
        requireActive();
        if (other.getId() == getId()) throw new IllegalStateException("You can't remove your own account while signed in.");
        adminDAO.deleteIfNotLast(other.getId());
    }

    /** Refuses every change once another admin has removed this account (even from a dialog that was already open). */
    public void requireActive() throws SQLException {
        if (!isStillAdmin()) throw new IllegalStateException("Your administrator account was removed. Sign in again.");
    }

    /** False once another admin has removed this account: its open console must stop working. */
    public boolean isStillAdmin() throws SQLException {
        return adminDAO.exists(getId());
    }

    /** Admins can correct or change the institution's name set at first-run setup. */
    public void renameInstitution(String name) throws SQLException {
        requireActive();
        adminDAO.saveSetting("institution", CollegeBusSystem.checkInstitutionName(name));
    }

    public List<Admin> viewAdmins() throws SQLException {
        return adminDAO.findAll();
    }

    /** PPT method. */
    public void addBus(Bus bus) throws SQLException {
        requireActive();
        busDAO.insert(bus);
    }

    /** Changes route, first pick-up, evening trip, capacity and status; campus arrival and the evening time follow the timetable. */
    public void updateBus(Bus bus, Route route, LocalTime departure, boolean runsBack, int capacity, String status) throws SQLException {
        requireActive();
        Timetable t = adminDAO.timetable();
        com.collegebus.util.DatabaseConnection.transaction(c -> {
            // the route as saved now (another admin may have edited it since this form opened), locked before the bus -
            // the same order saveRoute uses, so two admin edits never deadlock
            Route current = BusDAO.lockRouteRow(c, route.getRouteId());
            BusDAO.lock(c, bus.getBusId());          // bookings wait until this change is saved, so the counts below stay true
            int returns = !runsBack ? reservationDAO.countUpcomingReturns(bus.getBusId()) : 0;
            if (returns > 0) throw new IllegalStateException(bus.getBusNo() + " has " + returns + " upcoming return-trip reservations. Keep the return trip or cancel them first.");
            checkStopsKept(busDAO.findById(bus.getBusId(), java.time.LocalDate.now()), current);
            bus.setBookedSeats(busDAO.highestUpcomingSeat(bus.getBusId()));   // cannot shrink below a booked seat
            t.applyTo(bus, current, departure, runsBack, capacity, status);
            busDAO.update(c, bus);
            return null;
        });
    }

    /** Riders booked on this bus must still find their stop on {@code route} (a route edit or a move to another route). */
    private void checkStopsKept(Bus bus, Route route) throws SQLException {
        for (String stop : reservationDAO.upcomingStops(bus.getBusId()))
            // only a stop the bus serves today and the new route drops: bookings at a stop an older version already removed
            // must not block every later edit (capacity, maintenance, ...) of that bus or route
            if (bus.getRoute().indexOf(stop) >= 0 && route.indexOf(stop) < 0)   // a spelling-only change (case, dots) keeps the stop
                throw new IllegalStateException("Students are booked on " + bus.getBusNo() + " at " + stop + ", which is not on the new route. "
                    + "Keep that stop, or cancel those reservations first.");
    }

    /** PPT method: refuses to delete a bus that students are still booked on. */
    public void removeBus(Bus bus) throws SQLException {
        requireActive();
        int upcoming = reservationDAO.countUpcoming(bus.getBusId());
        if (upcoming > 0) throw new IllegalStateException(bus.getBusNo() + " has " + upcoming + " upcoming reservations. Cancel them or set the bus to maintenance.");
        busDAO.delete(bus.getBusId());
    }

    /** PPT method: sets the institution's timetable and re-times every bus to match it. */
    public void manageSchedule(Timetable t) throws SQLException {
        requireActive();
        busDAO.retime(t);                // checks every route first; all buses change in one transaction
        adminDAO.saveTimetable(t);
    }

    /** Saves a route, then re-times its buses (their departure depends on the route's length). */
    public void saveRoute(Route route) throws SQLException {
        requireActive();
        Timetable t = adminDAO.timetable();
        com.collegebus.util.DatabaseConnection.transaction(c -> {
            if (route.getRouteId() != 0) BusDAO.lockRouteRow(c, route.getRouteId());   // route first, then its buses (as updateBus)
            BusDAO.lockRoute(c, route.getRouteId());             // its buses: no booking can add a stop between the check and the save
            for (Bus b : busDAO.findAll(java.time.LocalDate.now()))   // every bus on it must still work with the new stops - checked before writing
                if (route.getRouteId() != 0 && b.getRoute().getRouteId() == route.getRouteId()) {
                    checkStopsKept(b, route);
                    t.applyTo(b, route, b.getDeparture(), b.getReturnDeparture() != null, b.getCapacity(), b.getStatus());
                }
            busDAO.saveRoute(c, route);
            return null;
        });
        busDAO.retime(t);                        // campus arrival of buses on a route with a printed campus time follows the route
    }

    /** PPT method: the reservations for travel dates {@code from} to {@code to}, in departure order. */
    public List<Reservation> viewReservations(java.time.LocalDate from, java.time.LocalDate to) throws SQLException {
        return reservationDAO.findBetween(from, to);
    }

    @Override public String getRole() { return "Admin"; }

    @Override public JFrame openDashboard(CollegeBusSystem system) { return new AdminDashboard(this, system); }

    @Override public String displayDetails() { return "Admin " + username + " (" + getName() + ")"; }

    @Override public Map<String, Object> toMap() { return Map.of("id", getId(), "name", getName(), "firstName", getFirstName(), "username", username); }

    public String getUsername() { return username; }
}

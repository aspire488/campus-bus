package com.collegebus.service;

import com.collegebus.dao.*;
import com.collegebus.model.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** The main system class from the PPT: one object the screens talk to. */
public class CollegeBusSystem {
    private final StudentDAO studentDAO = new StudentDAO();
    private final AdminDAO adminDAO = new AdminDAO();
    private static final User NOBODY = new Admin(0, "-", "-", User.hash("-"));
    private final BusDAO busDAO = new BusDAO();
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final ReservationService reservationService = new ReservationService();

    /** Returns a Student or an Admin as a plain User - the caller does not need to know which (POLYMORPHISM). */
    public User login(boolean admin, String login, String password) throws SQLException {
        if (login.isBlank()) throw new IllegalArgumentException(admin ? "Enter your username." : "Enter your email or admission number.");
        if (password.isEmpty()) throw new IllegalArgumentException("Enter your password.");
        User user = admin ? adminDAO.find(login) : studentDAO.findByLogin(login);
        if (user == null && admin && needsSetup())
            throw new IllegalArgumentException("No administrator yet. Restart the app to run the first-time setup.");
        if (user == null) NOBODY.checkPassword(password);                 // same work as a real check: the time taken does not reveal unknown names
        if (user != null && user.checkPassword(password) && user.needsRehash()) {        // old or cheaper hash: store the current PBKDF2 now
            user.setPasswordHash(User.hash(password));
            if (user instanceof Student) studentDAO.savePassword(user); else adminDAO.savePassword(user);
            return user;
        }
        if (user == null || !user.checkPassword(password))
            throw new IllegalArgumentException(admin ? "Wrong username or password." : "Wrong email / admission number or password.");
        return user;
    }

    /** True until the institution has created its first administrator. */
    public boolean needsSetup() throws SQLException { return adminDAO.count() == 0; }

    /** One-time setup on a new installation: the institution's name and its first administrator. */
    public void setUpInstitution(String institution, String username, String fullName, String password) throws SQLException {
        String name = checkInstitutionName(institution);
        Admin first = Admin.create(username, fullName, password);      // checked in the order the form shows the fields
        if (!adminDAO.insertFirst(first)) throw new IllegalStateException("An administrator already exists. Sign in as admin instead.");
        adminDAO.saveSetting("institution", name);
    }

    /** 2-120 characters, no < > (it is drawn in Swing labels and HTML pages). */
    public static String checkInstitutionName(String institution) {
        String name = institution.trim();
        if (name.length() < 2 || name.length() > 120) throw new IllegalArgumentException("Institution name needs 2-120 characters.");
        if (name.matches(".*[<>].*")) throw new IllegalArgumentException("Institution name cannot contain < or >.");
        return name;
    }

    /** The name entered at setup, e.g. "St. Mary's High School"; a neutral default if none was saved. */
    public String institutionName() {
        try {
            String n = adminDAO.setting("institution");
            return n == null ? "Campus transport" : n;
        } catch (SQLException e) {
            return "Campus transport";
        }
    }

    /** Class start, morning arrival and evening departure for the whole institution. */
    public Timetable timetable() throws SQLException { return adminDAO.timetable(); }

    public void registerStudent(Student s) throws SQLException { studentDAO.insert(s); }

    /** PPT method: trips (either direction) that serve from -> to, leaving at or after {@code after}, optionally matching (part of) a bus number. */
    public List<Bus> searchBus(String from, String to, LocalDate date, LocalTime after, String busNo) throws SQLException {
        List<Bus> result = new ArrayList<>();
        String no = busNo.trim().toUpperCase();
        for (Bus b : busDAO.findTrips(date)) {
            if (b.getRoute().serves(from, to) && !b.timeAt(from).isBefore(after)
                    && (no.isEmpty() || b.getBusNo().contains(no))) result.add(b);
        }
        return result;
    }

    /** PPT method: gives screens access to booking / cancelling. */
    public ReservationService manageReservations() { return reservationService; }

    /** PPT method: live numbers for the admin overview - every value is read from MySQL. */
    public Map<String, Object> displayDetails() throws SQLException {
        LocalDate today = LocalDate.now();
        List<Bus> buses = busDAO.findAll(today), trips = busDAO.findTrips(today);
        int active = (int) buses.stream().filter(Bus::isActive).count(), seats = 0, booked = 0, free = 0;
        for (Bus b : trips) {                                   // every departure counts its own seats
            if (!b.isActive()) continue;
            seats += b.getCapacity(); booked += b.getBookedSeats(); free += b.checkAvailability();
        }
        List<Displayable> board = new ArrayList<>(trips);
        Map<String, Map<String, Object>> days = new LinkedHashMap<>();     // every day from -6 to +7, even with 0 bookings
        for (int i = -6; i <= 7; i++) days.put(today.plusDays(i).toString(), Map.of("date", today.plusDays(i).toString(), "booked", 0, "cancelled", 0));
        for (Object[] row : reservationDAO.dailyCounts()) {
            String d = row[0].toString();
            days.put(d, Map.of("date", d, "booked", ((Number) row[1]).intValue(), "cancelled", ((Number) row[2]).intValue()));
        }
        int cancelledToday = (Integer) days.get(today.toString()).get("cancelled");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", today.toString());
        m.put("activeBuses", active); m.put("totalBuses", buses.size()); m.put("routes", busDAO.findRoutes().size());
        m.put("bookedToday", booked); m.put("freeToday", free); m.put("cancelledToday", cancelledToday);
        m.put("occupancy", seats == 0 ? 0 : booked * 100 / seats);
        m.put("students", studentDAO.count());
        m.put("board", board); m.put("days", new ArrayList<>(days.values()));
        return m;
    }

    /** Every stop name, for the From / To pickers. */
    public List<String> allStops() throws SQLException {
        TreeSet<String> stops = new TreeSet<>();
        for (Route r : busDAO.findRoutes()) stops.addAll(r.stopNames());
        return new ArrayList<>(stops);
    }

    public StudentDAO students() { return studentDAO; }
    public BusDAO buses() { return busDAO; }
}

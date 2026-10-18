package com.collegebus.service;

import com.collegebus.dao.BusDAO;
import com.collegebus.dao.ReservationDAO;
import com.collegebus.model.*;
import com.collegebus.util.DatabaseConnection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.List;

/**
 * Booking rules. Flow:  UI -> ReservationService -> DAO -> JDBC -> MySQL.
 * Java validates first; the UNIQUE keys in MySQL are the final guard if two students click at the same moment.
 */
public class ReservationService {
    private final BusDAO busDAO = new BusDAO();
    private final ReservationDAO reservationDAO = new ReservationDAO();

    /** Books towards the institution (the morning trip), boarding at the first stop - a shortcut for tests and scripts. */
    public Reservation bookSeat(Student student, int busId, LocalDate date, int seatNo) throws SQLException {
        return bookSeat(student, busId, date, "OUT", seatNo);
    }

    /** Same, in either direction: gets on at the first stop (OUT) or off at the last stop (RETURN). */
    public Reservation bookSeat(Student student, int busId, LocalDate date, String direction, int seatNo) throws SQLException {
        Bus trip = busDAO.findTrip(busId, date, direction);
        List<String> stops = trip.getRoute().stopNames();
        return bookSeat(student, busId, date, direction, seatNo, trip.isReturnTrip() ? stops.get(stops.size() - 1) : stops.get(0));
    }

    /**
     * Books one seat on one trip: {@code direction} is "OUT" (towards the institution) or "RETURN" (back);
     * {@code stop} is where the student gets on (OUT) or off (RETURN) and must be on that trip's route.
     * One transaction: the student row and then the bus row are locked, so an admin edit of the bus (capacity, return trip,
     * status) or the same student booking another bus at the same moment waits instead of slipping in between the checks.
     */
    public Reservation bookSeat(Student student, int busId, LocalDate date, String direction, int seatNo, String stop) throws SQLException {
        if (date.isBefore(LocalDate.now())) throw new IllegalArgumentException("Choose today or a later date.");
        try {
            return DatabaseConnection.transaction(c -> {
                if (!locked(c, "SELECT student_id FROM students WHERE student_id = ? FOR UPDATE", student.getId()))
                    throw new IllegalStateException("Your student account no longer exists. Contact the transport office.");
                if (!locked(c, "SELECT bus_id FROM buses WHERE bus_id = ? FOR UPDATE", busId))
                    throw new IllegalArgumentException("That bus no longer exists.");
                Bus bus = busDAO.findTrip(busId, date, direction);          // read after the lock: the bus as it is now
                if (bus.checkAvailability() == 0 && bus.isActive()) throw new IllegalStateException(bus.getBusNo() + " is full on " + date + ".");

                Object[] held = reservationDAO.heldOn(student.getId(), date, bus.getDirection());
                if (held != null) throw new IllegalStateException("You already have seat " + held[0] + " on " + held[2] + (bus.isReturnTrip() ? " for the trip back" : " for the morning trip") + " on this date.");
                if (reservationDAO.bookedSeats(busId, date, bus.getDirection()).contains(seatNo)) throw new IllegalStateException("Seat " + seatNo + " was just taken. Pick another seat.");

                Reservation r = new Reservation(0, student.getId(), student.getName(), bus, date, seatNo, "NEW", null);
                r.setStop(stop);
                r.bookSeat();                                   // seat number, bus status, departure time, booking window
                reservationDAO.insert(c, r);
                return r;
            });
        } catch (SQLIntegrityConstraintViolationException e) {      // the database's UNIQUE / FOREIGN keys had the last word
            String m = String.valueOf(e.getMessage());
            if (m.contains("uq_student_per_trip")) throw new IllegalStateException("You already have a seat for this trip on this date.");
            if (m.contains("uq_seat_per_trip")) throw new IllegalStateException("Seat " + seatNo + " was just taken. Pick another seat.");
            throw new IllegalStateException(m.contains("bus_id") ? "That bus no longer exists." : "Your student account no longer exists. Contact the transport office.");
        }
    }

    /** SELECT ... FOR UPDATE on one row; false if the row does not exist. */
    static boolean locked(java.sql.Connection c, String sql, int id) throws SQLException {
        try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (java.sql.ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** Students may cancel only their own reservations; an admin passes null and may cancel any. */
    public Reservation cancelBooking(Student student, int resId) throws SQLException {
        Reservation r = find(student, resId);
        r.cancelBooking();
        if (!reservationDAO.cancel(resId)) throw new IllegalStateException("This reservation is already cancelled.");   // lost a race with another cancel
        return r;
    }

    /** One reservation; a student may only open their own (an admin passes null). */
    public Reservation find(Student student, int resId) throws SQLException {
        Reservation r = reservationDAO.findById(resId);
        if (student != null && r.getStudentId() != student.getId()) throw new IllegalStateException("This reservation belongs to another student.");
        return r;
    }

    /** Availability module: the bus (with live counts) and the taken seat numbers. */
    public Bus getBus(int busId, LocalDate date) throws SQLException { return busDAO.findById(busId, date); }

    public Bus getTrip(int busId, LocalDate date, String direction) throws SQLException { return busDAO.findTrip(busId, date, direction); }

    public List<Integer> bookedSeats(int busId, LocalDate date, String direction) throws SQLException { return reservationDAO.bookedSeats(busId, date, direction); }

    public List<Object[]> passengers(int busId, LocalDate date, String direction) throws SQLException { return reservationDAO.passengers(busId, date, direction); }

    public Object[] heldOn(Student s, LocalDate date, String direction) throws SQLException { return reservationDAO.heldOn(s.getId(), date, direction); }

    public List<Reservation> reservationsOf(Student s) throws SQLException { return reservationDAO.findByStudent(s.getId()); }
}

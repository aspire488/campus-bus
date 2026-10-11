package com.collegebus.dao;

import com.collegebus.model.Bus;
import com.collegebus.model.Reservation;
import com.collegebus.util.DatabaseConnection;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** All SQL for reservations: insert, cancel, look-ups and seat availability. */
public class ReservationDAO {
    // reservations and buses both have a "status" column, so the reservation's one is renamed res_status
    private static final String SELECT = "SELECT x.res_id, x.student_id, x.travel_date, x.direction, x.seat_no, x.stop, x.status AS res_status, x.booked_at, "
            + "s.name AS student_name, s.department AS student_class, b.*, r.source, r.destination, r.stops, r.distance_km, 0 AS booked, 0 AS booked_return, "
            + "COALESCE((SELECT value FROM settings WHERE name = 'evening_departure'), '16:30') AS evening "
            + "FROM reservations x JOIN students s ON s.student_id = x.student_id "
            + "JOIN buses b ON b.bus_id = x.bus_id JOIN routes r ON r.route_id = b.route_id ";

    private static final String DEPARTS = "IF(x.direction = 'RETURN', b.return_departure, b.departure)";

    private List<Reservation> query(String sql, Object... params) throws SQLException {
        List<Reservation> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var bookedAt = rs.getTimestamp("booked_at");
                    Bus bus = BusDAO.mapBus(rs);
                    if ("RETURN".equals(rs.getString("direction")))                  // the trip back: reversed route, return times
                        bus = bus.returnTrip(java.time.LocalTime.parse(rs.getString("evening")));  // (evening time if the bus no longer runs back)
                    Reservation r = new Reservation(rs.getInt("res_id"), rs.getInt("student_id"), rs.getString("student_name"),
                            bus, rs.getDate("travel_date").toLocalDate(), rs.getInt("seat_no"),
                            rs.getString("res_status"), bookedAt == null ? null : bookedAt.toLocalDateTime());
                    r.setStopAsStored(rs.getString("stop"));
                    r.setStudentClass(rs.getString("student_class"));
                    list.add(r);
                }
            }
        }
        return list;
    }

    /** Inserts a booked reservation and stores the generated id back into the object. */
    public void insert(Reservation r) throws SQLException {
        String sql = "INSERT INTO reservations (student_id, bus_id, travel_date, direction, seat_no, stop, status) VALUES (?,?,?,?,?,?,'BOOKED')";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getStudentId());
            ps.setInt(2, r.getBus().getBusId());
            ps.setDate(3, Date.valueOf(r.getTravelDate()));
            ps.setString(4, r.getBus().getDirection());
            ps.setInt(5, r.getSeatNo());
            ps.setString(6, r.getStop());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setResId(keys.getInt(1));
            }
        }
    }

    /** Returns false if the reservation was not BOOKED any more (e.g. cancelled a moment earlier). */
    public boolean cancel(int resId) throws SQLException {
        return Queries.update("UPDATE reservations SET status = 'CANCELLED' WHERE res_id = ? AND status = 'BOOKED'", resId) == 1;
    }

    public Reservation findById(int resId) throws SQLException {
        List<Reservation> list = query(SELECT + "WHERE x.res_id = ?", resId);
        if (list.isEmpty()) throw new IllegalArgumentException("Reservation not found.");
        return list.get(0);
    }

    public List<Reservation> findByStudent(int studentId) throws SQLException {
        return query(SELECT + "WHERE x.student_id = ? ORDER BY x.travel_date DESC, " + DEPARTS + " DESC", studentId);
    }

    /** Reservations travelling between two dates (inclusive) - never the whole history, which grows by thousands a week. */
    public List<Reservation> findBetween(LocalDate from, LocalDate to) throws SQLException {
        return query(SELECT + "WHERE x.travel_date BETWEEN ? AND ? ORDER BY x.travel_date, " + DEPARTS + ", b.bus_no, x.seat_no",
                Date.valueOf(from), Date.valueOf(to));
    }

    /** Availability module: the seat numbers already taken on one trip (bus, date, direction). */
    public List<Integer> bookedSeats(int busId, LocalDate date, String direction) throws SQLException {
        List<Integer> seats = new ArrayList<>();
        for (Object[] row : Queries.rows("SELECT seat_no FROM reservations WHERE bus_id=? AND travel_date=? AND direction=? AND status='BOOKED'",
                busId, Date.valueOf(date), direction))
            seats.add((Integer) row[0]);
        return seats;
    }

    /** Admin seat map: seat number, student name, admission number and stop for every booked seat on one trip. */
    public List<Object[]> passengers(int busId, LocalDate date, String direction) throws SQLException {
        return Queries.rows("SELECT x.seat_no, s.name, s.reg_no, x.stop FROM reservations x JOIN students s ON s.student_id = x.student_id "
                + "WHERE x.bus_id=? AND x.travel_date=? AND x.direction=? AND x.status='BOOKED' ORDER BY x.seat_no", busId, Date.valueOf(date), direction);
    }

    /**
     * The seat this student already holds that day in that direction, on any bus: {seat_no, bus_id, bus_no}, or null.
     * All morning buses arrive together and all evening buses leave together, so a student can ride only one of them.
     */
    public Object[] heldOn(int studentId, LocalDate date, String direction) throws SQLException {
        List<Object[]> rows = Queries.rows("SELECT x.seat_no, x.bus_id, b.bus_no FROM reservations x JOIN buses b ON b.bus_id = x.bus_id "
                + "WHERE x.student_id=? AND x.travel_date=? AND x.direction=? AND x.status='BOOKED'", studentId, Date.valueOf(date), direction);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int countUpcomingReturns(int busId) throws SQLException {
        return Queries.intValue("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND direction='RETURN' AND status='BOOKED' AND travel_date >= CURDATE()", busId);
    }

    public int countUpcoming(int busId) throws SQLException {
        return Queries.intValue("SELECT COUNT(*) FROM reservations WHERE bus_id=? AND status='BOOKED' AND travel_date >= CURDATE()", busId);
    }

    /** Admin statistics: bookings and cancellations per travel date for the last 7 + next 7 days. */
    public List<Object[]> dailyCounts() throws SQLException {
        return Queries.rows("SELECT travel_date, SUM(status='BOOKED'), SUM(status='CANCELLED') FROM reservations "
                + "WHERE travel_date BETWEEN CURDATE() - INTERVAL 6 DAY AND CURDATE() + INTERVAL 7 DAY GROUP BY travel_date ORDER BY travel_date");
    }
}

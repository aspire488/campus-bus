package com.collegebus.dao;

import com.collegebus.model.Bus;
import com.collegebus.model.Route;
import com.collegebus.model.Timetable;
import com.collegebus.util.DatabaseConnection;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** DAO = Data Access Object: all SQL for buses and routes lives here. */
public class BusDAO {
    private static final String BOOKED = "(SELECT COUNT(*) FROM reservations x WHERE x.bus_id = b.bus_id AND x.travel_date = ? "
      + "AND x.status = 'BOOKED' AND x.direction = '%s')";
    private static final String SELECT_BUS =
        "SELECT b.*, r.source, r.destination, r.stops, r.distance_km, " + BOOKED.formatted("OUT") + " AS booked, "
      + BOOKED.formatted("RETURN") + " AS booked_return FROM buses b JOIN routes r ON r.route_id = b.route_id ";

    /** Turns the current ResultSet row into a Bus object (also used by ReservationDAO). */
    static Bus mapBus(ResultSet rs) throws SQLException {
        Route route = new Route(rs.getInt("route_id"), rs.getString("source"), rs.getString("destination"),
                rs.getString("stops"), rs.getDouble("distance_km"));
        Bus bus = new Bus();
        bus.setBusId(rs.getInt("bus_id"));
        bus.setBusNo(rs.getString("bus_no"));
        Time back = rs.getTime("return_departure");
        bus.load(route, rs.getTime("departure").toLocalTime(), rs.getTime("arrival").toLocalTime(),
                back == null ? null : back.toLocalTime(), rs.getInt("capacity"), rs.getString("status"));
        bus.setBookedSeats(rs.getInt("booked"));
        bus.setReturnBooked(rs.getInt("booked_return"));
        return bus;
    }

    /** All buses (one row each), with the seats booked on {@code date} in each direction. */
    public List<Bus> findAll(LocalDate date) throws SQLException {
        return query(SELECT_BUS + "ORDER BY b.departure", Date.valueOf(date), Date.valueOf(date));
    }

    /** Every departure on {@code date}: each bus towards the institution and, if it has one, its trip back. Sorted by time. */
    public List<Bus> findTrips(LocalDate date) throws SQLException {
        List<Bus> trips = new ArrayList<>();
        for (Bus b : findAll(date)) {
            trips.add(b);
            if (b.returnTrip() != null) trips.add(b.returnTrip());
        }
        trips.sort(java.util.Comparator.comparing(Bus::getDeparture));
        return trips;
    }

    public Bus findById(int busId, LocalDate date) throws SQLException {
        List<Bus> list = query(SELECT_BUS + "WHERE b.bus_id = ?", Date.valueOf(date), Date.valueOf(date), busId);
        if (list.isEmpty()) throw new IllegalArgumentException("That bus no longer exists.");
        return list.get(0);
    }

    /** One direction of one bus: "OUT" or "RETURN". */
    public Bus findTrip(int busId, LocalDate date, String direction) throws SQLException {
        if (!"OUT".equals(direction) && !"RETURN".equals(direction)) throw new IllegalArgumentException("Choose the trip to campus or the trip back.");
        Bus bus = findById(busId, date);
        if ("OUT".equals(direction)) return bus;
        if (bus.returnTrip() == null) throw new IllegalArgumentException(bus.getBusNo() + " does not run a return trip.");
        return bus.returnTrip();
    }

    private List<Bus> query(String sql, Object... params) throws SQLException {
        List<Bus> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapBus(rs));
            }
        }
        return list;
    }

    public void insert(Bus b) throws SQLException {
        try {
            save("INSERT INTO buses (route_id, departure, arrival, return_departure, capacity, status, bus_no) VALUES (?,?,?,?,?,?,?)", b);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("Bus " + b.getBusNo() + " already exists. Use another bus number.");
        }
    }

    public void update(Bus b) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) { update(c, b); }
    }

    /** Same, on {@code c} - inside the transaction that holds the bus row lock (see lock). */
    public void update(Connection c, Bus b) throws SQLException {
        if (save(c, "UPDATE buses SET route_id=?, departure=?, arrival=?, return_departure=?, capacity=?, status=? WHERE bus_no=?", b) == 0)
            throw new IllegalArgumentException("That bus no longer exists.");
    }

    /**
     * Locks one bus row until {@code c}'s transaction ends. Bookings take the same lock, so checks made after it
     * ("no booked seat above the new capacity", "no booked return trip") stay true until the change is committed.
     */
    public static void lock(Connection c, int busId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT bus_id FROM buses WHERE bus_id = ? FOR UPDATE")) {
            ps.setInt(1, busId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("That bus no longer exists.");
            }
        }
    }

    private int save(String sql, Bus b) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) { return save(c, sql, b); }
    }

    private int save(Connection c, String sql, Bus b) throws SQLException {
        if (b.isReturnTrip()) throw new IllegalStateException("Save the bus itself, not its return trip.");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, b.getRoute().getRouteId());
            ps.setTime(2, Time.valueOf(b.getDeparture()));
            ps.setTime(3, Time.valueOf(b.getArrival()));
            ps.setTime(4, b.getReturnDeparture() == null ? null : Time.valueOf(b.getReturnDeparture()));
            ps.setInt(5, b.getCapacity());
            ps.setString(6, b.getStatus());
            ps.setString(7, b.getBusNo());
            return ps.executeUpdate();
        }
    }

    /**
     * Applies the timetable to every bus in one transaction: all of them change, or none. Pick-up times are never moved;
     * campus arrival targets and the evening departure follow the timetable. One-way buses stay one way.
     */
    public void retime(Timetable t) throws SQLException {
        DatabaseConnection.transaction(c -> {
            try (Statement st = c.createStatement()) { st.executeQuery("SELECT bus_id FROM buses FOR UPDATE").close(); }  // no edit slips in between
            List<Bus> all = findAll(LocalDate.now());                // read after the lock: every bus as it is now
            for (Bus b : all)                                        // validate every bus before changing anything
                t.applyTo(b, b.getRoute(), b.getDeparture(), b.getReturnDeparture() != null, b.getCapacity(), b.getStatus());
            try (PreparedStatement ps = c.prepareStatement("UPDATE buses SET arrival=?, return_departure=? WHERE bus_id=?")) {
                for (Bus b : all) {
                    ps.setTime(1, Time.valueOf(b.getArrival()));
                    ps.setTime(2, b.getReturnDeparture() == null ? null : Time.valueOf(b.getReturnDeparture()));
                    ps.setInt(3, b.getBusId());
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    /**
     * Deletes a bus that has no booked seats at all - past trips included, so the travel history is never erased
     * (the foreign key would cascade it away). Cancelled rows go with the bus.
     */
    public void delete(int busId) throws SQLException {
        DatabaseConnection.transaction(c -> {
            lock(c, busId);
            int booked = new ReservationDAO().countBooked(busId);
            if (booked > 0) throw new IllegalStateException("This bus has " + booked + " booked trips on record (past trips included). "
                    + "Set it to maintenance instead, so the travel history is kept.");
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM buses WHERE bus_id = ?")) {
                ps.setInt(1, busId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Highest seat number booked on this bus for today or later (capacity may not go below it). */
    public int highestUpcomingSeat(int busId) throws SQLException {
        return Queries.intValue("SELECT COALESCE(MAX(seat_no),0) FROM reservations WHERE bus_id=? AND status='BOOKED' AND travel_date >= CURDATE()", busId);
    }

    // ---------------- routes ----------------

    public List<Route> findRoutes() throws SQLException {
        List<Route> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM routes ORDER BY source, destination");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Route(rs.getInt("route_id"), rs.getString("source"), rs.getString("destination"),
                    rs.getString("stops"), rs.getDouble("distance_km")));
        }
        return list;
    }

    /** Inserts a new route (id 0) or updates an existing one. */
    public void saveRoute(Route r) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) { saveRoute(c, r); }
    }

    /** Same, on {@code c} (inside the transaction that locked the route's buses). */
    public void saveRoute(Connection c, Route r) throws SQLException {
        boolean add = r.getRouteId() == 0;
        try (PreparedStatement ps = c.prepareStatement(add ? "INSERT INTO routes (source, destination, stops, distance_km) VALUES (?,?,?,?)"
                : "UPDATE routes SET source=?, destination=?, stops=?, distance_km=? WHERE route_id=?")) {
            ps.setString(1, r.getSource());
            ps.setString(2, r.getDestination());
            ps.setString(3, r.getStops());
            ps.setObject(4, r.getDistanceKm() > 0 ? r.getDistanceKm() : null);
            if (!add) ps.setInt(5, r.getRouteId());
            ps.executeUpdate();
        }
    }

    /** Locks one route row until {@code c}'s transaction ends and returns it as saved now. */
    public static Route lockRouteRow(Connection c, int routeId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM routes WHERE route_id = ? FOR UPDATE")) {
            ps.setInt(1, routeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("That route no longer exists.");
                return new Route(rs.getInt("route_id"), rs.getString("source"), rs.getString("destination"), rs.getString("stops"), rs.getDouble("distance_km"));
            }
        }
    }

    /** Locks every bus on a route until {@code c}'s transaction ends (bookings take the same bus locks). */
    public static void lockRoute(Connection c, int routeId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT bus_id FROM buses WHERE route_id = ? FOR UPDATE")) {
            ps.setInt(1, routeId);
            ps.executeQuery().close();
        }
    }

    public void deleteRoute(int routeId) throws SQLException {
        if (Queries.intValue("SELECT COUNT(*) FROM buses WHERE route_id = ?", routeId) > 0)
            throw new IllegalStateException("Buses still use this route. Move them to another route first.");
        Queries.update("DELETE FROM routes WHERE route_id = ?", routeId);
    }
}

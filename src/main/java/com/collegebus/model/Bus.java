package com.collegebus.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bus service: vehicle number, route, timing and seat capacity.
 * One bus runs its route both ways: towards the institution (departure/arrival) and, optionally, back again
 * (returnDeparture, same route reversed, same trip length). Each direction has its own seats.
 */
public class Bus implements Displayable {
    private int busId;
    private String busNo;
    private Route route;
    private LocalTime departure;
    private LocalTime arrival;
    private int capacity;
    private String status = "ACTIVE";
    private LocalTime returnDeparture;     // null = this bus runs one way only
    private boolean returnTrip;            // true = this object describes the trip back (see returnTrip())
    private int bookedSeats;               // for one travel date and this direction, filled by BusDAO
    private int returnBooked;              // seats booked on the trip back, same date

    /** PPT method: validates and sets all bus details (used for a new bus). */
    public void addBus(String busNo, Route route, LocalTime departure, LocalTime arrival, int capacity) {
        if (!busNo.trim().matches("([A-Za-z]{1,4}-?)?\\d{1,4}")) throw new IllegalArgumentException("Bus number looks like 05 or CB-07.");
        updateBus(route, departure, arrival, capacity, status);
        this.busNo = busNo.trim().toUpperCase();
    }

    /** PPT method: validates and changes route, timing, capacity and status (keeps the return time). */
    public void updateBus(Route route, LocalTime departure, LocalTime arrival, int capacity, String status) {
        updateBus(route, departure, arrival, returnDeparture, capacity, status);
    }

    /** Same, including the return departure (null = one way); everything is checked before anything changes. */
    public void updateBus(Route route, LocalTime departure, LocalTime arrival, LocalTime returnDeparture, int capacity, String status) {
        if (returnTrip) throw new IllegalStateException("Change the bus itself, not its return trip.");
        if (route == null) throw new IllegalArgumentException("Choose a route.");
        if (departure == null || arrival == null) throw new IllegalArgumentException("Enter departure and arrival times.");
        if (!arrival.isAfter(departure)) throw new IllegalArgumentException("Arrival must be after departure.");
        if (!"ACTIVE".equals(status) && !"MAINTENANCE".equals(status)) throw new IllegalArgumentException("Status must be ACTIVE or MAINTENANCE.");
        if (capacity < 8 || capacity > 60) throw new IllegalArgumentException("Capacity must be between 8 and 60 seats.");
        if (capacity < bookedSeats) throw new IllegalArgumentException("Capacity cannot be lower than seats already booked.");
        LocalTime lastPickUp = departure.plusMinutes(route.lastTimedMinutes());
        if (arrival.isBefore(lastPickUp) || lastPickUp.isBefore(departure))
            throw new IllegalArgumentException("The bus still picks up at " + lastPickUp + ", after its campus arrival " + arrival + ".");
        checkReturn(returnDeparture, departure, arrival);
        this.returnDeparture = returnDeparture;
        this.route = route;
        this.departure = departure;
        this.arrival = arrival;
        this.capacity = capacity;
        this.status = status;
    }

    /** Sets the fields exactly as stored in the database. No checks: a stored row must always stay readable. */
    public void load(Route route, LocalTime departure, LocalTime arrival, LocalTime returnDeparture, int capacity, String status) {
        this.route = route;
        this.departure = departure;
        this.arrival = arrival;
        this.returnDeparture = returnDeparture;
        this.capacity = capacity;
        this.status = status;
    }

    private static void checkReturn(LocalTime back, LocalTime departure, LocalTime arrival) {
        if (back == null) return;
        if (!back.isAfter(arrival)) throw new IllegalArgumentException("The return trip must leave after " + arrival + ", when the bus arrives.");
        if (back.plus(Duration.between(departure, arrival)).isBefore(back)) throw new IllegalArgumentException("The return trip must end before midnight.");
    }

    /** The same bus on its way back: route reversed, return times, its own seat count. Null if it runs one way only. */
    public Bus returnTrip() {
        return returnTrip(null);
    }

    /** Same, but a bus that no longer runs back (old bookings) is shown leaving at {@code ifNone}, e.g. the evening time. */
    public Bus returnTrip(LocalTime ifNone) {
        LocalTime back = returnDeparture != null ? returnDeparture : ifNone;
        if (back == null) return null;
        Bus b = new Bus();
        b.busId = busId; b.busNo = busNo; b.route = route; b.departure = departure; b.arrival = arrival;
        b.capacity = capacity; b.status = status; b.returnDeparture = back;
        b.returnTrip = true; b.bookedSeats = returnBooked; b.returnBooked = returnBooked;
        return b;
    }

    /** PPT method (Availability module): free seats = capacity - booked seats. */
    public int checkAvailability() {
        return isActive() ? capacity - bookedSeats : 0;
    }

    /** PPT method. */
    public String displayBus() {
        return busNo + " | " + getRoute().getName() + " | " + getDeparture() + "-" + getArrival() + " | " + checkAvailability() + "/" + capacity + " seats free";
    }

    /**
     * "Where is my bus?" - where the bus should be at {@code now} on {@code date}, worked out from the timetable.
     * ponytail: estimate only; buses carry no GPS. A real tracker would replace this with the last reported position.
     */
    public Map<String, Object> estimatePosition(LocalDate date, LocalDateTime now) {
        List<Integer> mins = minutes();
        List<String> names = getRoute().stopNames();
        int n = mins.size(), total = mins.get(n - 1);
        double m = Duration.between(LocalDateTime.of(date, getDeparture()), now).toSeconds() / 60.0;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("updated", now.toLocalTime().withSecond(0).withNano(0).toString());
        if (!isActive()) { p.put("state", "NOT_RUNNING"); p.put("progress", 0); return p; }
        if (m < 0) {
            p.put("state", "WAITING"); p.put("progress", 0); p.put("nextStop", names.get(0));
            p.put("minutesToNext", (long) Math.ceil(-m));
            return p;
        }
        if (m >= total) { p.put("state", "ARRIVED"); p.put("progress", 1); p.put("lastStop", names.get(n - 1)); return p; }
        int i = 0;
        while (mins.get(i + 1) <= m) i++;                       // the bus is between stop i and stop i + 1
        double part = (m - mins.get(i)) / (mins.get(i + 1) - mins.get(i));
        p.put("state", "ON_ROUTE");
        p.put("progress", Math.round((i + part) / (n - 1) * 1000) / 1000.0);   // stops are evenly spaced on the drawn line
        p.put("lastStop", names.get(i)); p.put("nextStop", names.get(i + 1));
        p.put("nextTime", getDeparture().plusMinutes(mins.get(i + 1)).toString());
        p.put("minutesToNext", (long) Math.ceil(mins.get(i + 1) - m));
        p.put("minutesToEnd", (long) Math.ceil(total - m));
        return p;
    }

    /** When this trip reaches {@code stop} (blank = when it leaves the first stop): the published time, or the estimate if none is published. */
    public LocalTime timeAt(String stop) {
        int i = stop == null || stop.isBlank() ? 0 : getRoute().stopNames().indexOf(stop);
        return getDeparture().plusMinutes(minutes().get(Math.max(i, 0)));
    }

    /** Minutes after departure for every stop of this trip: published ones as printed, the rest estimated. */
    private List<Integer> minutes() {
        int trip = (int) getDurationMinutes();
        return getRoute().resolvedMinutes(trip);
    }

    /**
     * Every stop with its time and where the time comes from: "published" (printed in the schedule), "target" (the campus
     * arrival set by the transport office) or "estimated" (between published times, or anything on the trip back).
     */
    public List<Map<String, Object>> stopTimes() {
        List<String> names = getRoute().stopNames();
        List<Integer> published = getRoute().stopMinutes(), mins = minutes();
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String kind = published.get(i) != null ? "published" : !returnTrip && i == names.size() - 1 ? "target" : "estimated";
            list.add(Map.of("name", names.get(i), "time", getDeparture().plusMinutes(mins.get(i)).toString(), "kind", kind));
        }
        return list;
    }

    /** True when the campus arrival is the office's target, not a time printed in the schedule. */
    public boolean isArrivalTarget() { return !returnTrip && !route.lastStopTimed(); }

    /** The last printed pick-up time on the way to campus. */
    public LocalTime lastPickUp() { return departure.plusMinutes(route.lastTimedMinutes()); }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public long getDurationMinutes() { return Duration.between(departure, arrival).toMinutes(); }
    public int getOccupancyPercent() { return capacity == 0 ? 0 : bookedSeats * 100 / capacity; }
    /** Both directions together, for the admin bus list. */
    public int getBookedBothWays() { return bookedSeats + returnBooked; }

    @Override public String displayDetails() { return "Bus " + displayBus(); }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Route r = getRoute();
        m.put("id", busId); m.put("busNo", busNo); m.put("direction", getDirection()); m.put("route", r.getName());
        m.put("source", r.getSource()); m.put("destination", r.getDestination());
        m.put("departure", getDeparture().toString()); m.put("arrival", getArrival().toString());
        m.put("stops", r.stopNames()); m.put("stopTimes", stopTimes()); m.put("arrivalTarget", isArrivalTarget()); m.put("duration", getDurationMinutes()); m.put("capacity", capacity); m.put("booked", bookedSeats);
        m.put("available", checkAvailability()); m.put("occupancy", getOccupancyPercent()); m.put("status", status);
        return m;
    }

    public int getBusId() { return busId; }
    public void setBusId(int busId) { this.busId = busId; }
    public String getBusNo() { return busNo; }
    public void setBusNo(String busNo) { this.busNo = busNo; }
    /** The route in this trip's direction (reversed on the way back). */
    public Route getRoute() { return returnTrip ? route.reversed((int) getDurationMinutes()) : route; }
    public LocalTime getDeparture() { return returnTrip ? returnDeparture : departure; }
    public LocalTime getArrival() { return returnTrip ? returnDeparture.plus(Duration.between(departure, arrival)) : arrival; }
    /** "OUT" towards the institution, "RETURN" on the way back. */
    public String getDirection() { return returnTrip ? "RETURN" : "OUT"; }
    public boolean isReturnTrip() { return returnTrip; }
    public LocalTime getReturnDeparture() { return returnDeparture; }
    public void setReturnBooked(int seats) { returnBooked = seats; }
    public int getCapacity() { return capacity; }
    public String getStatus() { return status; }
    public int getBookedSeats() { return bookedSeats; }
    public void setBookedSeats(int bookedSeats) { this.bookedSeats = bookedSeats; }
}

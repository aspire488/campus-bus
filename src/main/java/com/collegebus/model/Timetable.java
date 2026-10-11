package com.collegebus.model;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The institution's bus timing, set by the transport office.
 * Pick-up times are the bus's own (as published in the schedule) and are never moved by this class. It sets:
 * the campus arrival window (the target for routes whose schedule prints no campus time), the class start it serves,
 * and the one evening time when every bus that runs back leaves the campus.
 */
public class Timetable implements Displayable {
    public static final Timetable DEFAULT = new Timetable(LocalTime.of(9, 0), LocalTime.of(8, 30), LocalTime.of(8, 45), LocalTime.of(16, 30));
    /** A route is flagged for checking when its last printed pick-up leaves less than this before the arrival window opens. */
    public static final int VERIFY_GAP_MINUTES = 20;

    private final LocalTime classStart;
    private final LocalTime arriveFrom;
    private final LocalTime arriveBy;
    private final LocalTime evening;

    public Timetable(LocalTime classStart, LocalTime arriveFrom, LocalTime arriveBy, LocalTime evening) {
        if (classStart == null || arriveFrom == null || arriveBy == null || evening == null)
            throw new IllegalArgumentException("Enter all four times (24-hour HH:MM).");
        if (!arriveFrom.isBefore(arriveBy)) throw new IllegalArgumentException("The arrival window must start before it ends.");
        if (arriveBy.isAfter(classStart)) throw new IllegalArgumentException("Buses must arrive by the time classes start.");
        if (!evening.isAfter(classStart)) throw new IllegalArgumentException("The evening departure must be after classes start.");
        this.classStart = classStart;
        this.arriveFrom = arriveFrom;
        this.arriveBy = arriveBy;
        this.evening = evening;
    }

    /** Campus arrival for a bus leaving at {@code departure}: the printed time if the route has one, otherwise the window's end (a target). */
    public LocalTime arrivalFor(Route route, LocalTime departure) {
        return route.lastStopTimed() ? departure.plusMinutes(route.lastTimedMinutes()) : arriveBy;
    }

    /** Sets a bus's route, first pick-up, capacity and status; campus arrival and the evening trip follow this timetable. */
    public void applyTo(Bus bus, Route route, LocalTime departure, boolean runsBack, int capacity, String status) {
        if (departure == null) throw new IllegalArgumentException("Enter the first pick-up time (HH:MM).");
        bus.updateBus(route, departure, arrivalFor(route, departure), runsBack ? evening : null, capacity, status);
    }

    /** A new bus whose campus arrival and evening trip follow this timetable. */
    public Bus newBus(String busNo, Route route, LocalTime departure, boolean runsBack, int capacity, String status) {
        if (departure == null) throw new IllegalArgumentException("Enter the first pick-up time (HH:MM).");
        Bus b = new Bus();
        b.addBus(busNo, route, departure, arrivalFor(route, departure), capacity);
        applyTo(b, route, departure, runsBack, capacity, status);
        return b;
    }

    /** True when a bus's last printed pick-up is close to the arrival window - the office should confirm the route is feasible. */
    public boolean needsCheck(Bus bus) {
        return bus.isArrivalTarget() && bus.lastPickUp().plusMinutes(VERIFY_GAP_MINUTES).isAfter(arriveFrom);
    }

    public LocalTime getClassStart() { return classStart; }
    public LocalTime getArriveFrom() { return arriveFrom; }
    public LocalTime getArriveBy() { return arriveBy; }
    public LocalTime getEvening() { return evening; }

    @Override public String displayDetails() {
        return "Classes " + classStart + ", buses arrive " + arriveFrom + "–" + arriveBy + ", evening departure " + evening;
    }

    @Override public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classStart", classStart.toString()); m.put("arriveFrom", arriveFrom.toString());
        m.put("arriveBy", arriveBy.toString()); m.put("evening", evening.toString());
        return m;
    }
}

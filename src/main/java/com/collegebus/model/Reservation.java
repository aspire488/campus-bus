package com.collegebus.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** One seat on one bus for one travel date. */
public class Reservation implements Displayable {
    private int resId;
    private int studentId;
    private String studentName;
    private Bus bus;
    private LocalDate travelDate;
    private int seatNo;
    private String status;
    private LocalDateTime bookedAt;
    private String stop;                   // where the student gets on (to campus) or off (back home); null on older bookings
    private String studentClass;           // e.g. "S3 CS2", for the admin's department filter

    public Reservation(int resId, int studentId, String studentName, Bus bus, LocalDate travelDate, int seatNo, String status, LocalDateTime bookedAt) {
        this.resId = resId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.bus = bus;
        this.travelDate = travelDate;
        this.seatNo = seatNo;
        this.status = status;
        this.bookedAt = bookedAt;
    }

    /** PPT method: checks the seat rules that belong to a single reservation, then marks it BOOKED. */
    public void bookSeat() {
        if (seatNo < 1 || seatNo > bus.getCapacity()) throw new IllegalArgumentException("Seat " + seatNo + " does not exist on " + bus.getBusNo() + ".");
        if (!bus.isActive()) throw new IllegalStateException(bus.getBusNo() + " is under maintenance. Choose another bus.");
        if (hasDeparted()) throw new IllegalStateException(bus.getBusNo() + " has already left at " + bus.getDeparture() + ".");
        if (travelDate.isAfter(LocalDate.now().plusDays(7))) throw new IllegalArgumentException("Seats open 7 days before travel.");
        status = "BOOKED";
        bookedAt = LocalDateTime.now();
    }

    /** PPT method: a reservation can be cancelled only while it is booked and the bus has not left. */
    public void cancelBooking() {
        if ("CANCELLED".equals(status)) throw new IllegalStateException("This reservation is already cancelled.");
        if (hasDeparted()) throw new IllegalStateException("The bus has already left, so this reservation cannot be cancelled.");
        status = "CANCELLED";
    }

    /** PPT method. */
    public String displayBooking() {
        return getCode() + " | " + bus.getBusNo() + " " + bus.getRoute().getName() + " | " + travelDate + " " + bus.getDeparture() + " | Seat " + seatNo + " | " + status;
    }

    public boolean hasDeparted() { return LocalDateTime.of(travelDate, bus.getDeparture()).isBefore(LocalDateTime.now()); }

    /** UPCOMING, CURRENT (bus on the road now), COMPLETED or CANCELLED. */
    public String getPhase() {
        if ("CANCELLED".equals(status)) return "CANCELLED";
        if (!hasDeparted()) return "UPCOMING";
        return LocalDateTime.of(travelDate, bus.getArrival()).isAfter(LocalDateTime.now()) ? "CURRENT" : "COMPLETED";
    }

    public String getCode() { return "CBR" + resId; }

    @Override public String displayDetails() { return "Reservation " + displayBooking(); }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", resId); m.put("code", getCode()); m.put("student", studentName); m.put("seat", seatNo);
        m.put("date", travelDate.toString()); m.put("status", status); m.put("phase", getPhase());
        m.put("bookedAt", bookedAt == null ? "" : bookedAt.toString().replace('T', ' ').substring(0, 16));
        m.put("bus", bus.toMap()); m.put("stop", stop == null ? "" : stop);
        return m;
    }

    public int getResId() { return resId; }
    public void setResId(int resId) { this.resId = resId; }
    public int getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public Bus getBus() { return bus; }
    public LocalDate getTravelDate() { return travelDate; }
    public int getSeatNo() { return seatNo; }
    public String getStatus() { return status; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public String getStop() { return stop; }
    public String getStudentClass() { return studentClass; }
    public void setStudentClass(String studentClass) { this.studentClass = studentClass; }

    /** As read from the database: kept even if the route was edited after booking. */
    public void setStopAsStored(String stop) { this.stop = stop; }

    /** The student's own stop: on the trip's route, and not the campus end of it. */
    public void setStop(String stop) {
        if (stop != null) {
            java.util.List<String> names = bus.getRoute().stopNames();
            int i = names.indexOf(stop), campus = bus.isReturnTrip() ? 0 : names.size() - 1;
            if (i < 0 || i == campus) throw new IllegalArgumentException("Choose the stop where you get " + (bus.isReturnTrip() ? "off." : "on."));
        }
        this.stop = stop;
    }
}

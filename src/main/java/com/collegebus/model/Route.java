package com.collegebus.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bus route with its stops, stored as "Name:minutes,Name,..." - minutes after the first stop, as published.
 * A stop without minutes has no published time (many printed schedules list some stops without one);
 * when a time is needed it is estimated between its neighbours and always shown as an estimate.
 */
public class Route implements Displayable {
    private int routeId;
    private String source;
    private String destination;
    private String stops;
    private double distanceKm;             // 0 = not published
    private boolean estimated;             // a derived route (the trip back): no time on it is published

    public Route(int routeId, String source, String destination, String stops, double distanceKm) {
        this.routeId = routeId;
        this.source = source;
        this.destination = destination;
        this.stops = stops;
        this.distanceKm = distanceKm;
    }

    /** Builds a route from a comma separated stop list, e.g. "Aluva:0, Kalamassery:14, HMT, College:40" (HMT has no time). */
    public static Route create(int id, String stopList, double km) {
        String[] parts = stopList.trim().split("\\s*,\\s*");
        if (parts.length < 2) throw new IllegalArgumentException("A route needs at least two stops.");
        List<String> names = new ArrayList<>(), clean = new ArrayList<>();
        int last = -1;
        for (String p : parts) {
            String[] s = p.split(":", -1);
            if (s.length > 2 || s[0].isBlank() || (s.length == 2 && !s[1].trim().matches("\\d{1,3}")))
                throw new IllegalArgumentException("Write each stop as Name:minutes, or just Name if it has no published time (problem: \"" + p + "\").");
            String name = s[0].trim();
            Integer min = s.length == 2 ? Integer.valueOf(s[1].trim()) : null;
            if (name.length() > 40) throw new IllegalArgumentException("Stop names can be at most 40 characters.");
            if (!name.matches("[\\p{L}\\d .'()&/-]+"))                  // no < > so Swing never treats a name as HTML
                throw new IllegalArgumentException("Stop names can use letters, digits, spaces and . ' ( ) & / - (problem: \"" + name + "\").");
            if (names.contains(name)) throw new IllegalArgumentException(name + " appears twice on the route.");
            if (names.isEmpty() && (min == null || min != 0)) throw new IllegalArgumentException("The first stop needs a time: minute 0.");
            if (min != null && min < last) throw new IllegalArgumentException("Stop times must not go backwards along the route (" + name + ").");
            if (min != null) last = min;
            names.add(name);
            clean.add(min == null ? name : name + ":" + min);
        }
        String stops = String.join(",", clean);
        if (stops.length() > 1500) throw new IllegalArgumentException("Too many stops - keep the list under 1500 characters.");
        if (km != 0 && !(km > 0 && km < 1000)) throw new IllegalArgumentException("Distance must be between 0 and 1000 km (leave it empty if unknown).");
        return new Route(id, names.get(0), names.get(names.size() - 1), stops, km);
    }

    /** Published minutes after the first stop, in route order; null where no time is published. */
    public List<Integer> stopMinutes() {
        List<Integer> mins = new ArrayList<>();
        for (String p : stops.split(",")) {
            String[] s = p.split(":");
            mins.add(s.length == 2 && !estimated ? Integer.valueOf(s[1].trim()) : null);
        }
        return mins;
    }

    /**
     * Every stop's minutes, with the gaps filled: an untimed last stop (the campus) gets {@code endMinutes},
     * other untimed stops are spread evenly between their timed neighbours. For estimates only - never shown as published.
     */
    public List<Integer> resolvedMinutes(int endMinutes) {
        List<Integer> m = new ArrayList<>();
        for (String p : stops.split(",")) m.add(p.contains(":") ? Integer.valueOf(p.split(":")[1].trim()) : null);
        int n = m.size();
        if (m.get(n - 1) == null) m.set(n - 1, Math.max(endMinutes, lastTimedMinutes()));
        for (int i = 1; i < n; i++) {
            if (m.get(i) != null) continue;
            int j = i;
            while (m.get(j) == null) j++;                       // next timed stop (the last one is always timed now)
            int a = m.get(i - 1), b = m.get(j);
            for (int k = i; k < j; k++) m.set(k, a + (b - a) * (k - i + 1) / (j - i + 1));
        }
        return m;
    }

    /** Minutes of the last stop that has a published time. */
    public int lastTimedMinutes() {
        int last = 0;
        for (String p : stops.split(",")) if (p.contains(":")) last = Integer.parseInt(p.split(":")[1].trim());
        return last;
    }

    /** False when the route ends at a stop with no published time (e.g. the campus in a schedule that lists boarding points only). */
    public boolean lastStopTimed() { return stops.substring(stops.lastIndexOf(',') + 1).contains(":"); }

    /** The same road driven the other way, for a trip that takes {@code totalMinutes}; every time on it is derived. */
    public Route reversed(int totalMinutes) {
        List<String> names = stopNames();
        List<Integer> mins = resolvedMinutes(totalMinutes);
        List<String> back = new ArrayList<>();
        for (int i = names.size() - 1; i >= 0; i--) back.add(names.get(i) + ":" + (totalMinutes - mins.get(i)));
        Route r = new Route(routeId, destination, source, String.join(",", back), distanceKm);
        r.estimated = true;
        return r;
    }

    public List<String> stopNames() {
        List<String> names = new ArrayList<>();
        for (String p : stops.split(",")) names.add(p.split(":")[0].trim());
        return names;
    }

    /** True if a passenger can board at {@code from} and get off later at {@code to} (blank = any). */
    public boolean serves(String from, String to) {
        List<String> n = stopNames();
        int a = from.isBlank() ? 0 : n.indexOf(from), b = to.isBlank() ? n.size() - 1 : n.indexOf(to);
        return a >= 0 && b >= 0 && a < b;
    }

    public String getName() { return source + " → " + destination; }

    @Override public String displayDetails() { return getName() + (distanceKm > 0 ? " (" + distanceKm + " km)" : ""); }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", routeId); m.put("source", source); m.put("destination", destination);
        m.put("name", getName()); m.put("km", distanceKm);
        return m;
    }

    @Override public String toString() { return getName(); }     // shown in Swing combo boxes

    public int getRouteId() { return routeId; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public String getStops() { return stops; }
    public double getDistanceKm() { return distanceKm; }
}

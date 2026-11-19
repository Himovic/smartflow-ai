package com.dnd.smartflow.ai.routing;

import com.dnd.smartflow.ai.routing.RoutingModels.*;
import com.dnd.smartflow.fleet.service.EmissionCalculator;

import java.util.*;

/**
 * Deterministic capacitated VRP solver used when the OR-Tools service is unavailable: Clarke-Wright savings
 * (capacity, max route duration), priority-aware ordering, then 2-opt improvement per route. The baseline
 * ("original") plan serves stops in their received order with first-fit vehicle assignment, which is how
 * dispatchers typically plan by hand; both plans use the same distance model.
 */
public class HeuristicRouteSolver {

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    public Solution solve(Problem p) {
        int n = p.stops().size();
        double[][] d = matrix(p);
        List<String> notes = new ArrayList<>();
        List<VehicleSpec> vehicles = new ArrayList<>(p.vehicles());
        vehicles.sort(Comparator.comparingDouble(VehicleSpec::capacityKg).reversed());
        double maxCap = vehicles.isEmpty() ? 0 : vehicles.get(0).capacityKg();

        // 1. Clarke-Wright savings: start with one route per stop, merge route ends by decreasing saving.
        List<LinkedList<Integer>> routes = new ArrayList<>();
        Map<Integer, LinkedList<Integer>> routeOf = new HashMap<>();
        List<String> unassigned = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            if (p.stops().get(i - 1).demandKg() > maxCap) {
                unassigned.add(p.stops().get(i - 1).id());
                continue;
            }
            LinkedList<Integer> r = new LinkedList<>(List.of(i));
            routes.add(r);
            routeOf.put(i, r);
        }
        List<double[]> savings = new ArrayList<>();
        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++)
                savings.add(new double[]{d[0][i] + d[0][j] - d[i][j], i, j});
        savings.sort((a, b) -> Double.compare(b[0], a[0]));
        for (double[] s : savings) {
            int i = (int) s[1], j = (int) s[2];
            LinkedList<Integer> ri = routeOf.get(i), rj = routeOf.get(j);
            if (ri == null || rj == null || ri == rj) continue;
            if (load(p, ri) + load(p, rj) > maxCap) continue;
            LinkedList<Integer> merged = null;
            if (ri.getLast() == i && rj.getFirst() == j) { merged = new LinkedList<>(ri); merged.addAll(rj); }
            else if (rj.getLast() == j && ri.getFirst() == i) { merged = new LinkedList<>(rj); merged.addAll(ri); }
            else if (ri.getFirst() == i && rj.getFirst() == j) { merged = new LinkedList<>(ri); Collections.reverse(merged); merged.addAll(rj); }
            else if (ri.getLast() == i && rj.getLast() == j) { merged = new LinkedList<>(ri); LinkedList<Integer> rev = new LinkedList<>(rj); Collections.reverse(rev); merged.addAll(rev); }
            if (merged == null) continue;
            if (duration(p, d, merged) > p.maxRouteMinutes()) continue;
            routes.remove(ri);
            routes.remove(rj);
            routes.add(merged);
            for (int k : merged) routeOf.put(k, merged);
        }

        // 2. Assign routes to vehicles (largest load first, best-fit), keep leftovers unassigned.
        routes.sort((a, b) -> Double.compare(load(p, b), load(p, a)));
        List<VehicleRoute> result = new ArrayList<>();
        List<VehicleSpec> free = new ArrayList<>(vehicles);
        for (LinkedList<Integer> r : routes) {
            double l = load(p, r);
            VehicleSpec best = free.stream().filter(v -> v.capacityKg() >= l).min(Comparator.comparingDouble(VehicleSpec::capacityKg)).orElse(null);
            if (best == null) {
                r.forEach(i -> unassigned.add(p.stops().get(i - 1).id()));
                continue;
            }
            free.remove(best);
            List<Integer> improved = twoOpt(d, prioritize(p, r));
            result.add(build(p, d, best, improved));
        }
        if (!unassigned.isEmpty()) notes.add(unassigned.size() + " stop(s) could not be served with the available fleet capacity");

        Solution baseline = baseline(p, d, vehicles);
        return summarize("Clarke-Wright savings + 2-opt (built-in deterministic engine)", result, unassigned, baseline.optimizedDistanceKm(),
                baseline.estimatedCo2Kg(), notes);
    }

    /**
     * Manual-dispatch reference plan ("before optimization"): stops grouped by town (alphabetically, as a
     * dispatcher's delivery list is usually sorted) and loaded first-fit into vehicles.
     */
    public Solution baseline(Problem p, double[][] d, List<VehicleSpec> vehicles) {
        List<VehicleRoute> routes = new ArrayList<>();
        List<String> unassigned = new ArrayList<>();
        Iterator<VehicleSpec> it = p.vehicles().iterator();
        VehicleSpec current = it.hasNext() ? it.next() : null;
        List<Integer> seq = new ArrayList<>();
        double l = 0;
        List<Integer> order = new ArrayList<>();
        for (int i = 1; i <= p.stops().size(); i++) order.add(i);
        order.sort(Comparator.comparing(i -> town(p.stops().get(i - 1).label())));
        for (int i : order) {
            double dem = p.stops().get(i - 1).demandKg();
            if (current != null && l + dem > current.capacityKg() && !seq.isEmpty()) {
                routes.add(build(p, d, current, seq));
                seq = new ArrayList<>();
                l = 0;
                current = it.hasNext() ? it.next() : null;
            }
            if (current == null || dem > current.capacityKg()) {
                unassigned.add(p.stops().get(i - 1).id());
                continue;
            }
            seq.add(i);
            l += dem;
        }
        if (current != null && !seq.isEmpty()) routes.add(build(p, d, current, seq));
        return summarize("baseline", routes, unassigned, 0, 0, List.of());
    }

    private static String town(String label) {
        int a = label == null ? -1 : label.lastIndexOf('(');
        return a < 0 ? String.valueOf(label) : label.substring(a + 1).replace(")", "").trim();
    }

    public double[][] matrix(Problem p) {
        int n = p.stops().size();
        double[][] pts = new double[n + 1][2];
        pts[0] = new double[]{p.depot().latitude(), p.depot().longitude()};
        for (int i = 0; i < n; i++) pts[i + 1] = new double[]{p.stops().get(i).latitude(), p.stops().get(i).longitude()};
        double[][] d = new double[n + 1][n + 1];
        for (int i = 0; i <= n; i++)
            for (int j = 0; j <= n; j++)
                d[i][j] = i == j ? 0 : haversineKm(pts[i][0], pts[i][1], pts[j][0], pts[j][1]) * p.roadFactor();
        return d;
    }

    private List<Integer> prioritize(Problem p, List<Integer> r) {
        // Urgent stops (priority >= 3) first, then by time-window end; the rest keeps savings order.
        List<Integer> urgent = r.stream().filter(i -> p.stops().get(i - 1).priority() >= 3)
                .sorted(Comparator.comparing(i -> Optional.ofNullable(p.stops().get(i - 1).timeWindowEnd()).orElse(Integer.MAX_VALUE))).toList();
        List<Integer> out = new ArrayList<>(urgent);
        r.stream().filter(i -> !urgent.contains(i)).forEach(out::add);
        return out;
    }

    private List<Integer> twoOpt(double[][] d, List<Integer> route) {
        List<Integer> best = new ArrayList<>(route);
        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 200) {
            improved = false;
            for (int i = 0; i < best.size() - 1; i++) {
                for (int k = i + 1; k < best.size(); k++) {
                    List<Integer> cand = new ArrayList<>(best.subList(0, i));
                    List<Integer> mid = new ArrayList<>(best.subList(i, k + 1));
                    Collections.reverse(mid);
                    cand.addAll(mid);
                    cand.addAll(best.subList(k + 1, best.size()));
                    if (length(d, cand) + 1e-9 < length(d, best)) {
                        best = cand;
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    static double length(double[][] d, List<Integer> r) {
        double sum = 0;
        int prev = 0;
        for (int i : r) { sum += d[prev][i]; prev = i; }
        return sum + d[prev][0];
    }

    private double load(Problem p, List<Integer> r) {
        return r.stream().mapToDouble(i -> p.stops().get(i - 1).demandKg()).sum();
    }

    private double duration(Problem p, double[][] d, List<Integer> r) {
        return length(d, r) / p.averageSpeedKmh() * 60 + r.stream().mapToInt(i -> p.stops().get(i - 1).serviceMinutes()).sum();
    }

    private VehicleRoute build(Problem p, double[][] d, VehicleSpec v, List<Integer> seq) {
        List<RouteStop> stops = new ArrayList<>();
        int prev = 0;
        double t = 0, dist = 0;
        int k = 1;
        for (int i : seq) {
            Stop s = p.stops().get(i - 1);
            double leg = d[prev][i];
            dist += leg;
            t += leg / p.averageSpeedKmh() * 60;
            if (s.timeWindowStart() != null && t < s.timeWindowStart()) t = s.timeWindowStart();
            boolean ok = s.timeWindowEnd() == null || t <= s.timeWindowEnd();
            stops.add(new RouteStop(s.id(), s.label(), k++, s.latitude(), s.longitude(), EmissionCalculator.round(leg), Math.round(t), ok));
            t += s.serviceMinutes();
            prev = i;
        }
        dist += d[prev][0];
        t += d[prev][0] / p.averageSpeedKmh() * 60;
        double fuel = EmissionCalculator.fuelLiters(dist, v.consumptionPer100km());
        return new VehicleRoute(v.id(), v.label(), stops, EmissionCalculator.round(dist), Math.round(t), load(p, seq), v.capacityKg(), fuel,
                EmissionCalculator.co2Kg(fuel, v.co2FactorKgPerLiter(), null));
    }

    public static Solution summarize(String engine, List<VehicleRoute> routes, List<String> unassigned, double originalKm, double originalCo2,
                                     List<String> notes) {
        double km = routes.stream().mapToDouble(VehicleRoute::distanceKm).sum();
        double min = routes.stream().mapToDouble(VehicleRoute::durationMinutes).max().orElse(0);
        double fuel = routes.stream().mapToDouble(VehicleRoute::fuelLiters).sum();
        double co2 = routes.stream().mapToDouble(VehicleRoute::co2Kg).sum();
        int violations = (int) routes.stream().flatMap(r -> r.stops().stream()).filter(s -> !s.timeWindowRespected()).count();
        return new Solution(engine, routes, unassigned, EmissionCalculator.round(originalKm), EmissionCalculator.round(km), min,
                EmissionCalculator.round(fuel), EmissionCalculator.round(co2), EmissionCalculator.round(originalCo2), violations, notes);
    }
}

package com.dnd.smartflow.ai.routing;

import com.dnd.smartflow.ai.AiServiceClient;
import com.dnd.smartflow.ai.routing.RoutingModels.*;
import com.dnd.smartflow.catalog.domain.Customer;
import com.dnd.smartflow.catalog.domain.CustomerRepository;
import com.dnd.smartflow.common.crud.ReferenceGenerator;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.distribution.domain.*;
import com.dnd.smartflow.fleet.domain.*;
import com.dnd.smartflow.fleet.service.EmissionCalculator;
import com.dnd.smartflow.inventory.domain.Warehouse;
import com.dnd.smartflow.inventory.domain.WarehouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RouteOptimizationService {
    private static final LocalTime DEPOT_OPENING = LocalTime.of(7, 0);
    private final DeliveryRepository deliveries;
    private final SalesOrderRepository orders;
    private final CustomerRepository customers;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final WarehouseRepository warehouses;
    private final RoutePlanRepository plans;
    private final ReferenceGenerator references;
    private final AiServiceClient ai;
    private final ObjectMapper mapper;

    public record OptimizeRequest(LocalDate planDate, UUID depotWarehouseId, List<UUID> deliveryIds, List<UUID> vehicleIds, boolean save) {
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> optimize(OptimizeRequest req) {
        Warehouse depot = warehouses.findById(req.depotWarehouseId()).orElseThrow(() -> ApiException.notFound("Depot warehouse"));
        if (depot.getLatitude() == null) throw ApiException.badRequest("DEPOT_NOT_GEOLOCATED", "The depot has no GPS coordinates");
        LocalDate date = req.planDate() == null ? LocalDate.now() : req.planDate();
        List<Delivery> candidates = req.deliveryIds() != null && !req.deliveryIds().isEmpty()
                ? deliveries.findAllById(req.deliveryIds())
                : deliveries.findAll((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), depot.getId()), cb.equal(r.get("plannedDate"), date),
                cb.equal(r.get("status"), "PLANNED")));
        if (candidates.isEmpty()) throw ApiException.badRequest("NO_DELIVERIES", "No planned deliveries for this depot and date");
        List<Vehicle> fleet = req.vehicleIds() != null && !req.vehicleIds().isEmpty()
                ? vehicles.findAllById(req.vehicleIds())
                : vehicles.findAll((r, q, cb) -> cb.and(cb.equal(r.get("status"), "AVAILABLE"), cb.equal(r.get("homeSiteId"), depot.getSiteId())));
        if (fleet.isEmpty()) fleet = vehicles.findAll((r, q, cb) -> cb.equal(r.get("status"), "AVAILABLE"));
        if (fleet.isEmpty()) throw ApiException.badRequest("NO_VEHICLES", "No available vehicle");

        Map<UUID, Customer> cust = new HashMap<>();
        customers.findAllById(candidates.stream().map(Delivery::getCustomerId).toList()).forEach(c -> cust.put(c.getId(), c));
        Map<UUID, SalesOrder> ord = new HashMap<>();
        orders.findAllById(candidates.stream().map(Delivery::getOrderId).filter(Objects::nonNull).toList()).forEach(o -> ord.put(o.getId(), o));
        candidates.sort(Comparator.comparing(Delivery::getDeliveryNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        List<Stop> stops = new ArrayList<>();
        for (Delivery d : candidates) {
            Customer c = cust.get(d.getCustomerId());
            if (c == null || c.getLatitude() == null) continue;
            SalesOrder o = d.getOrderId() == null ? null : ord.get(d.getOrderId());
            double weight = o != null && o.getTotalWeightKg() != null && o.getTotalWeightKg() > 0 ? o.getTotalWeightKg() : 400;
            int priority = o == null ? 1 : switch (Objects.toString(o.getPriority(), "NORMAL")) {
                case "URGENT" -> 3;
                case "HIGH" -> 2;
                default -> 1;
            };
            stops.add(new Stop(d.getId().toString(), c.getName() + " (" + c.getCity() + ")", c.getLatitude(), c.getLongitude(), weight, priority,
                    minutes(d.getTimeWindowStart()), minutes(d.getTimeWindowEnd()), 20));
        }
        List<VehicleSpec> specs = fleet.stream().map(v -> new VehicleSpec(v.getId().toString(), v.getPlateNumber() + " · " + v.getType(),
                Optional.ofNullable(v.getCapacityKg()).orElse(3500.0), Optional.ofNullable(v.getFuelConsumptionPer100km()).orElse(28.0),
                Optional.ofNullable(v.getCo2FactorKgPerLiter()).orElse(EmissionCalculator.defaultFactor(v.getFuelType())))).toList();
        Problem problem = new Problem(new Location(depot.getLatitude(), depot.getLongitude()), stops, specs, 55.0, 1.3, 11 * 60);

        Solution solution = ai.post("/routing/optimize", problem, Solution.class).orElseGet(() -> new HeuristicRouteSolver().solve(problem));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planDate", date);
        out.put("depot", Map.of("id", depot.getId(), "name", depot.getName(), "latitude", depot.getLatitude(), "longitude", depot.getLongitude()));
        out.put("solution", solution);
        out.put("dataNature", "Distances are great-circle estimates × road factor 1.3; fuel and CO2 are model estimates, not measurements.");
        if (req.save()) {
            RoutePlan plan = new RoutePlan();
            plan.setCode(references.next("RP"));
            plan.setPlanDate(date);
            plan.setDepotWarehouseId(depot.getId());
            plan.setStatus("OPTIMIZED");
            plan.setAlgorithm(solution.engine());
            plan.setOriginalDistanceKm(solution.originalDistanceKm());
            plan.setOptimizedDistanceKm(solution.optimizedDistanceKm());
            plan.setEstimatedDurationMin(solution.estimatedDurationMinutes());
            plan.setEstimatedFuelLiters(solution.estimatedFuelLiters());
            plan.setEstimatedCo2Kg(solution.estimatedCo2Kg());
            plan.setVehiclesUsed(solution.routes().size());
            plan.setStopsCount(stops.size());
            plan.setResult(mapper.convertValue(out, Map.class));
            plan = plans.save(plan);
            out.put("planId", plan.getId());
            out.put("planCode", plan.getCode());
        }
        return out;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public RoutePlan dispatch(UUID planId) {
        RoutePlan plan = plans.findById(planId).orElseThrow(() -> ApiException.notFound("Route plan"));
        if (!"OPTIMIZED".equals(plan.getStatus())) throw ApiException.conflict("INVALID_STATE", "Only optimized plans can be dispatched");
        Solution s = mapper.convertValue(plan.getResult().get("solution"), Solution.class);
        List<Driver> available = new ArrayList<>(drivers.findAll((r, q, cb) -> cb.equal(r.get("status"), "AVAILABLE")));
        for (VehicleRoute route : s.routes()) {
            UUID vehicleId = UUID.fromString(route.vehicleId());
            Driver driver = available.isEmpty() ? null : available.remove(0);
            double cumulative = 0;
            for (RouteStop rs : route.stops()) {
                cumulative += rs.distanceFromPreviousKm();
                Delivery d = deliveries.findById(UUID.fromString(rs.stopId())).orElse(null);
                if (d == null) continue;
                d.setVehicleId(vehicleId);
                d.setDriverId(driver == null ? null : driver.getId());
                d.setRoutePlanId(plan.getId());
                d.setStopSequence(rs.sequence());
                d.setDistanceKm(EmissionCalculator.round(rs.distanceFromPreviousKm()));
                deliveries.save(d);
            }
            if (driver != null) {
                driver.setStatus("ON_DUTY");
                drivers.save(driver);
            }
        }
        plan.setStatus("DISPATCHED");
        return plans.save(plan);
    }

    private static Integer minutes(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        LocalTime t = LocalTime.parse(hhmm);
        return (int) java.time.Duration.between(DEPOT_OPENING, t).toMinutes();
    }
}

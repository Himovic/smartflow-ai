package com.dnd.smartflow.distribution.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.distribution.domain.*;
import com.dnd.smartflow.fleet.domain.Vehicle;
import com.dnd.smartflow.fleet.domain.VehicleRepository;
import com.dnd.smartflow.fleet.service.EmissionCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "Orders & deliveries")
public class DeliveryController extends CrudController<Delivery> {
    private final VehicleRepository vehicles;
    private final SalesOrderRepository orders;

    public DeliveryController(CrudSupport support, DeliveryRepository repository, VehicleRepository vehicles, SalesOrderRepository orders) {
        super(support, repository, Delivery.class, "distribution");
        this.vehicles = vehicles;
        this.orders = orders;
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "plannedDate").and(Sort.by("stopSequence"));
    }

    @Override
    protected void beforeCreate(Delivery d) {
        d.setDeliveryNumber(support.getReferences().next("DLV"));
        d.setStatus("PLANNED");
    }

    @PostMapping("/{id}/depart")
    public Delivery depart(@PathVariable UUID id) {
        support.getPermissions().require("distribution", "write");
        return support.getTx().execute(s -> {
            Delivery d = load(id);
            if (!Set.of("PLANNED", "DELAYED").contains(d.getStatus())) throw ApiException.conflict("INVALID_STATE", "Delivery cannot depart from " + d.getStatus());
            if (d.getVehicleId() == null) throw ApiException.badRequest("VEHICLE_REQUIRED", "Assign a vehicle before departure");
            d.setStatus("IN_TRANSIT");
            d.setDepartedAt(Instant.now());
            vehicles.findById(d.getVehicleId()).ifPresent(v -> {
                v.setStatus("ON_ROUTE");
                vehicles.save(v);
            });
            return repository.save(d);
        });
    }

    @PostMapping("/{id}/deliver")
    @Operation(summary = "Confirm delivery: computes delay against the time window, fuel and estimated CO2")
    public Delivery deliver(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        support.getPermissions().require("distribution", "write");
        return support.getTx().execute(s -> {
            Delivery d = load(id);
            if (!Set.of("IN_TRANSIT", "DELAYED").contains(d.getStatus())) throw ApiException.conflict("INVALID_STATE", "Delivery is not in transit");
            Instant now = Instant.now();
            d.setDeliveredAt(now);
            if (body != null && body.get("distanceKm") instanceof Number n) d.setDistanceKm(n.doubleValue());
            if (body != null && body.get("proofNote") != null) d.setProofNote(String.valueOf(body.get("proofNote")));
            d.setDelayMinutes(delay(d, now));
            d.setStatus("DELIVERED");
            applyEmissions(d);
            if (d.getOrderId() != null) orders.findById(d.getOrderId()).ifPresent(o -> {
                o.setStatus("DELIVERED");
                orders.save(o);
            });
            releaseVehicle(d);
            return repository.save(d);
        });
    }

    @PostMapping("/{id}/fail")
    public Delivery fail(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        support.getPermissions().require("distribution", "write");
        return support.getTx().execute(s -> {
            Delivery d = load(id);
            if (!Set.of("IN_TRANSIT", "DELAYED", "PLANNED").contains(d.getStatus())) throw ApiException.conflict("INVALID_STATE", "Delivery already closed");
            d.setStatus("FAILED");
            d.setProofNote(body == null ? null : String.valueOf(body.getOrDefault("reason", "")));
            releaseVehicle(d);
            return repository.save(d);
        });
    }

    private void releaseVehicle(Delivery d) {
        if (d.getVehicleId() == null) return;
        long stillOnRoute = repository.count((r, q, cb) -> cb.and(cb.equal(r.get("vehicleId"), d.getVehicleId()),
                cb.equal(r.get("status"), "IN_TRANSIT"), cb.notEqual(r.get("id"), d.getId())));
        if (stillOnRoute == 0) vehicles.findById(d.getVehicleId()).ifPresent(v -> {
            v.setStatus("AVAILABLE");
            vehicles.save(v);
        });
    }

    private void applyEmissions(Delivery d) {
        if (d.getDistanceKm() == null || d.getVehicleId() == null) return;
        Vehicle v = vehicles.findById(d.getVehicleId()).orElse(null);
        if (v == null) return;
        double liters = EmissionCalculator.fuelLiters(d.getDistanceKm(), v.getFuelConsumptionPer100km());
        d.setFuelLiters(liters);
        d.setCo2Kg(EmissionCalculator.co2Kg(liters, v.getCo2FactorKgPerLiter(), v.getFuelType()));
    }

    static int delay(Delivery d, Instant deliveredAt) {
        if (d.getPlannedDate() == null) return 0;
        LocalTime end = d.getTimeWindowEnd() == null ? LocalTime.of(18, 0) : LocalTime.parse(d.getTimeWindowEnd());
        Instant deadline = d.getPlannedDate().atTime(end).atZone(ZoneId.of("Africa/Algiers")).toInstant();
        return (int) Math.max(0, Duration.between(deadline, deliveredAt).toMinutes());
    }
}

package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.organization.domain.Employee;
import com.dnd.smartflow.organization.domain.EmployeeRepository;
import com.dnd.smartflow.qhse.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/training-matrix")
@RequiredArgsConstructor
@Tag(name = "Training & certifications")
public class TrainingMatrixController {
    private final EmployeeRepository employees;
    private final TrainingRepository trainings;
    private final TrainingRecordRepository records;
    private final PermissionService perm;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Qualification matrix: employees × mandatory trainings (VALID, EXPIRING, EXPIRED, MISSING)")
    public Map<String, Object> matrix(@RequestParam(required = false) UUID siteId, @RequestParam(required = false) UUID departmentId) {
        perm.require("training", "read");
        List<Training> mandatory = trainings.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getMandatory()))
                .sorted(Comparator.comparing(Training::getCode)).toList();
        List<Employee> staff = employees.findAll().stream()
                .filter(e -> siteId == null || siteId.equals(e.getSiteId()))
                .filter(e -> departmentId == null || departmentId.equals(e.getDepartmentId()))
                .filter(e -> !"INACTIVE".equals(e.getStatus()))
                .sorted(Comparator.comparing(Employee::getLastName)).limit(300).toList();
        Map<String, TrainingRecord> latest = new HashMap<>();
        for (TrainingRecord r : records.findAll()) {
            if (!"COMPLETED".equals(r.getStatus())) continue;
            String k = r.getEmployeeId() + "|" + r.getTrainingId();
            TrainingRecord cur = latest.get(k);
            if (cur == null || (r.getSessionDate() != null && cur.getSessionDate() != null && r.getSessionDate().isAfter(cur.getSessionDate()))) latest.put(k, r);
        }
        LocalDate today = LocalDate.now();
        int valid = 0, total = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee e : staff) {
            Map<String, Object> cells = new LinkedHashMap<>();
            for (Training t : mandatory) {
                TrainingRecord r = latest.get(e.getId() + "|" + t.getId());
                String status;
                if (r == null) status = "MISSING";
                else if (r.getExpiryDate() == null || r.getExpiryDate().isAfter(today.plusDays(60))) status = "VALID";
                else if (r.getExpiryDate().isAfter(today)) status = "EXPIRING";
                else status = "EXPIRED";
                total++;
                if (!status.equals("MISSING") && !status.equals("EXPIRED")) valid++;
                cells.put(t.getId().toString(), Map.of("status", status, "expiryDate", r == null || r.getExpiryDate() == null ? "" : r.getExpiryDate().toString()));
            }
            rows.add(Map.of("employeeId", e.getId(), "name", e.getFirstName() + " " + e.getLastName(),
                    "jobTitle", Objects.toString(e.getJobTitle(), ""), "cells", cells));
        }
        return Map.of("trainings", mandatory.stream().map(t -> Map.of("id", t.getId(), "code", t.getCode(), "title", t.getTitle())).toList(),
                "rows", rows, "complianceRate", total == 0 ? 100.0 : Math.round(valid * 1000.0 / total) / 10.0);
    }
}

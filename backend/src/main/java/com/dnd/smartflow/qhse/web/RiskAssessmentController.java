package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.RiskAssessment;
import com.dnd.smartflow.qhse.domain.RiskAssessmentRepository;
import com.dnd.smartflow.qhse.service.RiskMatrix;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/risk-assessments")
@Tag(name = "Risk management", description = "Hazard identification, 5x5 risk matrix, residual risk and job safety analysis")
public class RiskAssessmentController extends CrudController<RiskAssessment> {

    public RiskAssessmentController(CrudSupport support, RiskAssessmentRepository repository) {
        super(support, repository, RiskAssessment.class, "risk");
    }

    @Override
    protected String referencePrefix() {
        return "RISK";
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "riskScore");
    }

    @Override
    protected void beforeSave(RiskAssessment r) {
        int score = RiskMatrix.score(r.getProbability(), r.getSeverity());
        r.setRiskScore(score);
        r.setRiskLevel(RiskMatrix.level(score));
        if (r.getResidualProbability() != null && r.getResidualSeverity() != null) {
            int residual = RiskMatrix.score(r.getResidualProbability(), r.getResidualSeverity());
            if (residual > score) throw new IllegalArgumentException("Residual risk cannot exceed the initial risk");
            r.setResidualScore(residual);
            r.setResidualLevel(RiskMatrix.level(residual));
        } else {
            r.setResidualScore(null);
            r.setResidualLevel(null);
        }
        if (r.getStatus() == null) r.setStatus("DRAFT");
        if (r.getAssessmentType() == null) r.setAssessmentType("GENERAL");
    }

    @GetMapping("/matrix")
    @Operation(summary = "5x5 heat-map counts of active risks (initial or residual)")
    public Map<String, Object> matrix(@RequestParam(defaultValue = "false") boolean residual, @RequestParam(required = false) UUID siteId) {
        support.getPermissions().require("risk", "read");
        Map<String, String> params = new HashMap<>();
        params.put("status", "ACTIVE,UNDER_REVIEW,DRAFT");
        if (siteId != null) params.put("siteId", siteId.toString());
        int[][] cells = new int[5][5];
        List<Map<String, Object>> top = new ArrayList<>();
        for (RiskAssessment r : repository.findAll(spec(params))) {
            Integer p = residual ? r.getResidualProbability() : r.getProbability();
            Integer s = residual ? r.getResidualSeverity() : r.getSeverity();
            if (p == null || s == null) continue;
            cells[p - 1][s - 1]++;
        }
        repository.findAll(spec(params), org.springframework.data.domain.PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "riskScore")))
                .forEach(r -> top.add(Map.of("id", r.getId(), "reference", r.getReference(), "title", r.getTitle(),
                        "score", r.getRiskScore(), "level", r.getRiskLevel(),
                        "residualScore", r.getResidualScore() == null ? 0 : r.getResidualScore())));
        List<Map<String, Object>> data = new ArrayList<>();
        for (int p = 1; p <= 5; p++)
            for (int s = 1; s <= 5; s++)
                data.add(Map.of("probability", p, "severity", s, "count", cells[p - 1][s - 1], "level", RiskMatrix.level(p * s)));
        return Map.of("cells", data, "top", top, "residual", residual);
    }
}

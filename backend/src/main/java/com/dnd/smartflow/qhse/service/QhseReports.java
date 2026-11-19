package com.dnd.smartflow.qhse.service;

import com.dnd.smartflow.reporting.ReportExporter;
import com.dnd.smartflow.reporting.ReportExporter.Column;
import com.dnd.smartflow.reporting.ReportExporter.Section;
import com.dnd.smartflow.workflow.domain.WorkflowEvent;
import com.dnd.smartflow.workflow.domain.WorkflowEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.*;

/** Builds printable PDF case reports (NC, incident, inspection, recall…) with their workflow history. */
@Component
@RequiredArgsConstructor
public class QhseReports {
    private final ReportExporter exporter;
    private final WorkflowEventRepository events;

    public Section history(String entityType, UUID id) {
        List<WorkflowEvent> list = events.findAll((r, q, cb) -> cb.and(cb.equal(r.get("entityType"), entityType), cb.equal(r.get("entityId"), id)),
                Sort.by("occurredAt"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WorkflowEvent e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", e.getOccurredAt());
            m.put("step", e.getTransition());
            m.put("from", e.getFromStatus());
            m.put("to", e.getToStatus());
            m.put("by", e.getActorName());
            m.put("comment", e.getComment());
            rows.add(m);
        }
        return Section.table("Workflow history", List.of(new Column("at", "Date"), new Column("step", "Step"), new Column("from", "From"),
                new Column("to", "To"), new Column("by", "By"), new Column("comment", "Comment")), rows);
    }

    public ResponseEntity<byte[]> pdf(String fileName, String title, List<Section> sections) {
        byte[] data = exporter.sectionsPdf(title, sections);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(data);
    }
}

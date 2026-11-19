package com.dnd.smartflow.workflow;

import com.dnd.smartflow.workflow.domain.WorkflowDefinition;
import com.dnd.smartflow.workflow.domain.WorkflowDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

/** Installs the default workflow definitions ({@code config/workflows.json}) into the current tenant. */
@Component
@RequiredArgsConstructor
public class WorkflowInstaller {
    private final WorkflowDefinitionRepository repository;
    private final ObjectMapper mapper;

    @Transactional
    @SuppressWarnings("unchecked")
    public int installDefaults() {
        Set<String> existing = new HashSet<>();
        repository.findAll().forEach(d -> existing.add(d.getWorkflowKey()));
        try (InputStream in = new ClassPathResource("config/workflows.json").getInputStream()) {
            List<Map<String, Object>> defs = mapper.readValue(in, new TypeReference<>() {});
            int n = 0;
            for (Map<String, Object> d : defs) {
                if (existing.contains((String) d.get("workflowKey"))) continue;
                WorkflowDefinition w = new WorkflowDefinition();
                w.setWorkflowKey((String) d.get("workflowKey"));
                w.setName((String) d.get("name"));
                w.setEntityType((String) d.get("entityType"));
                w.setDescription((String) d.get("description"));
                w.setInitialState((String) d.get("initialState"));
                w.setStates((List<Map<String, Object>>) d.get("states"));
                w.setTransitions((List<Map<String, Object>>) d.get("transitions"));
                w.setActive(true);
                repository.save(w);
                n++;
            }
            return n;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot install default workflows", e);
        }
    }
}

package com.dnd.smartflow.integration.sync;

import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.integration.domain.IntegrationConnector;
import com.dnd.smartflow.integration.domain.IntegrationConnectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Runs enabled connectors according to their synchronization frequency. */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationScheduler {
    private final IntegrationConnectorRepository connectors;
    private final SyncEngine engine;

    @Value("${smartflow.integrations.scheduler-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${smartflow.integrations.poll-interval:60000}", initialDelay = 60000)
    public void tick() {
        if (!enabled) return;
        List<IntegrationConnector> due = TenantContext.callAs(TenantContext.ROOT, () -> connectors.findAll().stream()
                .filter(IntegrationConnector::isEnabled)
                .filter(c -> c.getSyncFrequencyMinutes() != null && c.getSyncFrequencyMinutes() > 0)
                .filter(c -> c.getLastSyncAt() == null || c.getLastSyncAt().plus(Duration.ofMinutes(c.getSyncFrequencyMinutes())).isBefore(Instant.now()))
                .toList());
        for (IntegrationConnector c : due) {
            try {
                TenantContext.runAs(c.getTenantId(), () -> engine.run(c.getId(), "SCHEDULED"));
            } catch (Exception e) {
                log.warn("Scheduled sync of {} failed: {}", c.getName(), e.getMessage());
            }
        }
    }
}

package com.dnd.smartflow.integration.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Extension point for external systems. A provider reads changed records of a canonical entity type
 * (PRODUCT, CUSTOMER, SUPPLIER, STOCK, EQUIPMENT, WORK_ORDER, VEHICLE_POSITION, SENSOR_READING) since an
 * incremental cursor and returns them in the platform's canonical JSON shape.
 */
public interface ConnectorProvider {

    String code();

    String label();

    /** ERP, CMMS, GPS, IOT, LIMS, MESSAGING. */
    String category();

    boolean simulated();

    List<String> supportedEntities();

    /** Configuration keys the administrator must provide (shown in the UI). */
    List<String> configKeys();

    TestResult test(ConnectorContext ctx);

    FetchResult fetch(ConnectorContext ctx, String entityType, String cursor);

    record ConnectorContext(String baseUrl, String authType, Map<String, String> credentials, Map<String, Object> config) {
    }

    record ExternalRecord(String externalId, Instant updatedAt, Map<String, Object> payload) {
    }

    record FetchResult(List<ExternalRecord> records, String nextCursor) {
    }

    record TestResult(boolean ok, String message, long latencyMs) {
    }
}

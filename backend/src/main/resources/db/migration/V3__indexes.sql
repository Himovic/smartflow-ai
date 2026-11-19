-- Query-path indexes for dashboards, traceability and workflows
create index ix_nc_detected on non_conformities(tenant_id, detected_at);
create index ix_nc_status on non_conformities(tenant_id, status);
create index ix_incidents_occurred on incidents(tenant_id, occurred_at);
create index ix_inspections_completed on inspections(tenant_id, completed_at);
create index ix_orders_date on sales_orders(tenant_id, order_date);
create index ix_deliveries_planned on deliveries(tenant_id, planned_date, status);
create index ix_deliveries_delivered on deliveries(tenant_id, delivered_at);
create index ix_movements_occurred on stock_movements(tenant_id, occurred_at);
create index ix_readings_type_time on environmental_readings(tenant_id, reading_type, recorded_at);
create index ix_workflow_events_entity on workflow_events(entity_type, entity_id, occurred_at);
create index ix_comments_entity on comments(entity_type, entity_id);
create index ix_attachments_entity on attachments(entity_type, entity_id);
create index ix_notifications_target on notifications(tenant_id, user_id, target_role, read_at);
create index ix_positions_vehicle_time on vehicle_positions(vehicle_id, recorded_at);
create unique index ux_integration_id_map on integration_id_map(connector_id, entity_type, external_id);
create index ix_sync_records_status on sync_records(connector_id, status);

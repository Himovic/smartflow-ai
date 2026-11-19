-- SmartFlow AI + QHSE — business domains (generated from the domain specification)
create table sites (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    type varchar(50) not null,
    address varchar(255),
    city varchar(100),
    region varchar(100),
    country varchar(100),
    latitude double precision,
    longitude double precision,
    manager_name varchar(255),
    phone varchar(50),
    active boolean,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_sites_tenant on sites(tenant_id);
create unique index ux_sites_code on sites(tenant_id, code);

create table departments (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    site_id uuid not null,
    code varchar(50) not null,
    name varchar(255) not null,
    domain varchar(50),
    manager_name varchar(255),
    headcount integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_departments_tenant on departments(tenant_id);
create index ix_departments_site_id on departments(site_id);
create unique index ux_departments_code on departments(tenant_id, code);

create table production_lines (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    site_id uuid not null,
    department_id uuid,
    code varchar(50) not null,
    name varchar(255) not null,
    product_family varchar(100),
    capacity_per_hour integer,
    status varchar(50),
    oee double precision,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_production_lines_tenant on production_lines(tenant_id);
create index ix_production_lines_site_id on production_lines(site_id);
create index ix_production_lines_department_id on production_lines(department_id);
create unique index ux_production_lines_code on production_lines(tenant_id, code);

create table employees (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    matricule varchar(50) not null,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    job_title varchar(255),
    site_id uuid,
    department_id uuid,
    email varchar(255),
    phone varchar(50),
    hire_date date,
    status varchar(50),
    user_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_employees_tenant on employees(tenant_id);
create index ix_employees_site_id on employees(site_id);
create index ix_employees_department_id on employees(department_id);
create index ix_employees_user_id on employees(user_id);
create unique index ux_employees_matricule on employees(tenant_id, matricule);

create table equipment (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    category varchar(100),
    site_id uuid,
    production_line_id uuid,
    manufacturer varchar(100),
    model varchar(100),
    serial_number varchar(100),
    commissioning_date date,
    status varchar(50),
    criticality varchar(20),
    last_maintenance_date date,
    next_maintenance_date date,
    running_hours integer,
    failure_count integer,
    mtbf_hours double precision,
    external_ref varchar(100),
    custom_fields jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_equipment_tenant on equipment(tenant_id);
create index ix_equipment_site_id on equipment(site_id);
create index ix_equipment_production_line_id on equipment(production_line_id);
create unique index ux_equipment_code on equipment(tenant_id, code);

create table maintenance_work_orders (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    equipment_id uuid not null,
    type varchar(50) not null,
    status varchar(50),
    priority varchar(20),
    description text,
    planned_date date,
    completed_date date,
    downtime_hours double precision,
    cost numeric(16,4),
    technician varchar(255),
    source varchar(50),
    external_ref varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_maintenance_work_orders_tenant on maintenance_work_orders(tenant_id);
create index ix_maintenance_work_orders_equipment_id on maintenance_work_orders(equipment_id);
create unique index ux_maintenance_work_orders_reference on maintenance_work_orders(tenant_id, reference);

create table suppliers (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    category varchar(100),
    country varchar(100),
    city varchar(100),
    contact_name varchar(255),
    email varchar(255),
    phone varchar(50),
    status varchar(50),
    quality_score double precision,
    on_time_rate double precision,
    certifications varchar(500),
    last_audit_date date,
    external_ref varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_suppliers_tenant on suppliers(tenant_id);
create unique index ux_suppliers_code on suppliers(tenant_id, code);

create table customers (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    type varchar(50),
    address varchar(255),
    city varchar(100),
    region varchar(100),
    latitude double precision,
    longitude double precision,
    contact_name varchar(255),
    phone varchar(50),
    email varchar(255),
    credit_limit numeric(16,4),
    active boolean,
    external_ref varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_customers_tenant on customers(tenant_id);
create unique index ux_customers_code on customers(tenant_id, code);

create table products (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    sku varchar(50) not null,
    name varchar(255) not null,
    category varchar(100),
    brand varchar(100),
    unit varchar(20),
    unit_weight_kg double precision,
    shelf_life_days integer,
    unit_price numeric(16,4),
    co2_per_unit_kg double precision,
    min_stock integer,
    reorder_point integer,
    barcode varchar(50),
    active boolean,
    external_ref varchar(100),
    custom_fields jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_products_tenant on products(tenant_id);
create unique index ux_products_sku on products(tenant_id, sku);

create table warehouses (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    site_id uuid,
    type varchar(50),
    city varchar(100),
    latitude double precision,
    longitude double precision,
    capacity_pallets integer,
    temperature_controlled boolean,
    min_temp double precision,
    max_temp double precision,
    manager_name varchar(255),
    external_ref varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_warehouses_tenant on warehouses(tenant_id);
create index ix_warehouses_site_id on warehouses(site_id);
create unique index ux_warehouses_code on warehouses(tenant_id, code);

create table batches (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    batch_number varchar(50) not null,
    product_id uuid not null,
    site_id uuid,
    production_line_id uuid,
    supplier_id uuid,
    production_date date,
    expiry_date date,
    quantity_produced integer,
    status varchar(30),
    quality_status varchar(30),
    raw_material_lots varchar(500),
    external_ref varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_batches_tenant on batches(tenant_id);
create index ix_batches_product_id on batches(product_id);
create index ix_batches_site_id on batches(site_id);
create index ix_batches_production_line_id on batches(production_line_id);
create index ix_batches_supplier_id on batches(supplier_id);
create unique index ux_batches_batch_number on batches(tenant_id, batch_number);

create table stock_levels (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    warehouse_id uuid not null,
    product_id uuid not null,
    batch_id uuid,
    quantity integer not null,
    reserved_quantity integer,
    location_code varchar(50),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_stock_levels_tenant on stock_levels(tenant_id);
create index ix_stock_levels_warehouse_id on stock_levels(warehouse_id);
create index ix_stock_levels_product_id on stock_levels(product_id);
create index ix_stock_levels_batch_id on stock_levels(batch_id);

create table stock_movements (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    type varchar(30) not null,
    product_id uuid not null,
    batch_id uuid,
    from_warehouse_id uuid,
    to_warehouse_id uuid,
    quantity integer not null,
    reference_type varchar(50),
    reference_id uuid,
    occurred_at timestamptz,
    notes varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_stock_movements_tenant on stock_movements(tenant_id);
create index ix_stock_movements_product_id on stock_movements(product_id);
create index ix_stock_movements_batch_id on stock_movements(batch_id);
create index ix_stock_movements_from_warehouse_id on stock_movements(from_warehouse_id);
create index ix_stock_movements_to_warehouse_id on stock_movements(to_warehouse_id);
create index ix_stock_movements_reference_id on stock_movements(reference_id);

create table sales_orders (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    order_number varchar(50),
    customer_id uuid not null,
    warehouse_id uuid,
    order_date date,
    requested_date date,
    status varchar(30),
    priority varchar(20),
    total_amount numeric(16,4),
    total_weight_kg double precision,
    notes varchar(500),
    external_ref varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_sales_orders_tenant on sales_orders(tenant_id);
create index ix_sales_orders_customer_id on sales_orders(customer_id);
create index ix_sales_orders_warehouse_id on sales_orders(warehouse_id);
create unique index ux_sales_orders_order_number on sales_orders(tenant_id, order_number);

create table sales_order_lines (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    order_id uuid not null,
    product_id uuid not null,
    batch_id uuid,
    quantity integer not null,
    unit_price numeric(16,4),
    line_total numeric(16,4),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_sales_order_lines_tenant on sales_order_lines(tenant_id);
create index ix_sales_order_lines_order_id on sales_order_lines(order_id);
create index ix_sales_order_lines_product_id on sales_order_lines(product_id);
create index ix_sales_order_lines_batch_id on sales_order_lines(batch_id);

create table deliveries (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    delivery_number varchar(50),
    order_id uuid,
    customer_id uuid not null,
    warehouse_id uuid,
    vehicle_id uuid,
    driver_id uuid,
    route_plan_id uuid,
    planned_date date,
    time_window_start varchar(10),
    time_window_end varchar(10),
    status varchar(30),
    departed_at timestamptz,
    delivered_at timestamptz,
    distance_km double precision,
    fuel_liters double precision,
    co2_kg double precision,
    stop_sequence integer,
    delay_minutes integer,
    proof_note varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_deliveries_tenant on deliveries(tenant_id);
create index ix_deliveries_order_id on deliveries(order_id);
create index ix_deliveries_customer_id on deliveries(customer_id);
create index ix_deliveries_warehouse_id on deliveries(warehouse_id);
create index ix_deliveries_vehicle_id on deliveries(vehicle_id);
create index ix_deliveries_driver_id on deliveries(driver_id);
create index ix_deliveries_route_plan_id on deliveries(route_plan_id);
create unique index ux_deliveries_delivery_number on deliveries(tenant_id, delivery_number);

create table vehicles (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    plate_number varchar(30) not null,
    code varchar(50),
    type varchar(50),
    brand varchar(100),
    model varchar(100),
    capacity_kg double precision,
    capacity_pallets integer,
    fuel_type varchar(30),
    fuel_consumption_per100km double precision,
    co2_factor_kg_per_liter double precision,
    status varchar(30),
    home_site_id uuid,
    current_latitude double precision,
    current_longitude double precision,
    last_gps_at timestamptz,
    odometer_km integer,
    next_service_km integer,
    last_hygiene_check date,
    refrigerated boolean,
    external_ref varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_vehicles_tenant on vehicles(tenant_id);
create index ix_vehicles_home_site_id on vehicles(home_site_id);
create unique index ux_vehicles_plate_number on vehicles(tenant_id, plate_number);
create unique index ux_vehicles_code on vehicles(tenant_id, code);

create table drivers (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    employee_id uuid,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    license_number varchar(50),
    license_category varchar(20),
    license_expiry date,
    phone varchar(50),
    status varchar(30),
    safety_score double precision,
    home_site_id uuid,
    harsh_braking_events integer,
    speeding_events integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_drivers_tenant on drivers(tenant_id);
create index ix_drivers_employee_id on drivers(employee_id);
create index ix_drivers_home_site_id on drivers(home_site_id);

create table fuel_logs (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    vehicle_id uuid not null,
    driver_id uuid,
    log_date date not null,
    liters double precision not null,
    cost numeric(16,4),
    odometer_km integer,
    station varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_fuel_logs_tenant on fuel_logs(tenant_id);
create index ix_fuel_logs_vehicle_id on fuel_logs(vehicle_id);
create index ix_fuel_logs_driver_id on fuel_logs(driver_id);

create table vehicle_positions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    vehicle_id uuid not null,
    latitude double precision not null,
    longitude double precision not null,
    speed_kmh double precision,
    recorded_at timestamptz not null,
    source varchar(30),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_vehicle_positions_tenant on vehicle_positions(tenant_id);
create index ix_vehicle_positions_vehicle_id on vehicle_positions(vehicle_id);

create table route_plans (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50),
    plan_date date not null,
    depot_warehouse_id uuid not null,
    status varchar(30),
    algorithm varchar(100),
    original_distance_km double precision,
    optimized_distance_km double precision,
    estimated_duration_min double precision,
    estimated_fuel_liters double precision,
    estimated_co2_kg double precision,
    vehicles_used integer,
    stops_count integer,
    result jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_route_plans_tenant on route_plans(tenant_id);
create index ix_route_plans_depot_warehouse_id on route_plans(depot_warehouse_id);
create unique index ux_route_plans_code on route_plans(tenant_id, code);

create table non_conformities (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    title varchar(255) not null,
    description text,
    type varchar(50) not null,
    category varchar(100),
    severity varchar(20),
    status varchar(50),
    source varchar(50),
    site_id uuid,
    department_id uuid,
    production_line_id uuid,
    product_id uuid,
    batch_id uuid,
    supplier_id uuid,
    inspection_id uuid,
    audit_id uuid,
    detected_at timestamptz,
    reported_by_name varchar(255),
    reported_by_user_id uuid,
    assignee_user_id uuid,
    assignee_name varchar(255),
    due_date date,
    root_cause text,
    root_cause_method varchar(30),
    five_whys jsonb,
    immediate_action text,
    quantity_affected integer,
    cost_estimate numeric(16,4),
    closed_at timestamptz,
    custom_fields jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_non_conformities_tenant on non_conformities(tenant_id);
create index ix_non_conformities_site_id on non_conformities(site_id);
create index ix_non_conformities_department_id on non_conformities(department_id);
create index ix_non_conformities_production_line_id on non_conformities(production_line_id);
create index ix_non_conformities_product_id on non_conformities(product_id);
create index ix_non_conformities_batch_id on non_conformities(batch_id);
create index ix_non_conformities_supplier_id on non_conformities(supplier_id);
create index ix_non_conformities_inspection_id on non_conformities(inspection_id);
create index ix_non_conformities_audit_id on non_conformities(audit_id);
create index ix_non_conformities_reported_by_user_id on non_conformities(reported_by_user_id);
create index ix_non_conformities_assignee_user_id on non_conformities(assignee_user_id);
create unique index ux_non_conformities_reference on non_conformities(tenant_id, reference);

create table capa_actions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    title varchar(255) not null,
    description text,
    type varchar(30) not null,
    priority varchar(20),
    status varchar(50),
    source_type varchar(50),
    source_id uuid,
    site_id uuid,
    owner_user_id uuid,
    owner_name varchar(255),
    due_date date,
    completed_at timestamptz,
    verified_at timestamptz,
    verified_by varchar(255),
    effectiveness_result varchar(30),
    effectiveness_comment text,
    evidence_note text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_capa_actions_tenant on capa_actions(tenant_id);
create index ix_capa_actions_source_id on capa_actions(source_id);
create index ix_capa_actions_site_id on capa_actions(site_id);
create index ix_capa_actions_owner_user_id on capa_actions(owner_user_id);
create unique index ux_capa_actions_reference on capa_actions(tenant_id, reference);

create table incidents (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    title varchar(255) not null,
    description text,
    domain varchar(30) not null,
    type varchar(50) not null,
    env_category varchar(50),
    severity integer,
    probability integer,
    risk_score integer,
    status varchar(50),
    site_id uuid,
    department_id uuid,
    location_detail varchar(255),
    equipment_id uuid,
    vehicle_id uuid,
    occurred_at timestamptz,
    reported_by_name varchar(255),
    reported_by_user_id uuid,
    injured_persons integer,
    lost_time_days integer,
    immediate_actions text,
    root_cause text,
    investigation_findings text,
    impact_assessment text,
    investigator_name varchar(255),
    investigator_user_id uuid,
    due_date date,
    closed_at timestamptz,
    custom_fields jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_incidents_tenant on incidents(tenant_id);
create index ix_incidents_site_id on incidents(site_id);
create index ix_incidents_department_id on incidents(department_id);
create index ix_incidents_equipment_id on incidents(equipment_id);
create index ix_incidents_vehicle_id on incidents(vehicle_id);
create index ix_incidents_reported_by_user_id on incidents(reported_by_user_id);
create index ix_incidents_investigator_user_id on incidents(investigator_user_id);
create unique index ux_incidents_reference on incidents(tenant_id, reference);

create table inspection_templates (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    domain varchar(30) not null,
    inspection_type varchar(50),
    industry_profile varchar(50),
    template_version integer,
    active boolean,
    description text,
    passing_score double precision,
    questions jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_inspection_templates_tenant on inspection_templates(tenant_id);
create unique index ux_inspection_templates_code on inspection_templates(tenant_id, code);

create table inspections (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    template_id uuid,
    title varchar(255) not null,
    domain varchar(30) not null,
    inspection_type varchar(50),
    site_id uuid,
    department_id uuid,
    warehouse_id uuid,
    production_line_id uuid,
    vehicle_id uuid,
    product_id uuid,
    batch_id uuid,
    supplier_id uuid,
    equipment_id uuid,
    scheduled_date date,
    completed_at timestamptz,
    inspector_user_id uuid,
    inspector_name varchar(255),
    status varchar(30),
    score double precision,
    result varchar(30),
    answers jsonb,
    temperature double precision,
    humidity double precision,
    signature text,
    notes text,
    generated_nc_count integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_inspections_tenant on inspections(tenant_id);
create index ix_inspections_template_id on inspections(template_id);
create index ix_inspections_site_id on inspections(site_id);
create index ix_inspections_department_id on inspections(department_id);
create index ix_inspections_warehouse_id on inspections(warehouse_id);
create index ix_inspections_production_line_id on inspections(production_line_id);
create index ix_inspections_vehicle_id on inspections(vehicle_id);
create index ix_inspections_product_id on inspections(product_id);
create index ix_inspections_batch_id on inspections(batch_id);
create index ix_inspections_supplier_id on inspections(supplier_id);
create index ix_inspections_equipment_id on inspections(equipment_id);
create index ix_inspections_inspector_user_id on inspections(inspector_user_id);
create unique index ux_inspections_reference on inspections(tenant_id, reference);

create table risk_assessments (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    title varchar(255) not null,
    assessment_type varchar(30),
    activity varchar(255),
    hazard text,
    hazard_category varchar(50),
    risk_category varchar(100),
    site_id uuid,
    department_id uuid,
    equipment_id uuid,
    probability integer not null,
    severity integer not null,
    risk_score integer,
    risk_level varchar(20),
    existing_controls text,
    preventive_controls text,
    residual_probability integer,
    residual_severity integer,
    residual_score integer,
    residual_level varchar(20),
    owner_name varchar(255),
    review_date date,
    status varchar(30),
    jsa_steps jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_risk_assessments_tenant on risk_assessments(tenant_id);
create index ix_risk_assessments_site_id on risk_assessments(site_id);
create index ix_risk_assessments_department_id on risk_assessments(department_id);
create index ix_risk_assessments_equipment_id on risk_assessments(equipment_id);
create unique index ux_risk_assessments_reference on risk_assessments(tenant_id, reference);

create table work_permits (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    permit_type varchar(50) not null,
    site_id uuid,
    equipment_id uuid,
    description text,
    requested_by varchar(255),
    contractor varchar(255),
    valid_from timestamptz,
    valid_to timestamptz,
    status varchar(30),
    approved_by varchar(255),
    precautions jsonb,
    gas_test_result varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_work_permits_tenant on work_permits(tenant_id);
create index ix_work_permits_site_id on work_permits(site_id);
create index ix_work_permits_equipment_id on work_permits(equipment_id);
create unique index ux_work_permits_reference on work_permits(tenant_id, reference);

create table audits (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    title varchar(255) not null,
    audit_type varchar(30) not null,
    standard varchar(50),
    scope text,
    site_id uuid,
    supplier_id uuid,
    lead_auditor varchar(255),
    audit_team varchar(500),
    planned_date date,
    start_date date,
    end_date date,
    status varchar(30),
    template_id uuid,
    score double precision,
    conclusion text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_audits_tenant on audits(tenant_id);
create index ix_audits_site_id on audits(site_id);
create index ix_audits_supplier_id on audits(supplier_id);
create index ix_audits_template_id on audits(template_id);
create unique index ux_audits_reference on audits(tenant_id, reference);

create table audit_findings (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    audit_id uuid not null,
    reference varchar(50),
    clause varchar(100),
    finding_type varchar(30) not null,
    description text not null,
    evidence text,
    non_conformity_id uuid,
    status varchar(30),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_audit_findings_tenant on audit_findings(tenant_id);
create index ix_audit_findings_audit_id on audit_findings(audit_id);
create index ix_audit_findings_non_conformity_id on audit_findings(non_conformity_id);
create unique index ux_audit_findings_reference on audit_findings(tenant_id, reference);

create table qhse_documents (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50) not null,
    title varchar(255) not null,
    doc_type varchar(50) not null,
    domain varchar(30),
    doc_version varchar(20),
    status varchar(30),
    owner_name varchar(255),
    approver_name varchar(255),
    approved_at timestamptz,
    effective_date date,
    review_date date,
    expiry_date date,
    site_id uuid,
    access_level varchar(30),
    allowed_roles jsonb,
    standard_clause varchar(100),
    previous_version_id uuid,
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_qhse_documents_tenant on qhse_documents(tenant_id);
create index ix_qhse_documents_site_id on qhse_documents(site_id);
create index ix_qhse_documents_previous_version_id on qhse_documents(previous_version_id);
create unique index ux_qhse_documents_reference on qhse_documents(tenant_id, reference);

create table trainings (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    title varchar(255) not null,
    category varchar(30),
    mandatory boolean,
    validity_months integer,
    duration_hours double precision,
    provider varchar(255),
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_trainings_tenant on trainings(tenant_id);
create unique index ux_trainings_code on trainings(tenant_id, code);

create table training_records (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    employee_id uuid not null,
    training_id uuid not null,
    session_date date,
    status varchar(30),
    score double precision,
    expiry_date date,
    trainer varchar(255),
    certificate_number varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_training_records_tenant on training_records(tenant_id);
create index ix_training_records_employee_id on training_records(employee_id);
create index ix_training_records_training_id on training_records(training_id);

create table certifications (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    employee_id uuid not null,
    name varchar(255) not null,
    cert_type varchar(30),
    issuer varchar(255),
    number varchar(100),
    issued_date date,
    expiry_date date,
    status varchar(30),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_certifications_tenant on certifications(tenant_id);
create index ix_certifications_employee_id on certifications(employee_id);

create table ppe_items (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    category varchar(100),
    standard_ref varchar(100),
    stock_quantity integer,
    min_stock integer,
    replacement_months integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_ppe_items_tenant on ppe_items(tenant_id);
create unique index ux_ppe_items_code on ppe_items(tenant_id, code);

create table ppe_issuances (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    employee_id uuid not null,
    ppe_item_id uuid not null,
    quantity integer not null,
    issued_date date,
    replacement_date date,
    issued_by varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_ppe_issuances_tenant on ppe_issuances(tenant_id);
create index ix_ppe_issuances_employee_id on ppe_issuances(employee_id);
create index ix_ppe_issuances_ppe_item_id on ppe_issuances(ppe_item_id);

create table cleaning_schedules (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    area varchar(255) not null,
    site_id uuid,
    warehouse_id uuid,
    vehicle_id uuid,
    frequency varchar(30),
    method text,
    products_used varchar(500),
    responsible_name varchar(255),
    last_done_at timestamptz,
    next_due_at timestamptz,
    active boolean,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_cleaning_schedules_tenant on cleaning_schedules(tenant_id);
create index ix_cleaning_schedules_site_id on cleaning_schedules(site_id);
create index ix_cleaning_schedules_warehouse_id on cleaning_schedules(warehouse_id);
create index ix_cleaning_schedules_vehicle_id on cleaning_schedules(vehicle_id);

create table cleaning_records (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    schedule_id uuid not null,
    done_at timestamptz not null,
    done_by varchar(255),
    verified boolean,
    verified_by varchar(255),
    notes varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_cleaning_records_tenant on cleaning_records(tenant_id);
create index ix_cleaning_records_schedule_id on cleaning_records(schedule_id);

create table pest_control_visits (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    site_id uuid not null,
    provider varchar(255),
    visit_date date not null,
    bait_stations_checked integer,
    activity_found boolean,
    findings text,
    next_visit date,
    status varchar(30),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_pest_control_visits_tenant on pest_control_visits(tenant_id);
create index ix_pest_control_visits_site_id on pest_control_visits(site_id);

create table environmental_readings (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reading_type varchar(50) not null,
    site_id uuid,
    warehouse_id uuid,
    vehicle_id uuid,
    location varchar(255),
    reading_value double precision not null,
    unit varchar(20),
    min_threshold double precision,
    max_threshold double precision,
    within_limits boolean,
    recorded_at timestamptz not null,
    source varchar(30),
    sensor_id varchar(100),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_environmental_readings_tenant on environmental_readings(tenant_id);
create index ix_environmental_readings_site_id on environmental_readings(site_id);
create index ix_environmental_readings_warehouse_id on environmental_readings(warehouse_id);
create index ix_environmental_readings_vehicle_id on environmental_readings(vehicle_id);
create index ix_environmental_readings_sensor_id on environmental_readings(sensor_id);

create table waste_records (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    reference varchar(50),
    waste_type varchar(255) not null,
    classification varchar(30) not null,
    waste_code varchar(50),
    site_id uuid,
    quantity_kg double precision not null,
    treatment varchar(30),
    carrier varchar(255),
    destination varchar(255),
    manifest_number varchar(100),
    recorded_date date not null,
    cost numeric(16,4),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_waste_records_tenant on waste_records(tenant_id);
create index ix_waste_records_site_id on waste_records(site_id);
create unique index ux_waste_records_reference on waste_records(tenant_id, reference);

create table chemical_products (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    name varchar(255) not null,
    cas_number varchar(50),
    hazard_class varchar(100),
    site_id uuid,
    storage_location varchar(255),
    quantity double precision,
    unit varchar(20),
    sds_document_id uuid,
    sds_expiry date,
    ghs_pictograms varchar(500),
    supplier_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_chemical_products_tenant on chemical_products(tenant_id);
create index ix_chemical_products_site_id on chemical_products(site_id);
create index ix_chemical_products_sds_document_id on chemical_products(sds_document_id);
create index ix_chemical_products_supplier_id on chemical_products(supplier_id);

create table custom_field_definitions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    entity_type varchar(50) not null,
    field_key varchar(50) not null,
    label varchar(255) not null,
    field_type varchar(30) not null,
    options jsonb,
    required boolean,
    sort_order integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_custom_field_definitions_tenant on custom_field_definitions(tenant_id);

create table kpi_definitions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    code varchar(50) not null,
    name varchar(255) not null,
    domain varchar(30) not null,
    unit varchar(20),
    target double precision,
    direction varchar(30),
    calculator varchar(100) not null,
    active boolean,
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_kpi_definitions_tenant on kpi_definitions(tenant_id);
create unique index ux_kpi_definitions_code on kpi_definitions(tenant_id, code);

create table workflow_definitions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    workflow_key varchar(50) not null,
    name varchar(255) not null,
    entity_type varchar(50) not null,
    description text,
    initial_state varchar(50) not null,
    states jsonb,
    transitions jsonb,
    active boolean,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_workflow_definitions_tenant on workflow_definitions(tenant_id);
create unique index ux_workflow_definitions_workflow_key on workflow_definitions(tenant_id, workflow_key);

create table workflow_events (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    entity_type varchar(50) not null,
    entity_id uuid not null,
    from_status varchar(50),
    to_status varchar(50) not null,
    transition varchar(50),
    comment text,
    actor_user_id uuid,
    actor_name varchar(255),
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_workflow_events_tenant on workflow_events(tenant_id);
create index ix_workflow_events_entity_id on workflow_events(entity_id);
create index ix_workflow_events_actor_user_id on workflow_events(actor_user_id);

create table comments (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    entity_type varchar(50) not null,
    entity_id uuid not null,
    body text not null,
    author_user_id uuid,
    author_name varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_comments_tenant on comments(tenant_id);
create index ix_comments_entity_id on comments(entity_id);
create index ix_comments_author_user_id on comments(author_user_id);

create table attachments (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    entity_type varchar(50) not null,
    entity_id uuid not null,
    file_name varchar(255) not null,
    content_type varchar(100),
    size_bytes bigint,
    storage_key varchar(500) not null,
    kind varchar(30),
    uploaded_by varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_attachments_tenant on attachments(tenant_id);
create index ix_attachments_entity_id on attachments(entity_id);

create table notifications (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    user_id uuid,
    target_role varchar(50),
    title varchar(255) not null,
    message text,
    severity varchar(20),
    category varchar(50),
    entity_type varchar(50),
    entity_id uuid,
    read_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_notifications_tenant on notifications(tenant_id);
create index ix_notifications_user_id on notifications(user_id);
create index ix_notifications_entity_id on notifications(entity_id);

create table notification_rules (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    event_type varchar(100) not null,
    roles jsonb,
    channels jsonb,
    min_severity varchar(20),
    active boolean,
    description varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_notification_rules_tenant on notification_rules(tenant_id);

create table sync_jobs (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    connector_id uuid not null,
    trigger_type varchar(30),
    status varchar(30),
    started_at timestamptz,
    finished_at timestamptz,
    records_read integer,
    records_created integer,
    records_updated integer,
    records_skipped integer,
    records_failed integer,
    cursor_value varchar(100),
    message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_sync_jobs_tenant on sync_jobs(tenant_id);
create index ix_sync_jobs_connector_id on sync_jobs(connector_id);

create table sync_records (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    job_id uuid not null,
    connector_id uuid not null,
    entity_type varchar(50) not null,
    external_id varchar(100) not null,
    status varchar(30) not null,
    payload jsonb,
    error text,
    attempts integer,
    local_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_sync_records_tenant on sync_records(tenant_id);
create index ix_sync_records_job_id on sync_records(job_id);
create index ix_sync_records_connector_id on sync_records(connector_id);
create index ix_sync_records_local_id on sync_records(local_id);

create table integration_id_map (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    connector_id uuid not null,
    entity_type varchar(50) not null,
    external_id varchar(100) not null,
    local_id uuid not null,
    payload_hash varchar(100),
    synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_integration_id_map_tenant on integration_id_map(tenant_id);
create index ix_integration_id_map_connector_id on integration_id_map(connector_id);
create index ix_integration_id_map_local_id on integration_id_map(local_id);

create table ai_predictions (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    prediction_type varchar(50) not null,
    target_type varchar(50),
    target_id uuid,
    model varchar(100),
    horizon integer,
    result jsonb,
    metrics jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_ai_predictions_tenant on ai_predictions(tenant_id);
create index ix_ai_predictions_target_id on ai_predictions(target_id);

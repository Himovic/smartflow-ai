-- SmartFlow AI + QHSE — core platform schema (tenancy, identity, audit)
create table tenants (
    id uuid primary key,
    code varchar(50) not null unique,
    name varchar(255) not null,
    industry_profile varchar(50) not null,
    country varchar(255),
    timezone varchar(255),
    default_language varchar(5),
    demo boolean not null default false,
    active boolean not null default true,
    settings jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table users (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    first_name varchar(255) not null,
    last_name varchar(255) not null,
    role varchar(40) not null,
    job_title varchar(255),
    phone varchar(255),
    department_id uuid,
    employee_id uuid,
    all_sites boolean not null default false,
    site_ids jsonb,
    language varchar(5),
    active boolean not null default true,
    last_login_at timestamptz,
    failed_attempts integer not null default 0,
    locked_until timestamptz,
    password_reset_hash varchar(255),
    password_reset_expires timestamptz,
    password_changed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_users_tenant on users(tenant_id);

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    tenant_id uuid not null,
    token_hash varchar(255) not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null,
    last_used_at timestamptz,
    user_agent varchar(500),
    ip_address varchar(64)
);
create index ix_refresh_tokens_user on refresh_tokens(user_id);

create table login_history (
    id uuid primary key,
    tenant_id uuid,
    user_id uuid,
    email varchar(255),
    success boolean not null,
    reason varchar(100),
    ip_address varchar(64),
    user_agent varchar(500),
    occurred_at timestamptz not null
);
create index ix_login_history_user on login_history(user_id, occurred_at desc);
create index ix_login_history_tenant on login_history(tenant_id, occurred_at desc);

create table audit_logs (
    id uuid primary key,
    tenant_id uuid,
    user_id uuid,
    user_email varchar(255),
    action varchar(50) not null,
    entity_type varchar(80),
    entity_id uuid,
    details jsonb,
    occurred_at timestamptz not null
);
create index ix_audit_logs_tenant on audit_logs(tenant_id, occurred_at desc);
create index ix_audit_logs_entity on audit_logs(entity_type, entity_id);

create table reference_sequences (
    tenant_id uuid not null,
    prefix varchar(20) not null,
    year integer not null,
    value bigint not null,
    primary key (tenant_id, prefix, year)
);

create table integration_connectors (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    name varchar(255) not null,
    provider varchar(50) not null,
    category varchar(30) not null,
    mode varchar(20) not null,
    base_url varchar(500),
    auth_type varchar(30),
    credentials_encrypted text,
    config jsonb,
    entity_types jsonb,
    enabled boolean not null default false,
    sync_frequency_minutes integer,
    conflict_policy varchar(20),
    last_sync_at timestamptz,
    last_status varchar(20),
    last_error text,
    cursors jsonb,
    webhook_secret_encrypted text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by varchar(255),
    updated_by varchar(255),
    version bigint not null default 0
);
create index ix_integration_connectors_tenant on integration_connectors(tenant_id);

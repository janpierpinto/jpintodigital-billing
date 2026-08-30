-- Schema da lib jp-billing. O host inclui este caminho nas locations do Flyway:
--   spring.flyway.locations=classpath:db/migration,classpath:billing/migration
-- Tabelas prefixadas billing_ para não colidir com o schema do host.
-- Nenhuma é multi-tenant-RLS por si — o host aplica a política que quiser sobre elas.

create table billing_plans (
    code         varchar(60) primary key,
    name         varchar(120) not null,
    amount_cents bigint not null,
    currency     varchar(3) not null default 'BRL',
    trial_days   integer not null default 14,
    max_units    integer not null default 0,
    active       boolean not null default true
);

create table billing_subscriptions (
    id                       uuid primary key,
    tenant_id                uuid not null unique,
    plan_code                varchar(60) not null references billing_plans (code),
    status                   varchar(15) not null,
    provider                 varchar(20) not null default 'none',
    provider_customer_id     varchar(80),
    provider_subscription_id varchar(80),
    current_period_end       timestamptz,
    trial_end                timestamptz,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    canceled_at              timestamptz,
    version                  bigint not null default 0
);
create index idx_billing_subscriptions_provider on billing_subscriptions (provider_subscription_id);

create table billing_payments (
    id                  uuid primary key,
    subscription_id     uuid not null references billing_subscriptions (id) on delete cascade,
    tenant_id           uuid not null,
    provider_payment_id varchar(80) not null unique,
    amount_cents        bigint not null,
    status              varchar(20) not null,
    due_date            date,
    paid_at             timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);
create index idx_billing_payments_tenant on billing_payments (tenant_id, due_date desc);

create table billing_webhook_events (
    id               uuid primary key,
    provider         varchar(20) not null,
    event_id         varchar(120) not null unique,
    event_type       varchar(60),
    received_at      timestamptz not null default now(),
    processed_at     timestamptz,
    processing_error varchar(500),
    payload          text not null
);
create index idx_billing_webhook_unprocessed on billing_webhook_events (received_at) where processed_at is null;

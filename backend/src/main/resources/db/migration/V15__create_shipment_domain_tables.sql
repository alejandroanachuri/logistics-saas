-- V15__create_shipment_domain_tables.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-3-envios / PR-1 (DB foundation, V15 of 3)
-- Author:   logistics-saas-team
-- Date:     2026-06-25
-- Purpose:  DDL for the shipment lifecycle domain: addresses, customers,
--           shipments, packages, tracking_events, shipment_custody,
--           id_sequences, branches, service_levels. RLS is added in V16
--           (separate migration, mirroring the V12/V14 split pattern so
--           that the DDL is shippable independently of the policy layer).
--
-- RLS DESIGN: every table carries tenant_id and inherits the standard
-- single-tenant isolation pattern. shipment_custody uses a subquery
-- against packages (V14-style) because it does not have a direct tenant_id
-- column. id_sequences, branches, service_levels are tenant-scoped
-- catalog tables.
--
-- IDEMPOTENCY: CREATE TABLE / CREATE INDEX are not idempotent in raw SQL.
-- We rely on Flyway tracking + the migration being part of the chain. No
-- ALTER TABLE IF NOT EXISTS in this DDL.
--
-- SPEC REFERENCE: prd-etapa-3-envios.md §6 (data model).
-- ----------------------------------------------------------------------------

-- ============================================================================
-- addresses
-- ============================================================================
CREATE TABLE public.addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES public.tenants(id),
    street          VARCHAR(200) NOT NULL,
    number          VARCHAR(20) NOT NULL,
    floor           VARCHAR(20),
    apartment       VARCHAR(20),
    city            VARCHAR(100) NOT NULL,
    province        VARCHAR(100) NOT NULL,
    postal_code     VARCHAR(10) NOT NULL,
    reference       TEXT,
    country         CHAR(2) NOT NULL DEFAULT 'AR',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_addresses_tenant_id ON public.addresses (tenant_id);
CREATE INDEX idx_addresses_deleted_at ON public.addresses (deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- customers (with field-level security hooks via @JsonView in the entity)
-- ============================================================================
CREATE TABLE public.customers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES public.tenants(id),
    person_type         VARCHAR(10) NOT NULL CHECK (person_type IN ('FISICA','JURIDICA')),
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100),
    razon_social        VARCHAR(200),
    dni                 VARCHAR(8),
    cuit_cuil           VARCHAR(11),
    tax_condition       VARCHAR(30) NOT NULL DEFAULT 'NO_CATEGORIZADO'
        CHECK (tax_condition IN ('RESPONSABLE_INSCRIPTO','MONOTRIBUTISTA','EXENTO','CONSUMIDOR_FINAL','NO_CATEGORIZADO')),
    phone               VARCHAR(30) NOT NULL,
    email               VARCHAR(100),
    default_address_id  UUID REFERENCES public.addresses(id),
    account_id          UUID,
    data_consent        BOOLEAN NOT NULL DEFAULT FALSE,
    consent_date        TIMESTAMP WITH TIME ZONE,
    consent_version     VARCHAR(20),
    notes               TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP WITH TIME ZONE,
    created_by          UUID,
    updated_by          UUID,
    -- Business rule RN-LOGI-011 (PRD §11): FISICA requires DNI; JURIDICA requires CUIT + razon_social.
    CONSTRAINT chk_fisica_dni       CHECK (person_type != 'FISICA'    OR dni IS NOT NULL),
    CONSTRAINT chk_juridica_cuit    CHECK (person_type != 'JURIDICA'  OR (cuit_cuil IS NOT NULL AND razon_social IS NOT NULL)),
    CONSTRAINT chk_consent_date     CHECK ((data_consent = FALSE)     OR (consent_date IS NOT NULL))
);

CREATE INDEX idx_customers_tenant_id ON public.customers (tenant_id);
CREATE INDEX idx_customers_dni ON public.customers (tenant_id, dni) WHERE dni IS NOT NULL;
CREATE INDEX idx_customers_cuit ON public.customers (tenant_id, cuit_cuil) WHERE cuit_cuil IS NOT NULL;
CREATE INDEX idx_customers_deleted_at ON public.customers (deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- shipments (the central entity of the system)
-- ============================================================================
CREATE TABLE public.shipments (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES public.tenants(id),
    tracking_id              VARCHAR(13) NOT NULL UNIQUE,    -- LGST-XXXXXXXX, unique global
    code                     VARCHAR(20),                    -- optional internal code
    shipment_type            VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (shipment_type IN ('NORMAL','RETURN')),
    parent_shipment_id       UUID REFERENCES public.shipments(id),  -- only if RETURN (Etapa 9)
    sender_id                UUID NOT NULL REFERENCES public.customers(id),
    receiver_id              UUID NOT NULL REFERENCES public.customers(id),
    delivery_address_id      UUID NOT NULL REFERENCES public.addresses(id),
    origin_branch_id         UUID NOT NULL,                  -- FK to branches (placeholder Etapa 4)
    destination_branch_id    UUID NOT NULL,                  -- FK to branches (placeholder Etapa 4)
    service_level_id         UUID NOT NULL,                  -- FK to service_levels (placeholder Etapa 4)
    payment_type             VARCHAR(30) NOT NULL
        CHECK (payment_type IN ('PAGO_ORIGEN','PAGO_DESTINO','CUENTA_CORRIENTE')),
    delivery_mode            VARCHAR(30) NOT NULL DEFAULT 'DOMICILIO'
        CHECK (delivery_mode IN ('DOMICILIO','RETIRO_SUCURSAL')),
    delivery_instructions    TEXT,
    status                   VARCHAR(50) NOT NULL DEFAULT 'PRE_ALTA',
    promised_delivery_date   DATE,
    sla_status               VARCHAR(20) NOT NULL DEFAULT 'EN_PLAZO'
        CHECK (sla_status IN ('EN_PLAZO','EN_RIESGO','VENCIDO','CUMPLIDO')),
    total_weight_kg          DECIMAL(8,2),
    total_cost               DECIMAL(12,2),                  -- calculated by Etapa 4; null in Etapa 3
    created_by               UUID NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at               TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_sender_ne_receiver  CHECK (sender_id != receiver_id),
    CONSTRAINT chk_return_has_parent   CHECK (shipment_type != 'RETURN' OR parent_shipment_id IS NOT NULL)
);

CREATE INDEX idx_shipments_tenant_id    ON public.shipments (tenant_id);
CREATE INDEX idx_shipments_tracking_id  ON public.shipments (tracking_id);
CREATE INDEX idx_shipments_status       ON public.shipments (tenant_id, status);
CREATE INDEX idx_shipments_created_at   ON public.shipments (tenant_id, created_at DESC);
CREATE INDEX idx_shipments_deleted_at   ON public.shipments (deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- packages (one or more per shipment; FSM is on packages, not on shipments)
-- ============================================================================
CREATE TABLE public.packages (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES public.tenants(id),
    shipment_id             UUID NOT NULL REFERENCES public.shipments(id),
    qr_code                 VARCHAR(255) NOT NULL UNIQUE,   -- internal operative identifier
    previous_status         VARCHAR(60),                    -- prior state before RETENIDO entry
    status                  VARCHAR(60) NOT NULL DEFAULT 'CREADO',
    weight_kg               DECIMAL(8,2) NOT NULL CHECK (weight_kg > 0),
    volume_cm3              DECIMAL(10,2),
    dimensions_cm           VARCHAR(30),                    -- "LxAxP cm" e.g. "30x20x15"
    content_description     TEXT NOT NULL,
    declared_value          DECIMAL(12,2),
    declared_currency       CHAR(3) NOT NULL DEFAULT 'ARS',
    has_insurance           BOOLEAN NOT NULL DEFAULT FALSE,
    insurance_premium       DECIMAL(10,2),
    is_fragile              BOOLEAN NOT NULL DEFAULT FALSE,
    is_urgent               BOOLEAN NOT NULL DEFAULT FALSE,
    requires_signature      BOOLEAN NOT NULL DEFAULT TRUE,
    requires_id_check       BOOLEAN NOT NULL DEFAULT TRUE,
    category                VARCHAR(30) NOT NULL DEFAULT 'GENERAL'
        CHECK (category IN ('GENERAL','DOCUMENTOS','ELECTRONICA','ALIMENTOS','MEDICAMENTOS','PELIGROSO')),
    reception_condition     VARCHAR(20) DEFAULT 'BUENO'
        CHECK (reception_condition IN ('BUENO','DAÑADO_EXTERNO','ABIERTO')),
    reception_notes         TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_packages_tenant_id   ON public.packages (tenant_id);
CREATE INDEX idx_packages_shipment_id ON public.packages (shipment_id);
CREATE INDEX idx_packages_qr_code     ON public.packages (qr_code);
CREATE INDEX idx_packages_status      ON public.packages (tenant_id, status);

-- ============================================================================
-- tracking_events (APPEND-ONLY, immutable, idempotent via event_hash)
-- ============================================================================
CREATE TABLE public.tracking_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES public.tenants(id),
    package_id      UUID NOT NULL REFERENCES public.packages(id),
    event_type      VARCHAR(60) NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    branch_id       UUID,                                -- nullable; populated in Etapa 5+
    user_id         UUID NOT NULL,
    event_source    VARCHAR(20) NOT NULL DEFAULT 'OPERADOR_SUCURSAL'
        CHECK (event_source IN ('OPERADOR_SUCURSAL','SISTEMA','ALIADO','CLIENTE')),
    event_hash      VARCHAR(64) NOT NULL UNIQUE,         -- SHA-256 idempotency
    metadata        JSONB,
    source_ip       VARCHAR(45),                          -- for audit
    user_agent      VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    -- NO updated_at, NO deleted_at: APPEND-ONLY per PRD §8.5
);

CREATE INDEX idx_tracking_events_tenant_id   ON public.tracking_events (tenant_id);
CREATE INDEX idx_tracking_events_package_id  ON public.tracking_events (package_id, event_timestamp DESC);
CREATE INDEX idx_tracking_events_hash        ON public.tracking_events (event_hash);
CREATE INDEX idx_tracking_events_type        ON public.tracking_events (tenant_id, event_type, event_timestamp DESC);

-- ============================================================================
-- shipment_custody (projection; full handoff is Etapa 6+)
-- ============================================================================
CREATE TABLE public.shipment_custody (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id             UUID NOT NULL REFERENCES public.packages(id),
    custodian_tenant_id    UUID NOT NULL REFERENCES public.tenants(id),
    acquired_event_id      UUID NOT NULL REFERENCES public.tracking_events(id),
    acquired_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    released_event_id      UUID REFERENCES public.tracking_events(id),
    released_at            TIMESTAMP WITH TIME ZONE,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (package_id, acquired_event_id)
);

CREATE INDEX idx_custody_package_active ON public.shipment_custody (package_id, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_custody_custodian      ON public.shipment_custody (custodian_tenant_id, is_active) WHERE is_active = TRUE;

-- ============================================================================
-- id_sequences (internal codes; LGST is random, not sequenced)
-- ============================================================================
CREATE TABLE public.id_sequences (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES public.tenants(id),
    sequence_key  VARCHAR(50) NOT NULL,
    year          INT NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, sequence_key, year)
);

CREATE INDEX idx_id_sequences_tenant_id ON public.id_sequences (tenant_id);

-- ============================================================================
-- branches (placeholder for Etapa 4; lazy seed PRINCIPAL on first shipment)
-- ============================================================================
CREATE TABLE public.branches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES public.tenants(id),
    code            VARCHAR(20) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    address_id      UUID REFERENCES public.addresses(id),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_branches_tenant_id ON public.branches (tenant_id);

-- ============================================================================
-- service_levels (placeholder for Etapa 4; lazy seed STANDARD on first shipment)
-- ============================================================================
CREATE TABLE public.service_levels (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES public.tenants(id),
    code            VARCHAR(20) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE,
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_service_levels_tenant_id ON public.service_levels (tenant_id);

-- ============================================================================
-- FK constraints for placeholder columns (origin_branch_id, destination_branch_id,
-- service_level_id on shipments) — added at the end so all referenced tables
-- exist before we add the constraints.
-- ============================================================================
ALTER TABLE public.shipments
    ADD CONSTRAINT shipments_origin_branch_fk
        FOREIGN KEY (origin_branch_id) REFERENCES public.branches(id);
ALTER TABLE public.shipments
    ADD CONSTRAINT shipments_destination_branch_fk
        FOREIGN KEY (destination_branch_id) REFERENCES public.branches(id);
ALTER TABLE public.shipments
    ADD CONSTRAINT shipments_service_level_fk
        FOREIGN KEY (service_level_id) REFERENCES public.service_levels(id);

-- ============================================================================
-- GRANTs — V8's GRANT ON ALL TABLES only covered tables present at V8 time.
-- We re-grant here so the new tables are usable by the application roles.
-- ============================================================================
GRANT SELECT, INSERT, UPDATE, DELETE ON
    public.addresses,
    public.customers,
    public.shipments,
    public.packages,
    public.tracking_events,
    public.shipment_custody,
    public.id_sequences,
    public.branches,
    public.service_levels
TO app_user;

-- app_admin and app_platform need CRUD on the new tables too (mirror V14 pattern):
-- app_admin for the registration/cross-tenant onboarding flows,
-- app_platform for the (future) cross-tenant admin views.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    public.addresses,
    public.customers,
    public.shipments,
    public.packages,
    public.tracking_events,
    public.shipment_custody,
    public.id_sequences,
    public.branches,
    public.service_levels
TO app_admin;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    public.addresses,
    public.customers,
    public.shipments,
    public.packages,
    public.tracking_events,
    public.shipment_custody,
    public.id_sequences,
    public.branches,
    public.service_levels
TO app_platform;

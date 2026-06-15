-- V1__create_tenants.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1a (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 211-243 (verbatim, indexes preserved).
--
-- Creates the tenants table. The slug and CUIT are globally unique. The
-- tax_type and status are CHECK-constrained to the three / three values the
-- frontend hard-codes. Soft-delete is via a nullable deleted_at; a partial
-- index over "alive" rows keeps the partial-unique lookups cheap.
-- ----------------------------------------------------------------------------

CREATE TABLE public.tenants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(12) NOT NULL,
    legal_name          VARCHAR(255) NOT NULL,
    commercial_name     VARCHAR(255),
    cuit                VARCHAR(13) NOT NULL,
    tax_type            VARCHAR(30) NOT NULL,
    contact_email       VARCHAR(255) NOT NULL,
    contact_phone       VARCHAR(50),
    country             CHAR(2) NOT NULL DEFAULT 'AR',
    province            VARCHAR(100),
    city                VARCHAR(100),
    address_line        VARCHAR(255),
    address_number      VARCHAR(20),
    address_floor       VARCHAR(10),
    address_apartment   VARCHAR(10),
    postal_code         VARCHAR(20),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP WITH TIME ZONE,
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT tenants_slug_unique UNIQUE (slug),
    CONSTRAINT tenants_cuit_unique UNIQUE (cuit),
    CONSTRAINT tenants_tax_type_check CHECK (tax_type IN ('RESPONSABLE_INSCRIPTO', 'MONOTRIBUTO', 'EXENTO')),
    CONSTRAINT tenants_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TRIAL'))
);

CREATE INDEX idx_tenants_slug ON public.tenants (slug);
CREATE INDEX idx_tenants_cuit ON public.tenants (cuit);
CREATE INDEX idx_tenants_deleted_at ON public.tenants (deleted_at) WHERE deleted_at IS NULL;

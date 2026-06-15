-- V2__create_roles.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1a (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 250-259 (verbatim).
--
-- The roles catalog. Two scopes: COMPANY (inside a tenant) and PLATFORM
-- (cross-tenant). V7 seeds six rows; the (name, scope) pair is the lookup
-- key used by TenantRegistrationService and JwtService.
-- ----------------------------------------------------------------------------

CREATE TABLE public.roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50) NOT NULL,
    scope           VARCHAR(20) NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT roles_name_unique UNIQUE (name),
    CONSTRAINT roles_scope_check CHECK (scope IN ('COMPANY', 'PLATFORM'))
);

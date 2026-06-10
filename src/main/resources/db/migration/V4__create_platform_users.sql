-- V4__create_platform_users.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1a (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 317-339 (verbatim).
--
-- platform_users: cross-tenant operators (internal staff). Created via the
-- CLI (PR9) or an admin endpoint. NOT RLS-scoped — they are global.
-- email is unique globally. There is no tenant_id column by design.
-- ----------------------------------------------------------------------------

CREATE TABLE public.platform_users (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id                     UUID NOT NULL,
    email                       VARCHAR(255) NOT NULL,
    first_name                  VARCHAR(100),
    last_name                   VARCHAR(100),
    password_hash               VARCHAR(255) NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_login_at               TIMESTAMP WITH TIME ZONE,
    failed_login_attempts       INT NOT NULL DEFAULT 0,
    locked_until                TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP WITH TIME ZONE,
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT platform_users_role_fk FOREIGN KEY (role_id) REFERENCES public.roles(id),
    CONSTRAINT platform_users_email_unique UNIQUE (email),
    CONSTRAINT platform_users_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_platform_users_role_id ON public.platform_users (role_id);

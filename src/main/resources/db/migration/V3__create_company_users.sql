-- V3__create_company_users.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1a (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 278-310 (verbatim, indexes preserved).
--
-- company_users: tenant-scoped users. The two unique constraints
-- (tenant_id, username) and (tenant_id, email) prevent duplicate identities
-- inside a tenant; the same email MAY exist in two tenants (separate
-- identifiers). The (email_verified = FALSE) partial index speeds up
-- resend-verification lookups.
-- ----------------------------------------------------------------------------

CREATE TABLE public.company_users (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    role_id                     UUID NOT NULL,
    username                    VARCHAR(30) NOT NULL,
    email                       VARCHAR(255) NOT NULL,
    first_name                  VARCHAR(100),
    last_name                   VARCHAR(100),
    password_hash               VARCHAR(255) NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    email_verified              BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at           TIMESTAMP WITH TIME ZONE,
    verification_token          UUID,
    verification_token_expires_at TIMESTAMP WITH TIME ZONE,
    last_login_at               TIMESTAMP WITH TIME ZONE,
    failed_login_attempts       INT NOT NULL DEFAULT 0,
    locked_until                TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP WITH TIME ZONE,
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT company_users_tenant_fk FOREIGN KEY (tenant_id) REFERENCES public.tenants(id),
    CONSTRAINT company_users_role_fk FOREIGN KEY (role_id) REFERENCES public.roles(id),
    CONSTRAINT company_users_tenant_username_unique UNIQUE (tenant_id, username),
    CONSTRAINT company_users_tenant_email_unique UNIQUE (tenant_id, email),
    CONSTRAINT company_users_status_check CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_company_users_tenant_id ON public.company_users (tenant_id);
CREATE INDEX idx_company_users_role_id ON public.company_users (role_id);
CREATE INDEX idx_company_users_email_verified ON public.company_users (email_verified) WHERE email_verified = FALSE;

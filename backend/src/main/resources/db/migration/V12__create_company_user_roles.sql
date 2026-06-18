-- V12__create_company_user_roles.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-2-usuarios / PR-1 (DB foundation)
-- Author:   logistics-saas-team
-- Date:     2026-06-18
-- Purpose:  Introduce the company_user_roles junction table that replaces
--           the single role_id FK on company_users. Composite PK + FKs
--           (CASCADE on user, RESTRICT on role) + indexes on both FK
--           columns. Append-only — does NOT touch V3 or V8.
--
-- IDEMPOTENCY: CREATE TABLE IF NOT EXISTS, CREATE INDEX IF NOT EXISTS.
-- RLS is enabled in V14 (separate migration per spec A.4 — never edit V8).
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.company_user_roles (
    company_user_id   UUID                     NOT NULL,
    role_id           UUID                     NOT NULL,
    assigned_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    assigned_by       UUID,
    CONSTRAINT company_user_roles_pk PRIMARY KEY (company_user_id, role_id),
    CONSTRAINT company_user_roles_user_fk FOREIGN KEY (company_user_id)
        REFERENCES public.company_users(id) ON DELETE CASCADE,
    CONSTRAINT company_user_roles_role_fk FOREIGN KEY (role_id)
        REFERENCES public.roles(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_company_user_roles_user_id
    ON public.company_user_roles (company_user_id);
CREATE INDEX IF NOT EXISTS idx_company_user_roles_role_id
    ON public.company_user_roles (role_id);

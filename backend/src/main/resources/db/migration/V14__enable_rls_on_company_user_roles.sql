-- V14__enable_rls_on_company_user_roles.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-2-usuarios / PR-1 (DB foundation)
-- Author:   logistics-saas-team
-- Date:     2026-06-18
-- Purpose:  Enable RLS on company_user_roles with a policy that resolves
--           tenant_id via a subquery against company_users. Subquery is
--           preferred over denormalizing tenant_id (single source of
--           truth, no migration drift). app_user gets CRUD; app_admin
--           and app_platform already have table-wide grants from V8.
--
-- IDEMPOTENCY: ALTER TABLE ... ENABLE ROW LEVEL SECURITY is a no-op
-- when already enabled (Postgres). DROP POLICY IF EXISTS + CREATE POLICY
-- is the standard pattern from V8. GRANT is additive.
--
-- SPEC REFERENCE: design §2 (V14) + spec A.4. The RLS aspect pointcut
-- (RlsAspect) is extended in PR-3 to cover auth.repository.company..* so
-- this policy takes effect on every read/write through the company pool.
-- Until PR-3 lands, only registration (systemDataSource, BYPASSRLS) will
-- see this table; that's correct — V12/V13/V14 must run together and the
-- first application writes happen via RegistrationService, which is
-- system-side.
-- ----------------------------------------------------------------------------

ALTER TABLE public.company_user_roles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS company_user_roles_isolation ON public.company_user_roles;
CREATE POLICY company_user_roles_isolation ON public.company_user_roles
    FOR ALL TO app_user
    USING (
        company_user_id IN (
            SELECT id FROM public.company_users
            WHERE tenant_id = current_setting('app.current_tenant', true)::uuid
        )
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON public.company_user_roles TO app_user;

-- app_admin needs CRUD on the junction so the registration path can
-- attach the first COMPANY_ADMIN role in the same transaction that
-- creates the tenant (RegistrationService.register runs under
-- BYPASSRLS; no app.current_tenant context is in scope yet). V8's
-- GRANT ... ON ALL TABLES only covered tables present at V8's
-- execution time, so V14 has to add the junction explicitly.
-- app_platform gets the same grant because F1 platform-admin
-- endpoints read user role assignments cross-tenant.
GRANT SELECT, INSERT, UPDATE, DELETE ON public.company_user_roles TO app_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.company_user_roles TO app_platform;

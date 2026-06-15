-- V8__enable_rls_and_create_roles.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1b (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 432-488 (verbatim policy definitions and grants).
--
-- Enables RLS on the three tenant-scoped tables and creates the policies
-- that drive multi-tenant isolation.
--
-- IDEMPOTENCY: This script is safe to re-run. The initdb script
-- (docker/initdb/01-create-roles.sql) creates the three roles once when
-- the volume is fresh, but V8 also creates them here (with IF NOT EXISTS
-- semantics) so a developer running `mvn flyway:migrate` against a
-- freshly-created database with NO initdb script still ends up with a
-- working RLS layer. The GRANT statements are also rerun safely — GRANT
-- is additive in Postgres.
--
-- DEPENDENCY ORDER: V1..V7 must have run before V8 so the tables exist.
-- The FK + GRANT references are safe because the roles are created
-- before the policies and the grants.
-- ----------------------------------------------------------------------------

-- Enable RLS on the tenant-scoped tables.
ALTER TABLE public.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.company_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refresh_tokens ENABLE ROW LEVEL SECURITY;

-- Idempotently create the three application roles. (The initdb script
-- does this on a fresh container, but V8 covers the case where Flyway
-- is run against a database that has NOT had the initdb script applied.)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user NOINHERIT LOGIN PASSWORD 'app_user';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_admin') THEN
        CREATE ROLE app_admin NOINHERIT LOGIN BYPASSRLS PASSWORD 'app_admin';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_platform') THEN
        CREATE ROLE app_platform NOINHERIT LOGIN PASSWORD 'app_platform';
    END IF;
END
$$;

-- Schema-level USAGE. (The initdb script grants this too, but Flyway
-- may run against a database that skipped the init script.)
GRANT USAGE ON SCHEMA public TO app_user, app_admin, app_platform;

-- Policies for app_user (company users, their session has app.current_tenant set).
DROP POLICY IF EXISTS tenants_isolation ON public.tenants;
CREATE POLICY tenants_isolation ON public.tenants
    FOR ALL TO app_user
    USING (id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS company_users_isolation ON public.company_users;
CREATE POLICY company_users_isolation ON public.company_users
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS refresh_tokens_isolation ON public.refresh_tokens;
CREATE POLICY refresh_tokens_isolation ON public.refresh_tokens
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid
           AND user_scope = 'COMPANY');

-- Policies for app_platform (platform users, cross-tenant access).
DROP POLICY IF EXISTS tenants_platform_all ON public.tenants;
CREATE POLICY tenants_platform_all ON public.tenants
    FOR ALL TO app_platform
    USING (true);

DROP POLICY IF EXISTS company_users_platform_read ON public.company_users;
CREATE POLICY company_users_platform_read ON public.company_users
    FOR SELECT TO app_platform
    USING (true);

DROP POLICY IF EXISTS platform_users_self ON public.platform_users;
CREATE POLICY platform_users_self ON public.platform_users
    FOR ALL TO app_platform
    USING (true);

-- app_user: tables tenant-scoped + roles catalog for FK resolution.
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenants, public.company_users, public.refresh_tokens TO app_user;
GRANT SELECT ON public.roles TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- app_admin: BYPASSRLS, used for registration, login, system ops.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_admin;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_admin;

-- app_platform: cross-tenant reads + writes for /api/v1/platform/* endpoints.
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenants, public.company_users, public.platform_users, public.refresh_tokens, public.audit_log TO app_platform;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.roles TO app_platform;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_platform;

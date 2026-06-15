-- 01-create-roles.sql
--
-- Runs once on first boot of the postgres container (volume empty).
-- Creates the three application roles used by the backend's
-- routing DataSource layer:
--
--   app_user      — RLS-enforced, used for company-scoped reads/writes
--   app_admin     — BYPASSRLS, used for migrations, audit log writes,
--                   registration lookups and refresh-token validation
--   app_platform  — RLS-enforced with cross-tenant platform policies,
--                   used for the /api/v1/platform/* endpoints
--
-- The migrator superuser (created from POSTGRES_USER in docker-compose.yml)
-- is the role Flyway connects as when applying V1..V10 in PR1.
-- This script does NOT create migrations — that is V8's job.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- app_user: RLS-enforced, no special privileges.
CREATE ROLE app_user NOINHERIT;
ALTER ROLE app_user WITH LOGIN PASSWORD 'app_user';

-- app_admin: full backend privileges with BYPASSRLS for migrations
-- and cross-table operations the user role cannot perform.
CREATE ROLE app_admin NOINHERIT;
ALTER ROLE app_admin WITH LOGIN PASSWORD 'app_admin' BYPASSRLS;

-- app_platform: cross-tenant reads (tenant list, tenant detail,
-- platform_users self-management). RLS policies for this role
-- are defined in V8; this script only creates the role and grants
-- baseline schema access.
CREATE ROLE app_platform NOINHERIT;
ALTER ROLE app_platform WITH LOGIN PASSWORD 'app_platform';

-- Database-level connectivity.
GRANT CONNECT ON DATABASE logistics TO app_user;
GRANT CONNECT ON DATABASE logistics TO app_admin;
GRANT CONNECT ON DATABASE logistics TO app_platform;

-- Schema-level USAGE. RLS policies and table-level grants are
-- defined in V8__enable_rls_and_create_roles.sql.
GRANT USAGE ON SCHEMA public TO app_user;
GRANT USAGE ON SCHEMA public TO app_admin;
GRANT USAGE ON SCHEMA public TO app_platform;

-- Schema-level CREATE. The backend's Flyway migrator runs as
-- app_admin (the system DataSource user) per
-- ar.com.logistics.config.SystemDataSourceConfig#systemFlyway.
-- app_admin has BYPASSRLS, which is required for the V8 RLS
-- policy grants, but the V1..V10 migrations also need to
-- CREATE tables, and CREATE is not implied by USAGE+BYPASSRLS
-- alone. Without this grant, Flyway fails on the first V__
-- script with `ERROR: permission denied for schema public`
-- (SQLSTATE 42501). Idempotent: re-running this initdb script
-- after a `docker compose down -v` re-applies the same grants.
GRANT CREATE ON SCHEMA public TO app_user, app_admin, app_platform;

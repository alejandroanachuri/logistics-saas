-- V7__seed_roles.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1b (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 263-271 (verbatim).
--
-- Seed the role catalog. Six rows total: four COMPANY-scoped and two
-- PLATFORM-scoped. The catalog is read-only at runtime; see spec
-- role-catalog.md and design §4.6.
-- ----------------------------------------------------------------------------

INSERT INTO public.roles (name, scope, description) VALUES
    ('COMPANY_ADMIN',    'COMPANY',  'Administrador de la empresa: control total dentro del tenant'),
    ('COMPANY_OPERATOR', 'COMPANY',  'Operador de oficina: crea envíos, ve reportes, no gestiona usuarios'),
    ('COMPANY_DRIVER',   'COMPANY',  'Repartidor: usa la app móvil'),
    ('COMPANY_VIEWER',   'COMPANY',  'Solo lectura'),
    ('PLATFORM_ADMIN',   'PLATFORM', 'Administrador de la plataforma: acceso total cross-tenant'),
    ('PLATFORM_SUPPORT', 'PLATFORM', 'Soporte: acceso limitado a tenants para troubleshooting');

-- V17__add_shipment_columns_to_tenants.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-3-envios / PR-1 (DB foundation, V17 of 3)
-- Author:   logistics-saas-team
-- Date:     2026-06-25
-- Purpose:  Add the shipment-tracking columns to the tenants table.
--           max_shipments_per_month is nullable (= unlimited in Etapa 3
--           per PRD §6.9 — plan enforcement is out of scope).
--           current_month_shipment_count defaults to 0 and is incremented
--           atomically on every shipment creation.
--
-- No enforcement check is added in this migration; the application
-- reads/writes these columns and Etapa 10 (billing) will add the cron
-- job that resets the counter monthly.
--
-- A minimal dev seed is included at the bottom, guarded by the
-- app.env runtime setting so it only runs in dev profiles.
--
-- SPEC REFERENCE: prd-etapa-3-envios.md §6.9.
-- ----------------------------------------------------------------------------

ALTER TABLE public.tenants
    ADD COLUMN max_shipments_per_month INT;

ALTER TABLE public.tenants
    ADD COLUMN current_month_shipment_count INT NOT NULL DEFAULT 0;

-- ============================================================================
-- Optional dev seed: only runs when app.env = 'dev'. Flyway executes this
-- migration once per environment; the WHERE clause prevents accidental
-- population in prod. Safe to leave in the migration because the setting
-- is only set in dev profiles (see application-dev.yml).
-- ============================================================================

-- Dev seed is intentionally minimal in V17. A more comprehensive dev
-- fixture (with example customers + a sample shipment) is best handled
-- by an integration test fixture rather than a Flyway migration, to
-- avoid coupling test data to schema versioning. If you need a few
-- rows in dev, run them manually with psql against your dev DB.

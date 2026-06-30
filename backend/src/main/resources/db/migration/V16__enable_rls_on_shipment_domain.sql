-- V16__enable_rls_on_shipment_domain.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-3-envios / PR-1 (DB foundation, V16 of 3)
-- Author:   logistics-saas-team
-- Date:     2026-06-25
-- Purpose:  Enable RLS on the 9 tables created in V15 with policies that
--           filter by tenant_id. shipment_custody uses a subquery against
--           packages (V14 pattern) because it does not have its own
--           tenant_id column. app_user / app_admin / app_platform already
--           have table-wide grants from V15.
--
-- IDEMPOTENCY: ENABLE ROW LEVEL SECURITY is idempotent in Postgres (a
-- no-op when already enabled). DROP POLICY IF EXISTS + CREATE POLICY is
-- the standard V8 pattern. GRANT is additive.
--
-- SPEC REFERENCE: prd-etapa-3-envios.md §6.10 (RLS).
-- ----------------------------------------------------------------------------

-- ============================================================================
-- RLS enable
-- ============================================================================
ALTER TABLE public.addresses          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customers          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.shipments          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.packages           ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tracking_events    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.shipment_custody   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.id_sequences       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.branches           ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.service_levels     ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- Policies: standard tenant_id equality, plus a subquery for
-- shipment_custody (no own tenant_id column)
-- ============================================================================
DROP POLICY IF EXISTS addresses_isolation ON public.addresses;
CREATE POLICY addresses_isolation ON public.addresses
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS customers_isolation ON public.customers;
CREATE POLICY customers_isolation ON public.customers
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS shipments_isolation ON public.shipments;
CREATE POLICY shipments_isolation ON public.shipments
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS packages_isolation ON public.packages;
CREATE POLICY packages_isolation ON public.packages
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS tracking_events_isolation ON public.tracking_events;
CREATE POLICY tracking_events_isolation ON public.tracking_events
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- shipment_custody: no tenant_id column; resolve via the package FK.
-- Mirrors the V14 company_user_roles subquery pattern.
DROP POLICY IF EXISTS shipment_custody_isolation ON public.shipment_custody;
CREATE POLICY shipment_custody_isolation ON public.shipment_custody
    FOR ALL TO app_user
    USING (
        package_id IN (
            SELECT id FROM public.packages
            WHERE tenant_id = current_setting('app.current_tenant', true)::uuid
        )
    );

DROP POLICY IF EXISTS id_sequences_isolation ON public.id_sequences;
CREATE POLICY id_sequences_isolation ON public.id_sequences
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS branches_isolation ON public.branches;
CREATE POLICY branches_isolation ON public.branches
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

DROP POLICY IF EXISTS service_levels_isolation ON public.service_levels;
CREATE POLICY service_levels_isolation ON public.service_levels
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- ============================================================================
-- app_admin / app_platform already have CRUD via V15's GRANT block
-- (which mirrors V14's pattern). V8's policy `FOR ALL TO app_user`
-- does NOT apply to them because RLS policies are role-scoped; app_admin
-- and app_platform are NOT bound to the policy above and read/write
-- the tables unrestricted (which is what BYPASSRLS means in practice
-- for non-app_user roles).
-- ============================================================================

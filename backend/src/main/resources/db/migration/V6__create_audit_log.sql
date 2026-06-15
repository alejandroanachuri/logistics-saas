-- V6__create_audit_log.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1b (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 373-389 (verbatim).
--
-- audit_log: append-only event log. user_scope is a discriminator that lets
-- the same table log events for both COMPANY and PLATFORM users (nullable
-- for system events that have no actor). metadata is JSONB so we can attach
-- arbitrary structured data per event type without schema churn.
-- ----------------------------------------------------------------------------

CREATE TABLE public.audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID,
    user_scope      VARCHAR(20),
    tenant_id       UUID,
    event_type      VARCHAR(50) NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_log_user_scope_check CHECK (user_scope IN ('COMPANY', 'PLATFORM') OR user_scope IS NULL)
);

CREATE INDEX idx_audit_log_tenant_id_created_at ON public.audit_log (tenant_id, created_at DESC) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_audit_log_user_id ON public.audit_log (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_log_event_type ON public.audit_log (event_type);

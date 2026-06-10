-- V5__create_refresh_tokens.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1a (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 348-364 (verbatim).
--
-- Refresh tokens: hashed UUIDs (BCrypt of the raw UUID, see
-- RefreshTokenService in PR4). The user_scope discriminator lets the same
-- table serve both COMPANY and PLATFORM users. user_id has no FK by design
-- (the user may live in company_users OR platform_users — application-level
-- integrity is enforced). tenant_id is NULL when user_scope = 'PLATFORM'.
-- ----------------------------------------------------------------------------

CREATE TABLE public.refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    user_scope      VARCHAR(20) NOT NULL,
    tenant_id       UUID,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    replaced_by     UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT refresh_tokens_user_scope_check CHECK (user_scope IN ('COMPANY', 'PLATFORM'))
);

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_tenant_id ON public.refresh_tokens (tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_refresh_tokens_expires_at ON public.refresh_tokens (expires_at);

-- V11__add_audit_columns_to_refresh_tokens.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / gap-refresh-tokens-audit-columns (F1 follow-up)
-- Author:   logistics-saas-team
-- Date:     2026-06-16
-- Reason:   The RefreshToken entity extends BaseEntity (the same
--           @MappedSuperclass as Role, CompanyUser, PlatformUser), which
--           declares created_by / updated_by columns. V5 only created
--           created_at (the design did not anticipate the
--           MappedSuperclass field expansion that happened between V5 and
--           V10). Hibernate generates a SELECT that includes
--           created_by and updated_by, and the query fails on
--           PostgreSQL with "column rt1_0.created_by does not exist"
--           (SQLSTATE 42703). The error surfaces on the first
--           Hibernate-managed query against the refresh_tokens table
--           — including the token-validation path in
--           RefreshTokenService.findByTokenHash (called from
--           /api/v1/auth/refresh and /api/v1/auth/logout), and the
--           RefreshToken chain-tracking CTE in
--           findChainIds / revokeChain.
--
--           Discovered via F1 end-to-end testing of the register-then-
--           login-then-refresh flow on 2026-06-15: the first /refresh
--           call after a successful login returned 500 with
--           "column rt1_0.created_by does not exist".
--
--           This migration mirrors V10 (which added the same four
--           columns to the roles table) and brings refresh_tokens
--           into alignment with the BaseEntity contract. The
--           columns are nullable because the @PrePersist hook on
--           BaseEntity only fills created_at/updated_at, not the
--           created_by/updated_by UUIDs (which require an actor
--           context that BaseEntity does not have).
-- ----------------------------------------------------------------------------

ALTER TABLE public.refresh_tokens
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

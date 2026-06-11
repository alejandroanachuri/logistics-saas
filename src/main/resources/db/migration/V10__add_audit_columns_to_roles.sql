-- V10__add_audit_columns_to_roles.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR3c (registration IT exposed the gap)
-- Author:   logistics-saas-team
-- Date:     2026-06-11
-- Reason:   The Role entity extends BaseEntity, which declares
--           @MappedSuperclass fields for updated_at, deleted_at, created_by,
--           updated_by. V2 only added created_at. The mismatch is invisible
--           to JdbcTemplate tests (RlsIntegrationIT, DswiringIT) but breaks
--           the first Hibernate-managed query against the roles table in
--           RegistrationIT.
--
--           This migration adds the four missing columns and a
--           gen_random_uuid() default on created_by so the entity can
--           INSERT a row without supplying it explicitly (the @PrePersist
--           hook on BaseEntity only fills created_at/updated_at, not
--           created_by, which is left nullable).
-- ----------------------------------------------------------------------------

ALTER TABLE public.roles
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

-- The existing seed rows (V7) have no created_by/updated_by values;
-- that's fine — both are nullable. The new columns inherit NOW() for
-- updated_at so future UPDATE statements trigger the @PreUpdate
-- timestamp refresh.

-- V13__migrate_role_to_many_to_many.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-2-usuarios / PR-1 (DB foundation)
-- Author:   logistics-saas-team
-- Date:     2026-06-18
-- Purpose:  Copy every company_users.role_id into company_user_roles, then
--           drop the role_id column. Single transaction — either the
--           INSERT succeeds AND the DROP runs, or neither does (Flyway
--           wraps the whole file in a transaction by default).
--
-- IDEMPOTENCY: the INSERT ... WHERE NOT EXISTS skips rows that already
-- have a junction entry (re-runs are a no-op). The DROP COLUMN IF EXISTS
-- makes the second half re-runnable. Together: the script is safe to run
-- against a DB where V13 already applied (it just exits cleanly).
--
-- NOTE: After V13 runs, RlsIntegrationIT.insertCompanyUser (test helper)
-- will fail because that helper uses raw SQL with the role_id column.
-- The test must be updated to insert into company_user_roles instead. This
-- is flagged in the PR-1 apply report under "Issues found" — the fix
-- happens at the start of the verify phase or in a follow-up patch.
-- ----------------------------------------------------------------------------

BEGIN;

INSERT INTO public.company_user_roles (company_user_id, role_id)
SELECT id, role_id
FROM public.company_users
WHERE role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM public.company_user_roles cur
      WHERE cur.company_user_id = public.company_users.id
        AND cur.role_id         = public.company_users.role_id
  );

ALTER TABLE public.company_users DROP COLUMN IF EXISTS role_id;

COMMIT;

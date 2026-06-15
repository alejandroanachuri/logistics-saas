-- V9__seed_reserved_slugs.sql
-- ----------------------------------------------------------------------------
-- Change:   etapa-1-registro / PR1b (database-foundation-rls)
-- Author:   logistics-saas-team
-- Date:     2026-06-10
-- Source:   PRD lines 399-415 (verbatim).
--
-- Reserved tenant slugs. A tenant cannot be created with one of these
-- (the registration service looks them up before INSERT and returns
-- RESERVED_SLUG 409). 15 entries.
-- ----------------------------------------------------------------------------

CREATE TABLE public.reserved_slugs (
    slug VARCHAR(12) PRIMARY KEY,
    reason VARCHAR(100) NOT NULL
);

INSERT INTO public.reserved_slugs (slug, reason) VALUES
    ('admin', 'reserved'),
    ('api', 'reserved'),
    ('app', 'reserved'),
    ('www', 'reserved'),
    ('system', 'reserved'),
    ('support', 'reserved'),
    ('help', 'reserved'),
    ('login', 'reserved'),
    ('register', 'reserved'),
    ('auth', 'reserved'),
    ('static', 'reserved'),
    ('public', 'reserved'),
    ('root', 'reserved'),
    ('test', 'reserved'),
    ('demo', 'reserved');

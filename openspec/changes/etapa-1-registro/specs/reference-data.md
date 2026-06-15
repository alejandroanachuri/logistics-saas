# Delta Spec: reference-data

> Capability scope (from proposal): `reference-data` — read-only catalog
> endpoints that the registration wizard and other forms consume. In v1
> this capability exposes the Argentine provinces list (24 entries)
> seeded by `V10__seed_provinces_reference.sql` (design §4.11).
>
> Source PRD: lines 1048 (province list for the wizard), 1085-1098
> (input helpers), 1508 (success criterion: 24 provinces exposed).

## Purpose

The system SHALL expose a small set of public, unauthenticated reference
endpoints that the registration wizard (and future forms) can consume to
render canonical, server-of-record option lists — starting with the 24
Argentine provinces. The wizard SHALL be able to call these endpoints
without authentication, without rate-limit budgets in v1, and with a
stable response shape that the client can cache.

## ADDED Requirements

### Requirement: Provinces Catalog Endpoint

The system SHALL expose `GET /api/v1/reference/provinces` (no auth
required) that returns the 24 Argentine provinces seeded by
`V10__seed_provinces_reference.sql` into `public.provinces`, ordered
alphabetically by `code` ascending. The response is a JSON array of
`Province` DTOs with shape `{ code: string, displayName: string }`,
where `code` is the canonical enum value and `displayName` is the
human-readable Spanish label rendered in the UI.

The endpoint is public, MUST NOT require an `access_token` or
`refresh_token` cookie, and is exempt from the v1 Bucket4j rate-limit
budgets that govern register / login / availability endpoints.

#### Scenario: Endpoint returns all 24 provinces in alphabetical order

- **GIVEN** the database has the 24 province rows seeded by
  `V10__seed_provinces_reference.sql`
- **WHEN** a client sends `GET /api/v1/reference/provinces` with no
  cookies
- **THEN** the system responds with HTTP 200
- **AND** `Content-Type` is `application/json; charset=utf-8`
- **AND** the body is a JSON array of exactly 24 elements
- **AND** the elements are ordered alphabetically by `code` ascending:
  `BUENOS_AIRES, CABA, CATAMARCA, CHACO, CHUBUT, CORDOBA, CORRIENTES,
  ENTRE_RIOS, FORMOSA, JUJUY, LA_PAMPA, LA_RIOJA, MENDOZA, MISIONES,
  NEUQUEN, RIO_NEGRO, SALTA, SAN_JUAN, SAN_LUIS, SANTA_CRUZ, SANTA_FE,
  SANTIAGO_DEL_ESTERO, TIERRA_DEL_FUEGO, TUCUMAN`
- **AND** every element has the shape `{ "code": "BUENOS_AIRES",
  "displayName": "Buenos Aires" }` (Spanish label, not the code)

#### Scenario: Province DTO uses Spanish displayName labels

- **WHEN** the client parses the response body
- **THEN** `BUENOS_AIRES.displayName` equals `"Buenos Aires"`
- **AND** `CABA.displayName` equals `"Ciudad Autónoma de Buenos Aires"`
- **AND** `SANTIAGO_DEL_ESTERO.displayName` equals
  `"Santiago del Estero"`
- **AND** `TIERRA_DEL_FUEGO.displayName` equals `"Tierra del Fuego"`
- **AND** the remaining 20 entries map 1:1 from their `code` to a
  Spanish title-case label (e.g. `CORDOBA` → `"Córdoba"`; the design
  accepts ASCII-only labels in v1 — see open_gaps for full diacritic
  coverage)

#### Scenario: No authentication or rate limit applies

- **WHEN** a client calls the endpoint without any cookies and from
  the same IP that has just exceeded the slug-availability rate limit
- **THEN** the system responds with HTTP 200 (the Bucket4j budget for
  availability endpoints does not extend to `/reference/*` in v1)
- **AND** the response does not require, read, or validate a JWT
  (no `Authorization` header, no `access_token` cookie)

### Requirement: Provinces Source of Truth

The system SHALL treat `public.provinces` (seeded by
`V10__seed_provinces_reference.sql`) as the single source of truth for
the province catalog. The endpoint SHALL query the table at request
time and SHALL NOT hardcode the 24 entries in application code. The
seed migration is idempotent and SHALL be re-runnable without
violating the `provinces_pkey` constraint.

#### Scenario: Endpoint reflects seeded rows, not a hardcoded list

- **WHEN** an integration test inserts a temporary 25th row with
  `code = "ZZZ_TEST"` into `public.provinces`
- **THEN** `GET /api/v1/reference/provinces` returns 25 elements in
  that test's transaction (proving the controller reads from the table
  rather than a constant list)
- **AND** the temporary row is rolled back at the end of the test,
  leaving the canonical 24 rows untouched

### Requirement: Provinces Response Caching Hint

The system SHALL include an HTTP `Cache-Control: public,
max-age=3600` response header on `GET /api/v1/reference/provinces` so
that the frontend `ProvincesService` (design §5.1) and any upstream
cache can avoid round-trips on subsequent renders. The wizard still
issues the request on app load and caches the result in a signal
(design §5.1, `ProvincesService.list()`); the header is a hint, not a
replacement for the client-side cache.

#### Scenario: Cache-Control header is set

- **WHEN** a client sends `GET /api/v1/reference/provinces`
- **THEN** the response includes a `Cache-Control: public, max-age=3600`
  header
- **AND** the response body is unchanged across repeated calls within
  the 1-hour window

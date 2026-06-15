# Delta Spec: tenant-me

> Capability scope (from proposal): subset of `auth-company` — the
> `GET /api/v1/tenants/me` endpoint that returns the current tenant's
> profile to an authenticated company user.
>
> Source PRD: lines 694-722 (GET /tenants/me), 1083-1090 (dashboard
> placeholder shows tenantId + slug).

## Purpose

The system SHALL let an authenticated company user read the profile of
their own tenant, and SHALL deny any attempt to read another tenant's
profile through this endpoint.

## ADDED Requirements

### Requirement: Tenant Profile Endpoint

The system SHALL expose `GET /api/v1/tenants/me` (company scope cookie
required) that returns the tenant profile of the currently authenticated
company user (PRD lines 694-722).

#### Scenario: Authenticated user reads own tenant

- **WHEN** a client sends a request with a valid `access_token` cookie
  whose JWT `scope` is `COMPANY`
- **THEN** the system responds with HTTP 200
- **AND** the body contains `{ id, slug, legalName, commercialName, cuit,
  taxType, contactEmail, contactPhone, address: { country, province,
  city, line, number, floor, apartment, postalCode }, status, createdAt }`
  matching the tenant associated with the JWT's `tid` claim

#### Scenario: Missing or invalid access token

- **WHEN** a client sends a request without an `access_token` cookie, or
  with an expired / tampered token
- **THEN** the system responds with HTTP 401

#### Scenario: Platform user cannot use company endpoint

- **WHEN** a client sends a request with an `access_token` whose JWT
  `scope` is `PLATFORM`
- **THEN** the system responds with HTTP 403
- **AND** `error.code` equals `FORBIDDEN_SCOPE` (or matches the project's
  401-envelope; see open_gaps)

### Requirement: Tenant Context is the JWT Subject

The system SHALL resolve the tenant id strictly from the authenticated
JWT's `tid` claim — never from a query parameter, path parameter, or
request body — and SHALL NOT accept a `tenantId` (or `slug`) override
in the request.

#### Scenario: No tenant override accepted

- **WHEN** a client sends a request to `/api/v1/tenants/me` with a query
  parameter `?tenantId=<other-uuid>` while authenticated as a company user
  of tenant A
- **THEN** the system responds with the profile of tenant A (the JWT
  `tid`) and MUST ignore the query parameter

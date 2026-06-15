# Delta Spec: api-client-and-interceptors

> Capability scope (from proposal): `frontend-auth-shell` (HTTP slice)
> — `HttpClient` interceptor chain, typed responses, signal-based
> `AuthStore` and `TenantStore`, error envelope mapping.
>
> Source PRD: lines 970-973 (HttpClient + interceptors, signal
> stores, Vitest), 504-505 (error envelope shape), 972 (cookie auth
> with `withCredentials`).
>
> Backend prerequisite: the error envelope `{ error: { code, message,
> details } }` from PRD line 505 is implemented across the company
> auth endpoints. The interceptor unit tests mock `HttpClient` and
> run without the backend; the store transition tests use the same
> mock layer.

## Purpose

The Angular application SHALL configure `HttpClient` with
`withInterceptors([authInterceptor, errorInterceptor])`, SHALL use
typed request/response generics on every call site, and SHALL keep
authentication and tenant state in two signal-based stores so that
components and the `authGuard` can react synchronously.

## ADDED Requirements

### Requirement: HttpClient with Interceptor Chain

The system SHALL provide `HttpClient` via
`provideHttpClient(withInterceptors([authInterceptor,
errorInterceptor]))` in `app.config.ts` (PRD line 972). Both
interceptors SHALL be functional (`HttpInterceptorFn`) and SHALL be
registered in the order `authInterceptor` first, then
`errorInterceptor`, so the auth interceptor can attach credentials
before the error interceptor maps failures.

#### Scenario: Interceptors are registered in the documented order

- **WHEN** the application bootstraps and `app.config.ts` is loaded
- **THEN** `HttpClient` is created with exactly two interceptors in
  this order: `authInterceptor`, `errorInterceptor`
- **AND** the `interceptors` static list in `app.config.ts` matches
  that order

### Requirement: authInterceptor — withCredentials

The system SHALL provide an `authInterceptor` that sets
`withCredentials: true` on every outgoing request so that the
httpOnly `access_token` and `refresh_token` cookies issued by the
backend are sent (PRD line 972, 504). The interceptor SHALL NOT
read, write, or otherwise touch the cookie values from JavaScript.

In v1, the interceptor MAY be extended to attach an `Authorization`
header in addition to cookies once a non-cookie token strategy is
introduced; this is not required in v1.

#### Scenario: Every outgoing request has withCredentials = true

- **GIVEN** the interceptor is registered
- **WHEN** a component or service calls `http.get`, `http.post`,
  `http.put`, `http.delete`, or any other `HttpClient` method
- **THEN** the outgoing request has `withCredentials: true`
  regardless of whether the caller explicitly set it

#### Scenario: Interceptor does not read the cookie jar

- **GIVEN** the interceptor is running
- **WHEN** it processes a request or a response
- **THEN** it MUST NOT call `document.cookie` and MUST NOT set
  `Authorization` headers based on stored tokens
- **AND** its only side effect on the request is the
  `withCredentials` flag

### Requirement: errorInterceptor — Envelope Mapping

The system SHALL provide an `errorInterceptor` that:

- Inspects `HttpErrorResponse.error` for the envelope
  `{ error: { code, message, details } }` (PRD line 505)
- Maps known `code` values to a stable set of user-facing messages
  (Spanish) for use by feature components
- Surfaces `error.message` from the envelope as the default
  fallback
- Re-throws an `HttpErrorResponse` (or a typed `ApiError`) with the
  parsed `code`, `message`, `details`, and the original `status`
  preserved
- Treats `status === 0` and `ProgressEvent`-shaped errors as
  `NETWORK_ERROR`
- Does NOT swallow the error — it MUST be re-thrown so the call
  site can branch on it

#### Scenario: 401 with INVALID_CREDENTIALS envelope is re-thrown with the code

- **GIVEN** the backend returns HTTP 401 with body
  `{ error: { code: "INVALID_CREDENTIALS", message: "..." } }`
- **WHEN** the response passes through the error interceptor
- **THEN** the interceptor throws an `HttpErrorResponse` whose
  `error.code === "INVALID_CREDENTIALS"`
- **AND** the original HTTP `status` (401) is preserved
- **Backend prerequisite:** `company-user-auth` and friends return
  the documented envelope.

#### Scenario: Validation error details are preserved on the error

- **GIVEN** the backend returns HTTP 400 with
  `details = { slug: "required", email: "invalid" }`
- **WHEN** the response passes through the interceptor
- **THEN** the thrown error's `error.details` equals
  `{ slug: "required", email: "invalid" }` (object identity and
  keys preserved)

#### Scenario: Network failure is normalized to NETWORK_ERROR

- **GIVEN** `HttpClient` reports an error with `status === 0` and a
  `ProgressEvent` in `error`
- **WHEN** the response passes through the interceptor
- **THEN** the thrown error's `error.code === "NETWORK_ERROR"`
- **AND** its `error.message === "No pudimos conectarnos. Reintentá"`
  (the canonical copy from PRD line 1081)

#### Scenario: 204 No Content is not treated as an error

- **GIVEN** the backend returns HTTP 204 (e.g. logout)
- **WHEN** the response passes through the interceptor
- **THEN** no error is thrown and the call site resolves normally

### Requirement: 401 Refresh-and-Retry

The system SHALL, on receiving a 401 from any request other than
`/auth/login`, `/auth/register`, `/auth/refresh`, or `/platform/*`,
attempt exactly one call to `POST /api/v1/auth/refresh` and retry
the original request once. If the refresh also responds 401, the
interceptor SHALL signal a forced logout: clear `auth.store` and
`tenant.store`, and the application SHALL navigate to `/login` with
`returnUrl` preserved.

This retry is performed by the interceptor (or by a small wrapper
service it calls) and SHALL NOT block other concurrent requests in
v1 (simpler single-flight not required; explicit gap carried into
sdd-design if contention shows up in testing).

#### Scenario: Successful refresh retries the original request

- **GIVEN** the user is authenticated and a request to
  `GET /api/v1/tenants/me` returns 401
- **WHEN** the interceptor processes the 401
- **THEN** it calls `POST /api/v1/auth/refresh` once
- **AND** if the refresh responds 200, it re-issues the original
  request and the call site sees the new response (with the new
  cookies applied)
- **Backend prerequisite:** `refresh-token-rotation` is implemented
  and returns 200 with new cookies.

#### Scenario: Failed refresh triggers forced logout

- **GIVEN** the user is authenticated and a request returns 401
- **WHEN** `POST /api/v1/auth/refresh` also responds 401
- **THEN** the interceptor clears `auth.store.isAuthenticated` and
  `auth.store.currentUser`
- **AND** clears `tenant.store.currentTenant`
- **AND** the router navigates to `/login?returnUrl=<currentUrl>`

#### Scenario: 401 on /auth/login is NOT retried

- **GIVEN** the user posts invalid credentials to
  `POST /api/v1/auth/login`
- **WHEN** the backend returns 401
- **THEN** the interceptor MUST NOT call
  `POST /api/v1/auth/refresh`
- **AND** MUST re-throw the original 401 error directly

### Requirement: Typed HttpClient Calls

The system SHALL pass generic type parameters to every `HttpClient`
method call (PRD line 970, typed reactive forms precedent). Public
service methods (e.g. `AuthService.login`, `RegisterService.submit`)
SHALL declare their `Request` and `Response` types via TypeScript
generics and SHALL NOT use `any`.

#### Scenario: Login call carries typed request and response

- **GIVEN** `AuthService.login(creds: LoginRequest)`
- **WHEN** the implementation calls `http.post`
- **THEN** the call is of the shape
  `http.post<LoginResponse, LoginRequest>(url, credds, { withCredentials: true })`
  — or a typed wrapper that compiles to the same shape
- **AND** the return type is `Observable<LoginResponse>`

#### Scenario: No `any` in the public service surface

- **WHEN** the source tree under
  `frontend/src/app/core/**` and
  `frontend/src/app/features/**` is scanned
- **THEN** no exported function or method signature uses `any` as
  a request or response type

### Requirement: AuthStore Signal State

The system SHALL provide a signal-based `AuthStore` injectable as
an Angular service (PRD lines 986, 970) that exposes:

- `currentUser = signal<AuthUser | null>(null)`
- `isAuthenticated = computed(() => this.currentUser() !== null)`
- `isLoading = signal<boolean>(false)`

The store SHALL be the single source of truth that the `authGuard`
and feature components read from. Mutations SHALL happen only through
explicit store methods (`setUser(user)`, `clear()`,
`setLoading(value)`) called from `AuthService` or the error
interceptor's forced-logout path.

#### Scenario: Login success populates the store

- **GIVEN** `AuthService.login()` resolves with a `LoginResponse`
- **WHEN** the service commits the result
- **THEN** `auth.store.currentUser()` is the response's `user`
- **AND** `auth.store.isAuthenticated()` is `true`

#### Scenario: Logout clears the store

- **GIVEN** an authenticated user triggers logout
- **WHEN** `AuthService.logout()` resolves
- **THEN** `auth.store.currentUser()` is `null`
- **AND** `auth.store.isAuthenticated()` is `false`

#### Scenario: Forced logout from the interceptor clears the store

- **GIVEN** the user is authenticated and a 401 → refresh 401
  sequence occurs
- **WHEN** the interceptor's forced-logout branch runs
- **THEN** `auth.store.currentUser()` is `null` and
  `auth.store.isAuthenticated()` is `false`

### Requirement: TenantStore Signal State

The system SHALL provide a signal-based `TenantStore` injectable as
an Angular service (PRD line 993) that exposes:

- `currentTenant = signal<Tenant | null>(null)`

The store SHALL be populated by `AuthService` after a successful
login (from `LoginResponse.user.tenantId` and `tenantSlug`) and
cleared on logout and on forced logout.

#### Scenario: Login success populates the tenant store

- **GIVEN** `AuthService.login()` resolves with `tenantId` and
  `tenantSlug` in the response
- **WHEN** the service commits the result
- **THEN** `tenant.store.currentTenant()` equals
  `{ id: tenantId, slug: tenantSlug, ... }`

#### Scenario: Logout clears the tenant store

- **GIVEN** an authenticated user triggers logout
- **WHEN** `AuthService.logout()` resolves
- **THEN** `tenant.store.currentTenant()` is `null`

### Requirement: Vitest Unit Tests for the HTTP Layer

The system SHALL include Vitest unit tests (PRD line 973) that
cover, at minimum, the following scenarios with a mocked
`HttpClient` and Angular `TestBed`:

- `authInterceptor` sets `withCredentials: true` on every request
  and does not read `document.cookie`
- `errorInterceptor` maps the documented `error.code` values
  (`INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `VALIDATION_ERROR`,
  `NETWORK_ERROR`) to the correct `ApiError` shape
- `errorInterceptor` calls `POST /api/v1/auth/refresh` exactly
  once on a 401, retries the original request on refresh success,
  and triggers forced logout on refresh failure
- `errorInterceptor` does not retry a 401 from
  `POST /api/v1/auth/login`
- `AuthStore` transitions: empty → authenticated on login success,
  authenticated → empty on logout, authenticated → empty on forced
  logout
- `TenantStore` is set on login success and cleared on logout

#### Scenario: Interceptor test asserts withCredentials on a mocked GET

- **GIVEN** a Vitest test that injects `HttpClient` and the
  `authInterceptor` into a `TestBed` with a mocked `HttpHandler`
- **WHEN** the test calls `http.get('/api/v1/auth/me')`
- **THEN** the handler's `handle` is invoked with a request whose
  `withCredentials` is `true`

#### Scenario: Error mapping test asserts the parsed code is preserved

- **GIVEN** a Vitest test that injects the `errorInterceptor` with
  a mocked handler that returns an `HttpErrorResponse` for
  `{ status: 401, error: { error: { code: "INVALID_CREDENTIALS" } } }`
- **WHEN** the test subscribes to the resulting `Observable`
- **THEN** the error is caught and its parsed `code` equals
  `"INVALID_CREDENTIALS"`

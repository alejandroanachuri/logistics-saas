# ADR-0005: Path-scoped cookies for company vs platform (vs single scope cookie)

- **Status**: Accepted (etapa-1-registro)
- **Date**: 2026-06-10
- **Deciders**: Backend lead, Frontend lead
- **Sources**: PRD lines 879-894, spec `company-user-auth` + `platform-user-auth`.

## Context

The system has two authentication scopes: `COMPANY` (tenant users) and
`PLATFORM` (internal team). They must not be cross-usable: a company user's
cookie must NOT work on a platform endpoint, and vice versa. The candidates:

1. **Single cookie name, distinguished by JWT `scope` claim** — both flows
   set `access_token`; the server reads the JWT, checks `scope` against the
   request path, and rejects mismatches with 403 `FORBIDDEN_SCOPE`.
2. **Two different cookie names** — `company_access_token` vs
   `platform_access_token`. Client sends the wrong one → no match → 401.
3. **Path-scoped cookies** — same name (`access_token`), different `Path`
   attribute. The browser only attaches the cookie to matching paths.

## Decision

**Path-scoped cookies** (option 3), with the JWT `scope` claim as a second
line of defense.

| Scope    | `access_token` `Path` | `refresh_token` `Path`       | Cookie attrs (prod)           |
|----------|-----------------------|------------------------------|-------------------------------|
| COMPANY  | `/api/v1`             | `/api/v1/auth`               | `HttpOnly; Secure; SameSite=Strict; Max-Age=900/604800` |
| PLATFORM | `/api/v1/platform`    | `/api/v1/platform/auth`      | `HttpOnly; Secure; SameSite=Strict; Max-Age=900/604800` |

Server-side: `AuthenticationFilter` (and the platform equivalent) verify that
the JWT `scope` claim matches the path prefix. Mismatch → 403 `FORBIDDEN_SCOPE`
(resolved gap 2).

In dev profile: `Secure=false` to allow `http://localhost` (resolved gap 8).

## Alternatives considered

- **Single cookie + scope claim only (option 1)**: works, but the browser
  WILL send the wrong cookie to the wrong path. The server catches it, but
  the request hits the network and exposes the token to e.g. an attacker on
  the same network path. Path scoping prevents the cookie from leaving the
  browser at all.
- **Two different cookie names (option 2)**: also works. The browser still
  sends the wrong cookie (cookie jars don't filter by name). Net effect is
  similar to option 1, plus the server has to handle two names. No advantage
  over path scoping.
- **Path-scoped + name-distinguished**: rejected. Path scoping is enough; name
  distinction adds nothing for a single issuer.

## Consequences

- **Positive**: defense in depth. (a) Browser does not even attach the wrong
  cookie. (b) Server's path-prefix check still rejects forged/curl requests.
  c) JWT `scope` claim is the canonical truth — if a bug puts both cookies
  on the same path, the JWT check still catches it.
- **Negative**: the cookie's `Path` is part of its identity. If we ever move
  endpoints across path boundaries (e.g. `GET /api/v1/platform/tenants/{id}` →
  `GET /api/v1/tenants/{id}/platform-view`), we need to remember to re-scope
  the cookies. The `CookieConfig` is centralized so this is one file to change.
- **Operational**: the `CookieWriter` (in `auth/security`) is the single point
  that sets these cookies. Test coverage: `CookiePathScopeIT` asserts that a
  `COMPANY` access token has `Path=/api/v1` and a `PLATFORM` token has
  `Path=/api/v1/platform`.

## References

- PRD lines 879-894 (cookie spec)
- Spec `company-user-auth` (login scenario, cookie header expectations)
- Spec `platform-user-auth` (cross-scope rejection)
- Design.md §1.4, §7.3

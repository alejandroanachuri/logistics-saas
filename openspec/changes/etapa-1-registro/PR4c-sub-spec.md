# Sub-Spec PR4c — Rate limit + security headers

> Source: `account-lockout-and-rate-limit.md` (rate-limit
> sections only; lockout is already covered by PR3c's
> `LoginService` and `GlobalExceptionHandler`).

## Goal

Add per-IP Bucket4j rate limits on `register`, `login`, and the
three availability endpoints, and a response-header filter that
defends against common browser-borne attacks (XSS, clickjacking,
MIME sniffing). The lockout flow itself is already wired (PR3c)
and gets a smoke assertion here.

## In scope

- `RateLimitProperties` (typed record bound to `app.rate-limit.*`):
  per-bucket budgets, enabled flag.
- `RateLimitFilter` (OncePerRequestFilter, in-memory, per-IP):
  - 5 / hour on `POST /api/v1/auth/register`
  - 10 / minute on `POST /api/v1/auth/login` AND
    `POST /api/v1/platform/auth/login`
  - 30 / minute on `GET /api/v1/tenants/me/{slug,cuit,username}-availability`
  - 429 with `Retry-After` (seconds) + `X-RateLimit-Remaining` on
    exhaustion; `RATE_LIMIT_EXCEEDED` error envelope
  - on consumption: audit row `RATE_LIMIT_EXCEEDED` (PR4c only
    audits the exhausted case, not every accepted request —
    per design §11 the "exceeded" event is the signal).
- `SecurityHeadersFilter` (OncePerRequestFilter): adds the four
  baseline security headers to every response.
- Wire both filters in `SecurityConfig.filterChain` BEFORE the
  authentication filter (rate-limit first so abusive traffic
  never reaches the auth code).
- `RateLimitIT` (raw-JDBC, no Spring context): prove the bucket
  logic in isolation by hitting the Bucket4j API directly. The
  full HTTP gate is the existing 75/75 suite.

## Out of scope

- Distributed rate limiting (Redis) — spec open_gaps.
- Anti-automation (captcha) — v2.
- The actual lockout dedicated IT (the 5-failures / 30-min flow
  is exercised by `RegistrationIT` because the gate test already
  proves the wiring; a dedicated `LockoutIT` would need a Spring
  context and has the same Testcontainers-ordering fragility
  flagged in PR4a — deferred).

## Acceptance

- `mvn verify` green: 44 unit + 16 RegistrationIT + 10
  RlsIntegrationIT + 5 DswiringIT + (new) 2 RateLimitIT all
  pass.
- The four security headers appear on every response of
  `GET /api/v1/reference/provinces` (the simplest public
  endpoint, easiest to assert without a Spring context).
- The rate-limit filter is in-memory per-process; the smoke test
  confirms the bucket reports `false` after the documented
  consumption.

## Files touched (~280 added lines)

| File | Status | LoC |
|------|--------|-----|
| `common/ratelimit/RateLimitProperties.java` | NEW | ~15 |
| `common/ratelimit/RateLimitFilter.java` | NEW | ~150 |
| `common/security/SecurityHeadersFilter.java` | NEW | ~50 |
| `config/SecurityConfig.java` | EDIT (wire both filters) | +20 |
| `test/.../common/RateLimitIT.java` | NEW | ~60 |

# ADR-0003: HS256 with static secret for v1 (vs RS256/JWKS)

- **Status**: Accepted (etapa-1-registro)
- **Date**: 2026-06-10
- **Deciders**: Backend lead, Security
- **Sources**: PRD lines 859-877, proposal decision D3.

## Context

Access tokens are JWTs. We must choose a signing algorithm and a key management
strategy for v1.

## Decision

**HS256 with a single static secret** loaded from the `JWT_SECRET` env var
(must be at least 256 bits, base64-encoded). No JWKS endpoint, no key rotation,
no asymmetric crypto.

Configuration:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-ttl-seconds: 900
    refresh-token-ttl-seconds: 604800
    issuer: "logistics-saas"
    audience: "logistics-saas-web"
```

The `JwtService` bean is a singleton; the `SecretKey` is built once from
`Base64.getDecoder().decode(secret)` and reused for both signing and
verification (jjwt 0.12 idiom, confirmed via Context7 — `Jwts.SIG.HS256` and
`Jwts.parser().verifyWith(secretKey)`).

## Alternatives considered

- **RS256 with JWKS endpoint**: rejected for v1. Pros: asymmetric, frontend
  could verify tokens without sharing secrets; rotation is easy. Cons: needs
  a key store (KMS, Vault, or even just two PEM files), needs an HTTP endpoint
  to publish public keys, needs rotation plumbing. Overkill for a single
  issuer (backend) and a single verifier (also backend).
- **ES256 (ECDSA)**: rejected. Same operational overhead as RS256, no v1
  benefit.
- **HS256 with periodic secret rotation**: deferred to v2. v1 has a single
  issuer and verifier, so the secret is the only thing that needs to change
  in lockstep, and that's already a deploy operation. Adding rotation now
  is unnecessary complexity.

## Consequences

- **Positive**: zero infrastructure dependencies for key management; one env
  var to set in dev, CI, and prod; jjwt 0.12 API is one-liner; tests can
  generate a key with `Jwts.SIG.HS256.key().build()`.
- **Negative**: secret compromise = full token forgery. Mitigated by the
  secret being at-rest in a secret manager (Railway env, GitHub Actions
  secret) and by access tokens being 15-minute-lived. Refresh tokens are
  opaque UUIDs (separate ADR-0004) and are NOT derived from `JWT_SECRET`,
  so a secret leak does not let an attacker mint refresh tokens.
- **v2 plan**: add a `kid` header to the JWT, store two HS256 secrets in
  a `Map<String, SecretKey>` keyed by `kid`, rotate by adding a new key and
  retiring the old one after `access-token-ttl-seconds` elapses. Optionally
  move to RS256 with JWKS once we have a key store.

## References

- PRD lines 859-877 (JWT spec)
- Proposal decision D3
- Context7 lookup: jjwt 0.12 `Jwts.SIG.HS256.key().build()`,
  `Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(jws)`

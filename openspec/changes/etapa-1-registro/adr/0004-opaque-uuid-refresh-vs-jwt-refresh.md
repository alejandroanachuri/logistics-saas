# ADR-0004: Refresh tokens are opaque UUIDs hashed in DB (vs JWT refresh tokens)

- **Status**: Accepted (etapa-1-registro)
- **Date**: 2026-06-10
- **Deciders**: Backend lead, Security
- **Sources**: PRD line 877, spec `refresh-token-rotation`.

## Context

Refresh tokens must be:

- Revocable server-side (logout, lockout, suspected compromise)
- Bound to a user (and optionally a tenant for company scope)
- Verifiable on every refresh call
- Cryptographically unguessable

The candidates:

1. **Opaque UUID stored as BCrypt hash in DB** — the row IS the truth. Rotation
   is a `revoked_at` update.
2. **JWT refresh tokens** — the token itself is a self-contained signed claim
   with `sub`, `exp`, `jti`. Revocation requires a denylist.
3. **Opaque random bytes (e.g. 32 bytes from `SecureRandom`) stored as a hash** —
   same shape as (1) but bigger.

## Decision

**Opaque UUID** (v4 from `SecureRandom`), persisted as BCrypt strength-12 hash
in `public.refresh_tokens.token_hash` (unique). The raw UUID is returned to the
client only via the `Set-Cookie` header. Audit logs MAY store a truncated prefix
(first 8 chars) but never the full token.

Schema recap (PRD lines 348-363):
```sql
CREATE TABLE public.refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    user_scope      VARCHAR(20) NOT NULL,
    tenant_id       UUID,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    replaced_by     UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT refresh_tokens_user_scope_check CHECK (user_scope IN ('COMPANY', 'PLATFORM'))
);
```

Rotation (on every successful refresh):
1. `UPDATE … SET revoked_at = now() WHERE id = <old.id>`
2. `INSERT INTO refresh_tokens (…, replaced_by = <old.id>)` for the new token.

Reuse detection (resolved gap 3): when a revoked token is presented, walk the
`replaced_by` chain back to find the most recent valid row, then revoke ALL
rows in that chain. See design.md §7.2.

## Alternatives considered

- **JWT refresh tokens**: rejected. Pros: stateless. Cons: cannot be revoked
  without a denylist (so you end up with a DB anyway); cannot bind to a row
  for the "revoke entire chain on reuse" pattern; do not provide a
  `replaced_by` primitive; logging them risks leaking signatures.
- **Opaque 32 bytes from SecureRandom**: equivalent to UUID v4 in entropy
  (UUID v4 has ~122 bits of entropy; 32 random bytes have 256). The UUID wins
  on ergonomics: the cookie value is typeable, loggable (with truncation),
  and the row id is also a UUID.

## Consequences

- **Positive**: single source of truth is the DB row. Revocation is a one-row
  update. Reuse detection is a clean chain walk. No need for a denylist.
- **Negative**: every refresh does a BCrypt match (intentionally slow; matches
  the password hashing strength). For v1, the refresh rate is bounded by the
  access-token TTL (15 min) so this is negligible. v2 may switch to a faster
  hash (SHA-256) since the row is already keyed by a unique index — see below.
- **Note on hash choice**: BCrypt at strength 12 is the same as our password
  hashing. This is intentional: a stolen DB row gives no advantage (the BCrypt
  is still hard to brute force). If we wanted to optimize, we'd use
  `SHA-256(token)` and rely on the unique constraint + short token TTL.
  For v1 we keep BCrypt for consistency. The `RefreshTokenRotationIT` will
  benchmark this to confirm it's not on the hot path.

## References

- PRD line 877 (opaque UUID, BCrypt-hashed)
- Spec `refresh-token-rotation`
- Design.md §7.2 (rotation + reuse chain revocation)

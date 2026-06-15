# PRD — Etapa 1: Registro de Compañía y Admin
**Producto:** Logistics SaaS (working title)
**Etapa:** 1 de N (solo registro + autenticación)
**Versión:** 1.1 (refinamiento de roles y platform users)
**Fecha:** 2026-06-10
**Audiencia:** Agente de IA de desarrollo + equipo técnico humano

## 📝 Changelog

- **v1.1** (2026-06-10): agregada tabla `roles` con scope `COMPANY`/`PLATFORM`; `users` renombrado a `company_users` con FK a `roles`; nueva tabla `platform_users`; nueva tabla `roles` con seed de 6 roles; nuevos endpoints `/api/v1/platform/*`; login de platform user con email+password; path-scoped cookies separados por scope; 3 datasources (company/system/platform) con RLS diferenciado; CLI para bootstrap del primer `PLATFORM_ADMIN`.
- **v1.0** (2026-06-10): versión inicial.

---

## 📌 Resumen ejecutivo

Construir la **Etapa 1** del producto: el flujo completo de **registro self-service de una compañía** (que crea automáticamente un usuario administrador) y el **flujo de login** usando `slug + username + password`. Sin envíos, sin couriers, sin dashboard con datos. Solo identidad.

Esta etapa valida:
- La arquitectura multi-tenant con un solo schema + RLS
- El modelo de auth (Spring Security + JWT en cookie)
- El onboarding UX (wizard de 3 pasos)
- El ciclo de CI/CD y deploy

---

## 🎯 Objetivos de la Etapa 1

| # | Objetivo | Métrica de éxito |
|---|---|---|
| O1 | Una persona puede registrarse y obtener una cuenta funcional | Flujo de registro completo en <5 min |
| O2 | El login funciona con `slug + username + password` | Login exitoso en <2s |
| O3 | El sistema aísla datos por tenant a nivel de DB | Tests E2E cruzan tenants y fallan |
| O4 | El sistema está listo para producción | Deploy en Railway + Vercel funcional |
| O5 | La verificación de email está **arquitecturada** pero **no activa** | `EmailService` interface existe, no se llama en v1 |

---

## ❌ Fuera de alcance (NO hacer)

- ❌ Integración con couriers
- ❌ Creación de envíos
- ❌ Tracking público
- ❌ Notificaciones WhatsApp/SMS
- ❌ Facturación (manual o automática)
- ❌ Integración con ARCA
- ❌ Gestión de usuarios (más allá del admin inicial)
- ❌ Subida de logo
- ❌ Subdominio personalizado
- ❌ Cambio de plan
- ❌ Dashboard con datos reales (placeholder está bien)
- ❌ 2FA
- ❌ OAuth social
- ❌ Multi-idioma
- ❌ Tema visual personalizable por tenant
- ❌ Webhooks / API pública

---

## 🏗️ Arquitectura

### Diagrama

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (Angular 21)                   │
│              https://app.[domain].com                        │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  /register   │  │   /login     │  │ /dashboard   │       │
│  │  (wizard 3)  │  │ slug+user+pw │  │ (placeholder)│       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└────────────────────────┬─────────────────────────────────────┘
                         │ HTTPS (cookie httpOnly)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Backend (Spring Boot 3.5, Java 21)              │
│              https://api.[domain].com                        │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │   Auth   │ │ Tenant   │ │  Users   │ │  Audit log   │   │
│  │ controller│ │ controller│ │  (v2)   │ │              │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘   │
│       └─────────────┴────────────┴──────────────┘           │
│                         │                                     │
│       ┌─────────────────┴────────────────┐                   │
│       │  TenantContext (ThreadLocal)     │                   │
│       │  RLS Aspect: SET LOCAL           │                   │
│       │  app.current_tenant = ?          │                   │
│       └─────────────────┬────────────────┘                   │
└─────────────────────────┼───────────────────────────────────┘
                          │ JDBC
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  PostgreSQL (Neon o similar)                  │
│                  Schema: public (single)                     │
│                                                              │
│  Tablas: tenants, users, refresh_tokens, audit_log           │
│  RLS habilitado en todas las tablas tenant-scoped            │
└─────────────────────────────────────────────────────────────┘
```

### Stack tecnológico (versiones exactas)

#### Backend
| Componente | Versión | Notas |
|---|---|---|
| Java | 21 LTS | Usar records, pattern matching, virtual threads opcional |
| Spring Boot | 3.5.x | Última estable de la línea 3.5 |
| Spring Security | 6.5.x | Incluida en Spring Boot 3.5 |
| Spring Data JPA | Incluida | Con Hibernate 6.6+ |
| Hibernate | 6.6+ | Soporte para JSONB, sequences, etc. |
| PostgreSQL Driver | 42.7.x | JDBC driver |
| Flyway | 11.x | Para migraciones |
| jjwt (JWT) | 0.12.x | API moderna, fluent |
| BCrypt | Incluida en Spring Security | Strength 12 |
| Lombok | 1.18.x | `@Getter`, `@Builder`, etc. (opcional pero recomendado) |
| MapStruct | 1.6.x | Mapeo DTO ↔ Entity (opcional, puede ser manual) |
| SpringDoc OpenAPI | 2.7.x | Swagger UI en `/swagger-ui.html` |
| Bucket4j | 8.x | Rate limiting |
| Sentry | 7.x | Errores en producción |
| Micrometer | Incluida | Métricas |
| Logback | Incluida | Logging JSON |
| Testcontainers | 1.20.x | Tests de integración con Postgres real |
| JUnit 5 | 5.11.x | Tests |
| Mockito | 5.14.x | Mocks |
| AssertJ | 3.26.x | Assertions fluidas |
| Maven | 3.9.x | Build tool |
| Spotless | 2.43.x | Formateo de código |
| Checkstyle | 10.x | Linting (opcional) |

#### Frontend
| Componente | Versión | Notas |
|---|---|---|
| Angular | 21.x | Standalone components, signals, control flow |
| TypeScript | 5.7+ | Modo `strict: true` |
| Tailwind CSS | 4.x | Con preset oficial Angular |
| RxJS | 7.8+ | Solo donde signals no alcance |
| Angular Forms | Reactive forms tipados | Forms tipados con `FormGroup<{...}>` |
| Vitest | 2.x | Tests unitarios |
| Playwright | 1.49+ | Tests E2E |
| ESLint | 9.x | Linting |
| Prettier | 3.x | Formateo |
| Angular CDK | 21.x | Para componentes accesibles (stepper, a11y) |
| date-fns | 4.x | Si se necesita formatear fechas |

#### Infraestructura
| Componente | Proveedor | Plan |
|---|---|---|
| Backend hosting | Railway | Hobby ($5/mes) o superior |
| DB Postgres | Neon | Free tier (0.5GB) |
| Frontend hosting | Vercel | Free tier |
| Almacenamiento | Cloudflare R2 | Free tier (10GB) — para v2 |
| CDN | Cloudflare | Free tier |
| Sentry | sentry.io | Free tier (5k events/mes) |
| Email | — | **NO activo en v1** |
| CI/CD | GitHub Actions | Free tier |

---

## 🗄️ Modelo de datos

### Diagrama ER

```
┌──────────────────┐
│     tenants      │
├──────────────────┤         ┌──────────────────┐         ┌──────────────────┐
│ id (UUID) PK     │ 1     N │  company_users   │ N     1 │     roles        │
│ slug (UNIQUE)    ├─────────┤ id (UUID) PK     ├─────────┤ id (UUID) PK     │
│ legal_name       │         │ tenant_id (FK)   │         │ name (UNIQUE)    │
│ cuit (UNIQUE)    │         │ role_id (FK)     │         │ scope            │
│ *_at / *_by     │         │ username         │         │   COMPANY        │
└──────────────────┘         │ email            │         │   PLATFORM       │
                             │ password_hash    │         │ description      │
                             │ *_at / *_by     │         └──────────────────┘
                             └──────────────────┘                  ▲
                                                                    │
┌──────────────────┐         ┌──────────────────┐                  │
│ platform_users   │ N     1 │     roles        ├──────────────────┘
├──────────────────┤─────────┤ (PLATFORM_*)     │
│ id (UUID) PK     │         └──────────────────┘
│ role_id (FK)     │
│ email (UNIQUE)   │         ┌──────────────────┐
│ password_hash    │ 1     N │ refresh_tokens   │
│ *_at / *_by     ├─────────┤ id, user_id      │
└──────────────────┘         │ user_scope       │
                             │   COMPANY        │
                             │   PLATFORM       │
                             │ token_hash       │
                             │ expires_at       │
                             │ revoked_at       │
                             └──────────────────┘

                             ┌──────────────────┐
                             │   audit_log      │
                             ├──────────────────┤
                             │ id, tenant_id?   │
                             │ user_id?         │
                             │ user_scope       │
                             │ event_type       │
                             │ metadata (JSONB) │
                             └──────────────────┘
```

### Tablas

#### `public.tenants`

```sql
CREATE TABLE public.tenants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(12) NOT NULL,
    legal_name          VARCHAR(255) NOT NULL,
    commercial_name     VARCHAR(255),
    cuit                VARCHAR(13) NOT NULL,
    tax_type            VARCHAR(30) NOT NULL,
    contact_email       VARCHAR(255) NOT NULL,
    contact_phone       VARCHAR(50),
    country             CHAR(2) NOT NULL DEFAULT 'AR',
    province            VARCHAR(100),
    city                VARCHAR(100),
    address_line        VARCHAR(255),
    address_number      VARCHAR(20),
    address_floor       VARCHAR(10),
    address_apartment   VARCHAR(10),
    postal_code         VARCHAR(20),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP WITH TIME ZONE,
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT tenants_slug_unique UNIQUE (slug),
    CONSTRAINT tenants_cuit_unique UNIQUE (cuit),
    CONSTRAINT tenants_tax_type_check CHECK (tax_type IN ('RESPONSABLE_INSCRIPTO', 'MONOTRIBUTO', 'EXENTO')),
    CONSTRAINT tenants_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TRIAL'))
);

CREATE INDEX idx_tenants_slug ON public.tenants (slug);
CREATE INDEX idx_tenants_cuit ON public.tenants (cuit);
CREATE INDEX idx_tenants_deleted_at ON public.tenants (deleted_at) WHERE deleted_at IS NULL;
```

#### `public.roles`

Catálogo de roles. Separados por scope: `COMPANY` (dentro de un tenant) o `PLATFORM` (cross-tenant, para el equipo interno).

```sql
CREATE TABLE public.roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50) NOT NULL,
    scope           VARCHAR(20) NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT roles_name_unique UNIQUE (name),
    CONSTRAINT roles_scope_check CHECK (scope IN ('COMPANY', 'PLATFORM'))
);
```

Seed (en `V7__seed_roles.sql`):

```sql
INSERT INTO public.roles (name, scope, description) VALUES
    ('COMPANY_ADMIN',    'COMPANY',  'Administrador de la empresa: control total dentro del tenant'),
    ('COMPANY_OPERATOR', 'COMPANY',  'Operador de oficina: crea envíos, ve reportes, no gestiona usuarios'),
    ('COMPANY_DRIVER',   'COMPANY',  'Repartidor: usa la app móvil'),
    ('COMPANY_VIEWER',   'COMPANY',  'Solo lectura'),
    ('PLATFORM_ADMIN',   'PLATFORM', 'Administrador de la plataforma: acceso total cross-tenant'),
    ('PLATFORM_SUPPORT', 'PLATFORM', 'Soporte: acceso limitado a tenants para troubleshooting');
```

#### `public.company_users`

Usuarios internos de cada empresa (clientes del SaaS). Sujeto a RLS por tenant.

```sql
CREATE TABLE public.company_users (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    role_id                     UUID NOT NULL,
    username                    VARCHAR(30) NOT NULL,
    email                       VARCHAR(255) NOT NULL,
    first_name                  VARCHAR(100),
    last_name                   VARCHAR(100),
    password_hash               VARCHAR(255) NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    email_verified              BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at           TIMESTAMP WITH TIME ZONE,
    verification_token          UUID,
    verification_token_expires_at TIMESTAMP WITH TIME ZONE,
    last_login_at               TIMESTAMP WITH TIME ZONE,
    failed_login_attempts       INT NOT NULL DEFAULT 0,
    locked_until                TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP WITH TIME ZONE,
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT company_users_tenant_fk FOREIGN KEY (tenant_id) REFERENCES public.tenants(id),
    CONSTRAINT company_users_role_fk FOREIGN KEY (role_id) REFERENCES public.roles(id),
    CONSTRAINT company_users_tenant_username_unique UNIQUE (tenant_id, username),
    CONSTRAINT company_users_tenant_email_unique UNIQUE (tenant_id, email),
    CONSTRAINT company_users_status_check CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_company_users_tenant_id ON public.company_users (tenant_id);
CREATE INDEX idx_company_users_role_id ON public.company_users (role_id);
CREATE INDEX idx_company_users_email_verified ON public.company_users (email_verified) WHERE email_verified = FALSE;
```

#### `public.platform_users`

Usuarios de la plataforma (equipo de desarrollo, soporte). NO están atados a un tenant. Sin RLS.

```sql
CREATE TABLE public.platform_users (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id                     UUID NOT NULL,
    email                       VARCHAR(255) NOT NULL,
    first_name                  VARCHAR(100),
    last_name                   VARCHAR(100),
    password_hash               VARCHAR(255) NOT NULL,
    status                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_login_at               TIMESTAMP WITH TIME ZONE,
    failed_login_attempts       INT NOT NULL DEFAULT 0,
    locked_until                TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP WITH TIME ZONE,
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT platform_users_role_fk FOREIGN KEY (role_id) REFERENCES public.roles(id),
    CONSTRAINT platform_users_email_unique UNIQUE (email),
    CONSTRAINT platform_users_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_platform_users_role_id ON public.platform_users (role_id);
```

**Nota:** los `platform_users` se crean **fuera** del flujo de self-service. Se siembran con un script CLI / endpoint admin protegido. Ver sección "Platform Admin" más abajo.

#### `public.refresh_tokens`

Tokens de refresh. Funciona para ambos tipos de usuario, distinguidos por `user_scope`.

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

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_tenant_id ON public.refresh_tokens (tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_refresh_tokens_expires_at ON public.refresh_tokens (expires_at);
```

**Nota:** `user_id` no tiene FK a `users` porque ahora son dos tablas. La integridad se valida a nivel aplicación. `tenant_id` es NOT NULL solo cuando `user_scope = 'COMPANY'`.

#### `public.audit_log`

Eventos de seguridad. Funciona para ambos tipos de usuario.

```sql
CREATE TABLE public.audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID,
    user_scope      VARCHAR(20),
    tenant_id       UUID,
    event_type      VARCHAR(50) NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_log_user_scope_check CHECK (user_scope IN ('COMPANY', 'PLATFORM') OR user_scope IS NULL)
);

CREATE INDEX idx_audit_log_tenant_id_created_at ON public.audit_log (tenant_id, created_at DESC) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_audit_log_user_id ON public.audit_log (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_log_event_type ON public.audit_log (event_type);
```

#### `public.reserved_slugs` (catálogo, no RLS)

```sql
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
```

### Row-Level Security

**Estrategia:** habilitar RLS en las tablas tenant-scoped. Usar una variable de sesión `app.current_tenant` que se setea desde Spring Boot. Usar un rol de DB con bypass para operaciones del sistema (registro, login, plataforma).

**Tablas con RLS** (aislamiento por tenant):
- `tenants`
- `company_users`
- `refresh_tokens` (filtrado por `tenant_id`)

**Tablas SIN RLS** (accesibles bajo otras reglas):
- `roles` — catálogo, lectura pública
- `platform_users` — solo accesible via endpoints platform, no via endpoints company
- `audit_log` — sin RLS directo, pero el filtro se hace a nivel aplicación por `user_scope` + `tenant_id`
- `reserved_slugs` — catálogo

#### `V8__enable_rls.sql`

```sql
-- Habilitar RLS en tablas tenant-scoped
ALTER TABLE public.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.company_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refresh_tokens ENABLE ROW LEVEL SECURITY;

-- Crear roles de DB
CREATE ROLE app_user NOINHERIT;        -- RLS forzado
CREATE ROLE app_admin NOINHERIT BYPASSRLS;  -- bypasea RLS, usado para registro/login/seed
CREATE ROLE app_platform NOINHERIT;    -- RLS forzado + acceso cross-tenant (ver policies)

-- Policies para app_user (company users, su sesión tiene app.current_tenant seteado)
CREATE POLICY tenants_isolation ON public.tenants
    FOR ALL TO app_user
    USING (id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY company_users_isolation ON public.company_users
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE POLICY refresh_tokens_isolation ON public.refresh_tokens
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid
           AND user_scope = 'COMPANY');

-- Policies para app_platform (platform users, acceden a TODO)
CREATE POLICY tenants_platform_all ON public.tenants
    FOR ALL TO app_platform
    USING (true);

CREATE POLICY company_users_platform_read ON public.company_users
    FOR SELECT TO app_platform
    USING (true);

CREATE POLICY platform_users_self ON public.platform_users
    FOR ALL TO app_platform
    USING (true);

-- Otorgar permisos
GRANT USAGE ON SCHEMA public TO app_user, app_admin, app_platform;

-- app_user: solo tablas tenant-scoped
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenants, public.company_users, public.refresh_tokens TO app_user;
GRANT SELECT ON public.roles TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- app_admin: bypasea RLS, usado para registro, login, system ops
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_admin;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_admin;

-- app_platform: acceso cross-tenant
GRANT SELECT, INSERT, UPDATE, DELETE ON public.tenants, public.company_users, public.platform_users, public.refresh_tokens, public.audit_log TO app_platform;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.roles TO app_platform;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_platform;
```

**Uso desde la app (3 DataSources):**
- `companyDataSource` — usuario `app_user`, RLS activo. Spring setea `SET LOCAL app.current_tenant = '...'` por request (extraído del JWT).
- `systemDataSource` — usuario `app_admin`, RLS bypaseado. Usado para: registro (insertar tenant + admin user), login lookup, chequeo de slug/cuit availability, verificación de tokens.
- `platformDataSource` — usuario `app_platform`, RLS cross-tenant. Usado para endpoints `/api/v1/platform/*`.
- Migraciones: usuario con permisos totales (no `app_user`/`app_admin`/`app_platform`).

---

## 🌐 API REST

### Convenciones

- Base URL: `/api/v1`
- Content-Type: `application/json`
- Auth: cookie `access_token` (httpOnly, Secure, SameSite=Strict) + cookie `refresh_token` (httpOnly, Secure, SameSite=Strict)
- Errores: JSON con estructura `{ "error": { "code": "...", "message": "...", "details": {...} } }`
- IDs: UUIDs como string
- Fechas: ISO 8601 UTC

### Endpoints

#### `POST /api/v1/auth/register`

Crea una compañía y su admin. No requiere auth.

**Request body:**
```json
{
  "company": {
    "legalName": "Mercado Vías Rápidas S.A.",
    "commercialName": "MVR",
    "cuit": "30-71234567-8",
    "taxType": "RESPONSABLE_INSCRIPTO",
    "slug": "mvr",
    "contactEmail": "contacto@mvr.com.ar",
    "contactPhone": "+5491145678900",
    "address": {
      "country": "AR",
      "province": "BUENOS_AIRES",
      "city": "CABA",
      "line": "Av. Corrientes",
      "number": "1234",
      "floor": "5",
      "apartment": "B",
      "postalCode": "C1043"
    }
  },
  "admin": {
    "username": "admin",
    "email": "juan.perez@mvr.com.ar",
    "firstName": "Juan",
    "lastName": "Pérez",
    "password": "MiPassw0rd!Seguro"
  }
}
```

**Validaciones:**
- `slug`: 2-12 chars, solo lowercase letras y números, empieza con letra, no es reserved, no existe
- `cuit`: 11 dígitos con o sin guiones, dígito verificador válido
- `email`: formato válido
- `password`: mínimo 8 chars, al menos 1 mayúscula, 1 minúscula, 1 número
- `username`: 3-30 chars, alfanumérico + `._-`, lowercase

**Response 201 Created:**
```json
{
  "tenant": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "slug": "mvr",
    "legalName": "Mercado Vías Rápidas S.A.",
    "cuit": "30712356780"
  },
  "user": {
    "id": "660e8400-e29b-41d4-a716-446655440000",
    "username": "admin",
    "email": "juan.perez@mvr.com.ar",
    "firstName": "Juan",
    "lastName": "Pérez",
    "role": "COMPANY_ADMIN"
  },
  "emailVerificationRequired": true
}
```

**Errores posibles:**
- `400 VALIDATION_ERROR` con detalles campo a campo
- `409 SLUG_ALREADY_TAKEN`
- `409 CUIT_ALREADY_REGISTERED`
- `409 RESERVED_SLUG`
- `429 RATE_LIMIT_EXCEEDED`

**Side effects:**
- Crea tenant
- Crea user admin
- Genera `verification_token` (UUID), setea `verification_token_expires_at = now() + 24h`
- **NO envía email en v1** (loggea: "TODO: send verification email to X with token Y")
- Registra evento `TENANT_REGISTERED` en audit_log
- Setea cookies de sesión vacías (sin login automático)

#### `GET /api/v1/tenants/me/slug-availability?slug=foo`

Chequea disponibilidad de slug en tiempo real. Usado por el wizard.

**Response 200:**
```json
{
  "slug": "foo",
  "available": true
}
```

**Response 200 (no disponible):**
```json
{
  "slug": "foo",
  "available": false,
  "reason": "SLUG_ALREADY_TAKEN"
}
```

#### `GET /api/v1/tenants/me/cuit-availability?cuit=30712356780`

Idem para CUIT.

**Response 200:**
```json
{
  "cuit": "30712356780",
  "available": true,
  "valid": true
}
```

#### `POST /api/v1/auth/login`

**Request body:**
```json
{
  "slug": "mvr",
  "username": "admin",
  "password": "MiPassw0rd!Seguro"
}
```

**Response 200:**
- Setea cookies `access_token` (15 min) y `refresh_token` (7 días)
- Body:
```json
{
  "user": {
    "id": "660e8400-e29b-41d4-a716-446655440000",
    "tenantId": "550e8400-e29b-41d4-a716-446655440000",
    "tenantSlug": "mvr",
    "username": "admin",
    "email": "juan.perez@mvr.com.ar",
    "firstName": "Juan",
    "lastName": "Pérez",
    "role": "COMPANY_ADMIN",
    "scope": "COMPANY"
  },
  "expiresIn": 900
}
```

**Errores:**
- `400 VALIDATION_ERROR`
- `401 INVALID_CREDENTIALS` (genérico, no revelar si es el slug, el user o el password)
- `403 ACCOUNT_LOCKED` (después de 5 intentos en 15 min)
- `403 ACCOUNT_DISABLED`
- `403 EMAIL_NOT_VERIFIED` (NO se usa en v1, queda preparado)

**Side effects:**
- Resetea `failed_login_attempts` a 0
- Setea `last_login_at = now()`
- Crea `refresh_token` con hash del token
- Loggea evento `USER_LOGIN_SUCCESS` o `USER_LOGIN_FAILED`

#### `POST /api/v1/auth/refresh`

Rota el access token usando el refresh token de la cookie.

**Request:** cookies `refresh_token`
**Response 200:** nuevas cookies + mismo body que login
**Errores:**
- `401 REFRESH_TOKEN_INVALID`
- `401 REFRESH_TOKEN_EXPIRED`
- `401 REFRESH_TOKEN_REVOKED`

**Side effects:**
- Marca el refresh_token viejo como `revoked_at = now()`
- Crea nuevo refresh_token con `replaced_by` apuntando al anterior

#### `POST /api/v1/auth/logout`

Invalida el refresh token actual y limpia las cookies.

**Request:** cookies
**Response 204 No Content**
**Side effects:**
- Marca el refresh_token como `revoked_at = now()`
- Limpia cookies
- Loggea `USER_LOGOUT`

#### `GET /api/v1/tenants/me`

Devuelve la info del tenant actual. Requiere auth.

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "slug": "mvr",
  "legalName": "Mercado Vías Rápidas S.A.",
  "commercialName": "MVR",
  "cuit": "30712356780",
  "taxType": "RESPONSABLE_INSCRIPTO",
  "contactEmail": "contacto@mvr.com.ar",
  "contactPhone": "+5491145678900",
  "address": {
    "country": "AR",
    "province": "BUENOS_AIRES",
    "city": "CABA",
    "line": "Av. Corrientes",
    "number": "1234",
    "floor": "5",
    "apartment": "B",
    "postalCode": "C1043"
  },
  "status": "ACTIVE",
  "createdAt": "2026-06-10T14:30:00Z"
}
```

#### `GET /api/v1/auth/me`

Devuelve la info del usuario autenticado actual (company user).

**Response 200:**
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440000",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantSlug": "mvr",
  "username": "admin",
  "email": "juan.perez@mvr.com.ar",
  "firstName": "Juan",
  "lastName": "Pérez",
  "role": "COMPANY_ADMIN",
  "scope": "COMPANY",
  "emailVerified": true
}
```

#### Endpoints NO implementados en v1 (preparados para v2)

- `GET /api/v1/auth/verify-email?token=...`
- `POST /api/v1/auth/resend-verification`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `POST /api/v1/auth/change-password`

Estos se mencionan en el código con `@Deprecated(forRemoval = false)` o se dejan en la OpenAPI doc con `x-implementation-status: planned`.

---

#### `POST /api/v1/platform/auth/login`

Login de un platform user (equipo de desarrollo, soporte). **No requiere slug**, solo email + password. No usa RLS — el `platformDataSource` ya está configurado con `app_platform` role.

**Request body:**
```json
{
  "email": "tunombre@tuempresa.com",
  "password": "TuPassw0rd!Interno"
}
```

**Response 200:**
- Setea cookies `access_token` y `refresh_token` con path scope `/api/v1/platform`
- Body:
```json
{
  "user": {
    "id": "770e8400-e29b-41d4-a716-446655440000",
    "email": "tunombre@tuempresa.com",
    "firstName": "Tu Nombre",
    "lastName": "Apellido",
    "role": "PLATFORM_ADMIN",
    "scope": "PLATFORM"
  },
  "expiresIn": 900
}
```

**Errores:**
- `400 VALIDATION_ERROR`
- `401 INVALID_CREDENTIALS`
- `403 ACCOUNT_DISABLED`

**Side effects:** idénticos a login de company user, con `user_scope = 'PLATFORM'` y sin tenant.

#### `POST /api/v1/platform/auth/refresh`

Rotación de refresh token para platform users. Misma lógica que `/api/v1/auth/refresh` pero para platform scope.

#### `POST /api/v1/platform/auth/logout`

Logout de platform user. Invalida el refresh token y limpia las cookies del scope platform.

#### `GET /api/v1/platform/auth/me`

Devuelve la info del platform user autenticado.

#### `GET /api/v1/platform/tenants`

Lista de todos los tenants registrados. **Solo para `PLATFORM_ADMIN` y `PLATFORM_SUPPORT`**.

**Response 200:**
```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "slug": "mvr",
      "legalName": "Mercado Vías Rápidas S.A.",
      "cuit": "30712356780",
      "status": "ACTIVE",
      "createdAt": "2026-06-10T14:30:00Z",
      "userCount": 1
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20
}
```

Soporta query params `?page=0&size=20&status=ACTIVE&search=mvr`.

#### `GET /api/v1/platform/tenants/{tenantId}`

Detalle completo de un tenant (incluye `company_users`). Solo platform admin/support.

#### `POST /api/v1/platform/users`

Crea un nuevo platform user (para tu equipo). Solo `PLATFORM_ADMIN`. Pensado para uso desde CLI / scripts de bootstrap, no desde UI.

**Request body:**
```json
{
  "email": "nuevo@tuempresa.com",
  "firstName": "Nombre",
  "lastName": "Apellido",
  "password": "Passw0rd!Inicial",
  "role": "PLATFORM_SUPPORT"
}
```

**Nota v1:** este endpoint existe, pero en la práctica los platform users se siembran con un script CLI que lee un archivo YAML. La razón: en v1 no hay UI de platform admin, y no queremos que un admin de plataforma pueda "crear más admins de plataforma" sin una decisión consciente.

---

## 🔐 Seguridad y Auth

### Hashing de passwords
- **BCrypt** con strength 12 (default Spring Security)
- Nunca loggear, nunca retornar al cliente, nunca almacenar en claro

### JWT
- **Library:** jjwt 0.12.x
- **Algoritmo:** HS256 con secret de al menos 256 bits (env var `JWT_SECRET`)
- **Access token TTL:** 15 minutos
- **Refresh token TTL:** 7 días
- **Claims del access token (company user):**
  - `sub`: company_user_id
  - `tid`: tenant_id
  - `slug`: tenant_slug (para el frontend)
  - `role`: nombre del rol (ej: `COMPANY_ADMIN`) — viene de la tabla `roles`
  - `scope`: `COMPANY`
  - `iat`, `exp`, `iss` (issuer = "logistics-saas"), `aud` (audience)
- **Claims del access token (platform user):**
  - `sub`: platform_user_id
  - `role`: nombre del rol (ej: `PLATFORM_ADMIN`)
  - `scope`: `PLATFORM`
  - `iat`, `exp`, `iss`, `aud`
- **NO incluir tenant_id en JWT de platform users** (no tienen tenant, son cross-tenant).
- **Refresh token:** UUID opaco (no JWT), almacenado hasheado (BCrypt) en DB. Disambiguado por `user_scope` (`COMPANY` o `PLATFORM`).

### Cookies
**Para company users:**
```http
Set-Cookie: access_token=<jwt>; Path=/api/v1; HttpOnly; Secure; SameSite=Strict; Max-Age=900
Set-Cookie: refresh_token=<uuid>; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800
```

**Para platform users:**
```http
Set-Cookie: access_token=<jwt>; Path=/api/v1/platform; HttpOnly; Secure; SameSite=Strict; Max-Age=900
Set-Cookie: refresh_token=<uuid>; Path=/api/v1/platform/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800
```

- **Path scopes separados** para que el navegador no mande cookies de platform a endpoints de company ni viceversa. Esto es defensa en profundidad.
- En dev (`http://localhost`): NO usar `Secure` flag (los navegadores lo rechazan)
- Configurar atributo `Domain` en prod si aplica

### CORS
- **Dev:** `http://localhost:4200` con credenciales
- **Prod:** mismo dominio (Angular y Spring Boot servidos desde el mismo origen vía Vercel + Railway con reverse proxy, o ambos detrás de Cloudflare)

### Rate limiting
- `POST /api/v1/auth/register`: 5 requests / IP / hora
- `POST /api/v1/auth/login`: 10 requests / IP / minuto
- `GET /api/v1/tenants/me/slug-availability`: 30 requests / IP / minuto
- `GET /api/v1/tenants/me/cuit-availability`: 30 requests / IP / minuto
- Implementar con Bucket4j en memoria (suficiente para MVP). Si se quiere distribuido, Redis.

### Lockout de cuenta
- Después de 5 intentos fallidos en 15 min, bloquear por 30 min
- Resetear contador en login exitoso
- Implementar en backend (no en DB constraint)

### Headers de seguridad
En todas las responses:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (prod only)
- `Content-Security-Policy: default-src 'self'; ...` (configurar según frontend)
- NO incluir `X-XSS-Protection` (deprecated)

### Auditoría
Loggear TODOS estos eventos en `audit_log`:
- `TENANT_REGISTERED`
- `USER_LOGIN_SUCCESS`
- `USER_LOGIN_FAILED` (con `metadata.reason`)
- `USER_LOGOUT`
- `TOKEN_REFRESHED`
- `ACCOUNT_LOCKED`
- `RATE_LIMIT_EXCEEDED`
- `RLS_VIOLATION_ATTEMPT` (esto último si la DB loggea rechazos)

### Email verification (preparado, no activo en v1)

**Interface:**
```java
public interface EmailService {
    void sendVerificationEmail(String to, String firstName, String verificationToken);
    void sendPasswordResetEmail(String to, String firstName, String resetToken);
    void sendWelcomeEmail(String to, String firstName);
}
```

**Implementación dev:** `NoOpEmailService` que loggea con `WARN`:
```
[EmailService] Would send verification email to=juan@mvr.com.ar token=...
```

**Implementación prod (v2):** `ResendEmailService` o `SendGridEmailService`. No implementar en v1.

**Feature flag en `application.yml`:**
```yaml
app:
  email:
    enabled: false
    from-address: "no-reply@logistica.com"
    from-name: "Logistics SaaS"
```

Si `app.email.enabled=false`, el `EmailService` bean es `NoOpEmailService` (no se llama nunca al método real, pero el código de v1 está listo para activarlo).

---

## 🎨 Frontend (Angular 21)

### Setup

- **Standalone components** (NO módulos NgModule)
- **Signals** para estado local, computed para derivados, effect para side effects
- **Control flow nuevo** (`@if`, `@for`, `@switch`) en vez de `*ngIf`, `*ngFor`
- **Reactive Forms tipados** con `FormGroup<{...}>`
- **Tailwind 4** con `@tailwindcss/postcss`
- **HttpClient** con `withInterceptors([authInterceptor, errorInterceptor])`
- **Vitest** para tests
- **Playwright** para E2E

### Estructura de carpetas (resumida)

```
frontend/src/app/
├── core/
│   ├── auth/
│   │   ├── auth.service.ts         # login(), logout(), refresh(), register()
│   │   ├── auth.guard.ts           # canActivate, canMatch
│   │   ├── auth.interceptor.ts     # inyecta credenciales, no hace nada con JWT en cookies
│   │   ├── auth.types.ts
│   │   └── auth.store.ts           # signal-based: currentUser, isAuthenticated
│   ├── http/
│   │   ├── api.service.ts          # wrapper sobre HttpClient
│   │   └── error.interceptor.ts    # mapea errores a mensajes user-friendly
│   └── tenant/
│       ├── tenant.service.ts
│       ├── tenant.types.ts
│       └── tenant.store.ts         # signal-based: currentTenant
├── features/
│   ├── home/                       # landing simple
│   │   └── home.component.ts
│   ├── register/
│   │   ├── register.component.ts   # contenedor con stepper
│   │   ├── steps/
│   │   │   ├── company-step.component.ts
│   │   │   ├── admin-step.component.ts
│   │   │   └── confirmation-step.component.ts
│   │   ├── services/
│   │   │   └── register.service.ts
│   │   └── register.types.ts
│   ├── login/
│   │   └── login.component.ts
│   └── dashboard/
│       └── dashboard.component.ts  # placeholder: "Bienvenido {user.firstName}, tu slug es {slug}"
├── shared/
│   ├── components/
│   │   ├── ui/                     # button, input, alert, stepper, form-field
│   │   └── layout/                 # auth-layout, public-layout
│   ├── pipes/                      # cuit, slug
│   └── validators/                 # cuit, slug, password, cuit-unique, slug-unique (async)
├── app.config.ts
├── app.routes.ts
└── app.component.ts
```

### Rutas (v1)

```typescript
// app.routes.ts
export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent) },
  { path: 'register', loadComponent: () => import('./features/register/register.component').then(m => m.RegisterComponent) },
  { path: 'login', loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent) },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  { path: '**', redirectTo: '' }
];
```

### Wizard de registro (3 pasos)

**Paso 1: Datos de la compañía**
- Razón social (texto)
- Nombre comercial (opcional)
- CUIT (texto, validación en vivo del dígito verificador)
- Tipo de contribuyente (select: RI / MT / Exento)
- Slug (texto, validación async contra `/tenants/me/slug-availability`, con debounce 300ms)
- Email de contacto
- Teléfono (opcional)
- Dirección completa (país default AR, provincia select con 24 opciones, ciudad, calle, número, piso, depto, CP)

**Paso 2: Datos del admin**
- Nombre
- Apellido
- Username (validación async contra disponibilidad, debounce 300ms)
- Email personal
- Password + confirmación
- Indicador de fortaleza de password (zxcvbn o reglas simples)

**Paso 3: Confirmación**
- Resumen de los datos
- Checkbox: "Acepto los términos y condiciones" (link a TBD)
- Checkbox: "Acepto la política de privacidad" (link a TBD)
- Botón "Crear cuenta"

**Post-registro:**
- Mensaje de éxito: "Cuenta creada. Iniciá sesión con tu slug, usuario y contraseña."
- Link al login
- **NO** auto-login en v1 (decisión de producto: el usuario hace el login consciente)

### Pantalla de login

- Input: Slug (placeholder: "mvr")
- Input: Usuario
- Input: Password (con toggle show/hide)
- Botón: "Ingresar"
- Link: "¿No tenés cuenta? Registrate"
- Link: "¿Olvidaste tu contraseña?" (deshabilitado en v1 con tooltip "Próximamente")

**Estados de error:**
- Credenciales inválidas: "Las credenciales no son correctas"
- Cuenta bloqueada: "Demasiados intentos. Probá de nuevo en X minutos"
- Error de red: "No pudimos conectarnos. Reintentá"

### Dashboard (placeholder)

- "Bienvenido, {firstName}"
- "Tu slug es: {slug}"
- "Tu tenant ID es: {tenantId}"
- Botón "Cerrar sesión"

NO hace nada más. Es solo para confirmar que el flujo end-to-end funciona.

### Tailwind 4 setup

`tailwind.config.js` (o config CSS-first en Tailwind 4):
```js
// postcss.config.js
module.exports = {
  plugins: {
    '@tailwindcss/postcss': {},
    autoprefixer: {},
  }
}
```

```css
/* styles.css */
@import "tailwindcss";
```

Tema custom mínimo:
- Colores primarios: blue-600 (CTA), slate-900 (texto), slate-500 (subtexto)
- Border radius: rounded-lg default
- Sin modo oscuro en v1

### Accesibilidad
- Todos los inputs con `<label for>`
- Errores con `aria-describedby` y `aria-invalid`
- Foco visible (Tailwind default)
- Contraste WCAG AA
- Wizard navegable con teclado (Enter avanza, Backspace no)

---

## 🛡️ Platform Admin (preparado, no UI en v1)

El modelo de platform users existe desde v1, pero la **interfaz para administrarlos** queda para v2. Esto es por una razón de seguridad: en v1 la única forma de crear un `PLATFORM_ADMIN` es mediante un script CLI controlado por el equipo de desarrollo, no desde la web.

### Bootstrap del primer PLATFORM_ADMIN

**Opción A: Script CLI** (recomendado)

```bash
# Una vez desplegado el backend
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.main.web-application-type=none
# o
java -jar backend.jar --spring.main.web-application-type=none

# En otra terminal
./mvnw exec:java -Dexec.mainClass="ar.com.logistics.platform.cli.CreatePlatformUserCli" \
    -Dexec.args="--email=tunombre@tuempresa.com --firstName=Tu --lastName=Nombre --password=... --role=PLATFORM_ADMIN"
```

El CLI:
- Lee los args
- Usa el `systemDataSource` (app_admin, bypass RLS)
- Crea el `platform_users` con password hasheado (BCrypt)
- Asigna el rol `PLATFORM_ADMIN`
- Imprime confirmación

**Opción B: Seed en migración Flyway** (solo dev)

`V9__seed_dev_platform_users.sql` (solo corre en perfil `dev`):
```sql
INSERT INTO public.platform_users (id, role_id, email, first_name, last_name, password_hash, status)
SELECT '00000000-0000-0000-0000-000000000001',
       (SELECT id FROM public.roles WHERE name = 'PLATFORM_ADMIN'),
       'dev@localhost',
       'Dev',
       'User',
       '$2a$12$<bcrypt-hash-de-dev123>',
       'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM public.platform_users WHERE email = 'dev@localhost');
```

⚠️ **NUNCA** commitear passwords en el repo. Para dev local, dejar el archivo .sql con un seed que el dev completa localmente o usar el CLI.

### Endpoints de platform admin

Documentados en la sección "API REST > Endpoints" arriba. En v1 existen como backend pero sin UI.

### Política de platform users

- Un `PLATFORM_ADMIN` puede ver **toda la data** de cualquier tenant (con fines de soporte)
- Un `PLATFORM_SUPPORT` tiene los mismos permisos de lectura pero **NO puede** crear/modificar tenants ni impersonar
- Toda acción de platform user queda en `audit_log` con `user_scope = 'PLATFORM'`
- En v2: rate limiting específico para platform endpoints (anti-enumeration), alertas si un platform user accede a muchos tenants en poco tiempo

### ⚠️ Para v1, NO incluir en la UI

- ❌ No hay página de login para platform users en el frontend
- ❌ No hay dashboard de platform
- ❌ No hay endpoint público para crear platform users (solo CLI con credenciales de DB)

El acceso a platform endpoints en v1 es solo con `curl`/Postman, después de loguearse con un user creado por CLI.

---

## 📂 Estructura del monorepo

```
logistics-saas/
├── README.md
├── .gitignore
├── .editorconfig
├── docker-compose.yml              # para dev local
├── docs/
│   ├── ARCHITECTURE.md
│   ├── ADR/                        # Architectural Decision Records
│   ├── API.md                      # generado desde OpenAPI
│   └── RUNBOOK.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile                  # multi-stage
│   ├── .mvn/wrapper/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/ar/com/logistics/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       ├── application-prod.yml
│   │   │       ├── db/migration/
│   │   │       │   ├── V1__create_tenants.sql
│   │   │       │   ├── V2__create_users.sql
│   │   │       │   ├── V3__create_refresh_tokens.sql
│   │   │       │   ├── V4__create_audit_log.sql
│   │   │       │   ├── V5__create_reserved_slugs.sql
│   │   │       │   └── V6__enable_rls.sql
│   │   │       └── logback-spring.xml
│   │   └── test/
│   └── target/
├── frontend/
│   ├── package.json
│   ├── angular.json
│   ├── tsconfig.json
│   ├── tailwind.config.js
│   ├── postcss.config.js
│   ├── playwright.config.ts
│   ├── vitest.config.ts
│   ├── src/
│   └── e2e/
└── .github/
    └── workflows/
        ├── backend-ci.yml
        └── frontend-ci.yml
```

> **⚠️ Estructura de paquetes actualizada** (post-cambio de roles): El árbol de arriba refleja la versión anterior. La estructura real que debe generar el agente es la siguiente, organizada por dominio de negocio:
>
> **Backend (`backend/src/main/java/ar/com/logistics/`):**
> - `LogisticsApplication.java`
> - `common/` — exception handling, validation (CUIT, slug, password), audit, response wrappers
> - `config/` — `SecurityConfig`, `DataSourceConfig` (3 datasources: company, system, platform), `OpenApiConfig`, `AppProperties`
> - `tenant/` — `TenantContext` (ThreadLocal), `TenantInterceptor`, `RlsAspect`, `RlsContext`
> - `auth/` — todo lo de `/api/v1/auth/*` (company users)
>   - `controller/`, `service/`, `domain/` (`CompanyUser`, `RefreshToken`), `repository/`, `dto/`
> - `tenant/` (paquete) — todo lo de `/api/v1/tenants/*` (company)
> - `platform/` — todo lo de `/api/v1/platform/*` (platform users)
>   - `auth/` — login, refresh, logout
>   - `controller/` — tenants, users
>   - `service/`, `domain/` (`PlatformUser`), `repository/`, `dto/`
>   - `cli/` — `CreatePlatformUserCli` (command-line runner)
> - `shared/` — `BaseEntity` con campos de auditoría
>
> **Migraciones Flyway (`backend/src/main/resources/db/migration/`):**
> - V1: tenants
> - V2: roles
> - V3: company_users
> - V4: platform_users
> - V5: refresh_tokens (con user_scope)
> - V6: audit_log (con user_scope)
> - V7: seed_roles
> - V8: enable_rls (crea roles `app_user`, `app_admin`, `app_platform`)
> - V9: seed_reserved_slugs
> - V10: seed_dev_platform_users (solo perfil dev, opcional)

### Convenciones de código

#### Backend (Java)
- **Paquete base:** `ar.com.logistics`
- **Naming:**
  - Clases: PascalCase
  - Métodos/variables: camelCase
  - Constantes: UPPER_SNAKE_CASE
  - Tablas DB: snake_case, plural (`tenants`, `users`)
  - Columnas: snake_case
- **Capas:** controller → service → repository → entity
- **DTOs separados de entities**
- **Inmutabilidad:** usar records donde se pueda
- **Null safety:** nunca retornar null desde un service, usar `Optional<T>` o lanzar excepción
- **Excepciones:** usar `BusinessException` base con códigos; `@ControllerAdvice` global
- **Tests:** todos los services tienen unit tests; controllers tienen integration tests con Testcontainers
- **Lombok:** permitido para `@Getter`, `@Builder`, `@RequiredArgsConstructor`. NO para `@Data` ni `@AllArgsConstructor`
- **MapStruct:** opcional para mapeo DTO ↔ entity. Si no, hacerlo manual con métodos estáticos `toDto()` en el entity

#### Frontend (TypeScript)
- **Naming:**
  - Componentes: PascalCase (`LoginComponent`)
  - Selectores: `app-login` (kebab-case)
  - Servicios: `AuthService` (no prefijo)
  - Archivos: `kebab-case.ts` o `PascalCase.ts` para componentes
  - Variables/métodos: camelCase
  - Constantes: UPPER_SNAKE_CASE
  - Tipos: PascalCase, interfaces sin prefijo `I`
- **Standalone components siempre** (no NgModules)
- **Signals sobre RxJS** cuando se pueda
- **HttpClient con tipado fuerte**: `http.get<LoginResponse>(url)`
- **Errores:** nunca ignorar, propagar al `ErrorHandler` global
- **Tests:** unit tests con Vitest para services y signals, E2E con Playwright para flujos completos

---

## ⚙️ Configuración y entorno

### `application.yml` (base)

```yaml
spring:
  application:
    name: logistics-saas
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway maneja el schema
    open-in-view: false
    properties:
      hibernate:
        format_sql: false
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

app:
  jwt:
    secret: ${JWT_SECRET}                  # mínimo 256 bits, base64
    access-token-ttl-seconds: 900          # 15 min
    refresh-token-ttl-seconds: 604800      # 7 días
    issuer: "logistics-saas"
    audience: "logistics-saas-web"
  security:
    rate-limit:
      register-per-hour: 5
      login-per-minute: 10
    lockout:
      max-failed-attempts: 5
      window-minutes: 15
      lockout-minutes: 30
  email:
    enabled: false
    from-address: "no-reply@logistica.local"
    from-name: "Logistics SaaS"
  cors:
    allowed-origins:
      - http://localhost:4200
  rls:
    enabled: true

server:
  port: 8080
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    root: INFO
    ar.com.logistics: DEBUG
    org.springframework.security: INFO
    org.hibernate.SQL: WARN
```

### Variables de entorno (prod)

```env
# Database
DATABASE_URL=jdbc:postgresql://...neon.../logistics?sslmode=require
DATABASE_USER=app_user
DATABASE_PASSWORD=<secret>

# JWT
JWT_SECRET=<base64-256-bit>

# CORS
CORS_ALLOWED_ORIGINS=https://app.logistica.com

# Frontend (en Vercel)
VITE_API_URL=https://api.logistica.com
```

### Setup local con docker-compose

```yaml
# docker-compose.yml
version: '3.9'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: logistics
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    command: >
      postgres
      -c log_statement=all
      -c log_min_duration_statement=200ms

volumes:
  postgres_data:
```

### Comandos de setup

```bash
# Backend
cd backend
./mvnw spring-boot:run
# o
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm start    # ng serve en :4200

# Tests
cd backend && ./mvnw test
cd frontend && npm test         # vitest
cd frontend && npm run e2e      # playwright
```

---

## 🧪 Testing

### Backend

**Unit tests:**
- `SlugValidatorTest`: casos válidos, inválidos, reservados
- `CuitValidatorTest`: algoritmo de dígito verificador con varios CUITs (válidos e inválidos)
- `PasswordValidatorTest`: política de complejidad
- `AuthServiceTest`: registro, login, refresh, logout (con mocks)
- `JwtServiceTest`: generación, validación, expiración

**Integration tests con Testcontainers:**
- `RegisterFlowIT`: registro completo de tenant + admin, validaciones de DB
- `LoginFlowIT`: login exitoso, fallido, lockout
- `RlsIntegrationIT`: ⚠️ **CRÍTICO** — crea 2 tenants, intenta acceder a datos cruzados, debe fallar
- `RefreshTokenRotationIT`: refresh rota correctamente, viejo queda revocado
- `RateLimitingIT`: triggers de rate limit

**Cobertura objetivo:** >80% en services, 100% en validators.

### Frontend

**Unit tests con Vitest:**
- `SlugValidator`: validación síncrona y async
- `CuitValidator`: dígito verificador
- `PasswordValidator`: política
- `AuthService`: login, logout, refresh, signals
- `RegisterComponent`: cambios de paso, validaciones

**E2E con Playwright:**
- `register.spec.ts`: wizard completo, validaciones en vivo, errores
- `login.spec.ts`: login exitoso, credenciales inválidas, lockout
- `authenticated-flow.spec.ts`: register → logout → login → dashboard

### Definition of Done (v1)

**Funcional:**
- [ ] Una persona puede registrarse via wizard, completar los 3 pasos, y ver el mensaje de éxito
- [ ] Una persona puede loguearse con `slug + username + password` y ver el dashboard placeholder
- [ ] Un usuario no autenticado es redirigido a `/login` si intenta acceder a `/dashboard`
- [ ] Una persona no puede usar un slug reservado (`admin`, `api`, etc.)
- [ ] Una persona no puede usar un CUIT ya registrado
- [ ] Una persona no puede usar un email ya registrado para el mismo tenant
- [ ] Una persona no puede usar un username ya registrado para el mismo tenant
- [ ] Un login con credenciales inválidas devuelve error genérico (no revela qué campo está mal)
- [ ] Después de 5 intentos fallidos, la cuenta se bloquea por 30 min
- [ ] El logout limpia las cookies y el refresh token queda revocado
- [ ] El access token se refresca correctamente con el refresh token
- [ ] El refresh token se rota (uno nuevo, el viejo revocado)
- [ ] El access token expira a los 15 min
- [ ] Un platform user puede loguearse con `email + password` y obtener cookies con scope `PLATFORM`
- [ ] Un platform user NO puede acceder a endpoints de company (`/api/v1/auth/me`, `/api/v1/tenants/me`)
- [ ] Un company user NO puede acceder a endpoints de platform (`/api/v1/platform/*`)
- [ ] Un platform admin puede listar todos los tenants via `GET /api/v1/platform/tenants`
- [ ] Un platform admin puede ver el detalle de cualquier tenant via `GET /api/v1/platform/tenants/{id}`
- [ ] El primer company user creado en el registro tiene el rol `COMPANY_ADMIN`
- [ ] Los 6 roles están sembrados en la tabla `roles` al levantar la app

**Técnico:**
- [ ] Todos los tests pasan en CI
- [ ] Cobertura backend >80% en services
- [ ] Cobertura frontend >70% en services
- [ ] RLS activo en todas las tablas tenant-scoped
- [ ] Test de RLS cruza 2 tenants y falla el segundo intento
- [ ] Migraciones Flyway corren sin errores desde cero
- [ ] El sistema se deploya a Railway y Vercel con éxito
- [ ] Sentry captura errores en prod
- [ ] Logs estructurados JSON
- [ ] OpenAPI spec generado y accesible en `/swagger-ui.html`
- [ ] Rate limiting activo en `/auth/register` y `/auth/login`
- [ ] Headers de seguridad configurados

**Compliance (preparación, no activación):**
- [ ] `EmailService` interface existe con método `sendVerificationEmail`
- [ ] `NoOpEmailService` activo cuando `app.email.enabled=false`
- [ ] `verification_token` se genera y persiste al registrar
- [ ] El campo `email_verified` está en la tabla users
- [ ] La política de privacidad está linkeada en el form de registro (URL placeholder está bien)
- [ ] DPA mencionado en README (no implementado en código, decisión de producto)

---

## 📦 Entregables finales

1. **Código fuente** en repositorio Git
2. **README.md** con:
   - Setup local paso a paso
   - Cómo correr tests
   - Cómo deployar
   - Decisiones de arquitectura principales
3. **OpenAPI spec** servido en `/swagger-ui.html`
4. **Colección Postman/Insomnia** exportada
5. **Deploy funcionando** en Railway (backend) + Vercel (frontend) + Neon (DB)
6. **Capturas de pantalla** del wizard de registro, login, dashboard
7. **Video demo** de 5 min mostrando el flujo end-to-end

---

## 🚧 Riesgos identificados

| Riesgo | Mitigación |
|---|---|
| El agente genera código inconsistente entre sí | Convenciones claras + ESLint + Spotless + revisión manual |
| El agente no entiende la arquitectura RLS | Sección dedicada con SQL exacto + explicación del flujo |
| RLS mal configurado filtra datos entre tenants | Test E2E obligatorio que cruza tenants |
| El agente no implementa el rate limiting | Test de integración dedicado |
| El agente no rota el refresh token | Test específico de rotación |
| El agente "mejora" el scope agregando features | Sección "OUT of scope" prominente + checklist al final |

---

## 📋 Checklist para el agente

- [ ] Leí el PRD completo antes de empezar
- [ ] Pregunté cualquier duda antes de generar código
- [ ] No agregué features fuera del scope definido
- [ ] No inventé tablas, columnas o endpoints que no estén en el PRD
- [ ] Implementé TODOS los items de "Definition of Done"
- [ ] Todos los tests pasan
- [ ] El deploy funciona end-to-end
- [ ] Documenté desviaciones de este PRD (si las hubo) en `docs/ADR/`

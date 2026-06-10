# Backend — Logistics SaaS

Spring Boot 4.0.6 / Java 21 / Maven backend for the Logistics SaaS
Etapa 1 deliverable. Implements company registration, admin login,
platform admin login, and the row-level security foundation.

## Build

```bash
# All commands assume you are in this directory (backend/).

./mvnw -B verify                 # full build: compile + tests + JaCoCo
./mvnw -B -DskipTests package    # jar only
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Environment variables

| Variable                | Required | Example                                                              | Notes                                                  |
|-------------------------|----------|----------------------------------------------------------------------|--------------------------------------------------------|
| `DATABASE_URL`          | yes      | `jdbc:postgresql://localhost:5432/logistics`                         | JDBC URL of the Postgres instance                      |
| `DATABASE_USER`         | yes      | `migrator` / `app_user` / `app_admin` / `app_platform`               | Role to connect as. PR1 splits into 3 DataSources      |
| `DATABASE_PASSWORD`     | yes      | `<role-password>`                                                    | Role password                                          |
| `JWT_SECRET`            | yes      | `openssl rand -base64 32`                                            | Base64-encoded, **≥256 bits** (32 bytes)               |
| `SPRING_PROFILES_ACTIVE`| no       | `dev` (default) or `prod`                                            | Profile selector                                       |
| `SENTRY_DSN`            | no       | `https://...@sentry.io/123`                                          | Sentry DSN; empty disables Sentry                      |
| `LOG_FILE`              | no       | `logs/app.log`                                                       | Path of the rolling file appender                      |

A working `.env.example` is committed at the repo root. Copy it to
`.env` (or export each variable in your shell) and update
`JWT_SECRET` before starting the app.

## Project layout

```
src/main/java/ar/com/logistics/
├── BackendApplication.java          — @SpringBootApplication main
├── common/                          — exception handling, validation (PR2)
├── config/                          — security, DataSource, OpenAPI (PR1+)
├── tenant/                          — TenantContext, RLS aspect (PR1)
├── shared/                          — BaseEntity (PR1)
├── auth/                            — company-user auth (PR4)
├── platform/                        — platform-user auth (PR7)
└── ...                              — see design.md §2.1 for full map

src/main/resources/
├── application.yml                  — base config
├── application-dev.yml              — local dev overrides
├── application-prod.yml             — production overrides
└── logback-spring.xml               — JSON in prod, plain text in dev
```

## Testing

```bash
./mvnw test                         # unit + integration tests
./mvnw verify                       # adds JaCoCo coverage report
```

Unit tests use JUnit 5 + Mockito + AssertJ. Integration tests
(Testcontainers-based, name suffix `IT`) boot a real Postgres 16
container per test class. The first IT lands in PR1
(`MigrationsApplyIT`).

## Hikari connection pools

The base `application.yml` declares a single DataSource with
`maximum-pool-size: 5`. PR1 splits this into three DataSources
(`companyDataSource` / `systemDataSource` / `platformDataSource`),
each capped at 5 connections, for a total of 15 connections to
the database. This fits a 2 vCPU / 4 GB Postgres without saturating
the default `max_connections=100`.

## License

Proprietary — internal use only.

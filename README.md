# Logistics SaaS

Self-service multi-tenant logistics platform — Etapa 1 (company
registration + admin login + platform admin login).

This repository is a **logical monorepo** with two independent Git
repos side by side:

- `backend/`  — Spring Boot 4.0.6 / Java 21 / Maven
- `frontend/` — Angular 21 / bun 1.2.9 / Vitest 4 / Tailwind 4

Each sub-repo has its own `.git`, its own CI workflow, and its own
PR chain. Root files (`docker-compose.yml`, `.editorconfig`,
`.gitignore`, `.github/workflows/`) are the shared infra layer.

---

## Quick start

```bash
# 1. Boot Postgres 16 with the three RLS roles pre-created
docker compose up -d postgres

# 2. Backend
cd backend
cp .env.example .env   # set JWT_SECRET
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend
cd frontend
bun install
bun start
```

Then open <http://localhost:4200/>.

---

## Layout

```
logistics-saas/
├── backend/                                # Spring Boot 4.0.6 + Java 21
├── frontend/                               # Angular 21 + bun + Vitest 4
├── docker/initdb/01-create-roles.sql       # Postgres bootstrap
├── docker-compose.yml                      # Postgres 16-alpine + volume
├── openspec/changes/etapa-1-registro/      # SDD change folder
│   ├── proposal.md
│   ├── design.md
│   ├── tasks.md
│   ├── specs/                              # 14 delta spec files
│   ├── adr/                                # 5 ADRs
│   └── CHANGELOG.md
├── prd-etapa-1-registro.md                 # Product Requirements Document
└── .github/workflows/
    ├── backend-ci.yml
    └── frontend-ci.yml
```

---

## Environment variables

| Variable                    | Required | Description                                        |
|-----------------------------|----------|----------------------------------------------------|
| `DATABASE_URL`              | yes      | `jdbc:postgresql://localhost:5432/logistics`       |
| `DATABASE_USER`             | yes      | `app_user` / `app_admin` / `app_platform` (per DS) |
| `DATABASE_PASSWORD`         | yes      | Role password                                      |
| `JWT_SECRET`                | yes      | Base64, **≥256 bits** (32 bytes)                   |
| `SPRING_PROFILES_ACTIVE`    | no       | `dev` (default) or `prod`                          |
| `SENTRY_DSN`                | no       | Sentry DSN (prod only)                             |

Full reference at `backend/README.md`.

---

## Testing

```bash
cd backend && ./mvnw verify          # unit + integration + JaCoCo
cd frontend && bun run test          # Vitest unit tests
cd frontend && bun run e2e           # Playwright E2E
```

---

## Documentation

- [PRD](./prd-etapa-1-registro.md)
- [Change proposal](./openspec/changes/etapa-1-registro/proposal.md)
- [Architecture design](./openspec/changes/etapa-1-registro/design.md)
- [ADRs](./openspec/changes/etapa-1-registro/adr/)
- [Spec catalog](./openspec/changes/etapa-1-registro/specs/)

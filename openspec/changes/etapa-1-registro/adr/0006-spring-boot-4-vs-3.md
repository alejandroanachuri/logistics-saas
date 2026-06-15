# ADR-0006: Spring Boot 4 as the backend runtime (vs Spring Boot 3)

- **Status**: Accepted (etapa-1-registro)
- **Date**: 2026-06-13
- **Deciders**: Backend lead
- **Sources**: `backend/pom.xml` (parent: `spring-boot-starter-parent:4.0.6`); dev
  boot log ("Spring Boot v4.0.6, Spring v7.0.7"); F1 end-to-end
  integration testing 2026-06-13.

## Context

The PRD does not pin a Spring Boot major version. The repo's
`backend/pom.xml` declares `spring-boot-starter-parent:4.0.6` as
the parent POM, which transitively brings:

- Spring Framework 7.0.7
- Hibernate ORM 7.2.12.Final
- Flyway 11.14.1 (with `flyway-database-postgresql` as a
  separate module)
- Jackson 3 (`tools.jackson.databind` — the package renamed
  from `com.fasterxml.jackson` in Jackson 3.x)
- Tomcat 11.0.21
- A `Spring Boot 4`-aware starter family where some 3rd-party
  libraries still publish 3.x-only starters (e.g. Sentry 7's
  `sentry-spring-boot-starter` is unaligned with Spring Boot 4's
  `AutoConfiguration.imports` registration convention — see
  the in-boot warning).

SB4 landed in the repo at PR0 (chore/bootstrap-repos) and
has been the runtime for every backend PR since (PR1 through
PR5b). The choice was not re-litigated at any PR boundary;
the parent version was inherited as a baseline assumption.

The decision became salient during F1 end-to-end integration
testing (PR10/PR12 frontend + local backend), which surfaced
two regressions specific to SB4:

1. **Flyway auto-configuration silently disabled when
   multiple DataSources are present.** In SB3, the
   auto-config picked the first (or `@Primary`) DataSource
   and ran Flyway against it. In SB4, when the project
   declares three DataSources (`companyDataSource`,
   `platformDataSource`, `systemDataSource` — see
   ADR-0002 for why), Flyway auto-config is disabled entirely
   with no log line. The V1..V10 migrations never run, and
   every endpoint that touches a table returns 500 with
   `relation "<table>" does not exist`.
2. **`sentry-spring-boot-starter` prints an "Incompatible
   Spring Boot Version" warning at boot.** Sentry 7.x was
   published before SB4 GA and its auto-config registration
   does not match SB4's `AutoConfiguration.imports` file
   convention. The warning is non-fatal; Sentry is a no-op
   when no DSN is set, so the dev env is unaffected, but
   the warning clutters the boot log.

The dev environment (F1 integration testing) requires
both fixes to run end-to-end. The Flyway fix is a non-trivial
code change in `SystemDataSourceConfig.java`; the Sentry
warning is a known-noisy log line.

## Decision

**Stay on Spring Boot 4.0.6.** Do not downgrade to 3.x.

The two SB4-specific regressions are isolated, fixable, and
have local fixes. The SB3 downgrade path was evaluated and
rejected on cost grounds (see below).

## Alternatives considered

- **Downgrade to Spring Boot 3.4.x** (the last 3.x stable).
  - Pros: the Flyway auto-config and Sentry starter would
    work out of the box without code workarounds; the
    library ecosystem is more mature; documentation and
    community resources are more abundant.
  - Cons: PR0..PR5b all targeted SB4 APIs and assumptions
    (e.g. the multi-DataSource wiring in `SystemDataSourceConfig`,
    `CompanyJpaConfig`, `PlatformJpaConfig` may rely on SB4's
    auto-config changes for @EnableJpaRepositories targeting;
    the `tools.jackson.databind` import in the backend is
    SB4-only — Jackson 3). A downgrade is a `pom.xml`
    bump + likely 1-3 days of testing and de-SB4-ifying
    any SB4-only calls. There is no functional or business
    reason to downgrade.
  - Rejected.

- **Stay on Spring Boot 4.0.6 (chosen)**.
  - Pros: matches the current repo state; no PR rebases;
    no risk of regressions in already-shipped PRs; the
    two known SB4-specific issues are localised and have
    documented fixes (this ADR + the Flyway code fix in
    `SystemDataSourceConfig` + the Sentry DSN-disabled
    behavior in dev).
  - Cons: the `sentry-spring-boot-starter` boot warning
    is noisy; the Flyway fix in `SystemDataSourceConfig`
    is a hand-rolled auto-config replacement that future
    Spring Boot upgrades could break; some 3rd-party
    libraries will lag SB4 support until they publish
    SB4-aware starters.
  - Chosen.

## Consequences

- **Positive**: no migration cost; the team gets to stay on
  the latest Spring stack; the two known regressions are
  documented and fixed.
- **Negative**: every future SB4 upgrade needs a smoke test
  that boots the app with three DataSources and verifies
  Flyway runs (the Sentry warning is harmless and can be
  suppressed in the prod logging config if it becomes
  noisy in practice). Any 3rd-party starter that requires
  Spring Boot 3 will need an SB4-aware replacement or a
  hand-rolled config (same pattern as the Flyway fix here).
- **Operational**: the `SystemDataSourceConfig.systemFlyway`
  bean is a single point of Flyway wiring; future changes
  (e.g. switching to Flyway 12, or adding a 4th DataSource)
  must update this bean. The `sentry-spring-boot-starter`
  warning can be filtered in `logback-spring.xml` with
  `<logger name="io.sentry" level="OFF"/>` if the dev boot
  log noise becomes problematic.

## Follow-up work

- The Sentry boot warning is documented but not suppressed.
  If it becomes a recurring concern, add
  `<logger name="io.sentry.spring.boot.SentrySpringVersionChecker"
  level="OFF"/>` (or the equivalent logger name) to
  `src/main/resources/logback-spring.xml`. Not blocking.
- The `SystemDataSourceConfig` fix is the first instance of
  a hand-rolled replacement for SB4 auto-config removal. If
  more auto-config removals show up in SB4 minor releases
  (4.1, 4.2, ...), consider extracting a `BackendAutoConfiguration`
  class that wires all the missing pieces in one place,
  rather than scattering fixes across individual `@Configuration`
  files.

## References

- `backend/pom.xml` (parent POM declaration)
- `backend/src/main/java/ar/com/logistics/config/SystemDataSourceConfig.java`
  (the Flyway explicit-bind fix; commit `9f70316` on
  `feat/etapa-1-registro-pr3-tenant-registration`)
- `docker/initdb/01-create-roles.sql` (the companion
  `GRANT CREATE` initdb fix; workspace-only, not under
  version control)
- `openspec/changes/etapa-1-registro/CHANGELOG.md` (the
  F1 end-to-end integration fixes entry)
- engram observation #122 (apply-progress) +
  `sdd/etapa-1-registro/integration-lessons` topic_key
- ADR-0002 (three DataSources vs single — the architectural
  choice that triggered the Flyway auto-config regression
  in the first place)

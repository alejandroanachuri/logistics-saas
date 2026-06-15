# Frontend — Logistics SaaS

Angular 21 / bun 1.2.9 / Vitest 4 / Tailwind 4 frontend for the
Logistics SaaS Etapa 1 deliverable. Implements the public marketing
page, the 3-step company registration wizard, the login form, and
the authenticated dashboard placeholder.

## Development server

```bash
bun install
bun start   # ng serve on http://localhost:4200
```

The dev server expects the backend to be running on
`http://localhost:8080` (start it from `../backend`).

## Build

```bash
bun run build                  # production build into dist/
bun run watch                  # development build with watch mode
```

## Testing

```bash
bun run test                   # Vitest unit tests (jsdom)
bun run test:coverage          # with v8 coverage
bun run e2e                    # Playwright E2E (chromium only)
bun run e2e:install            # first-time: install Playwright browsers
bun run e2e:ui                 # interactive Playwright UI mode
```

## Linting and formatting

```bash
bun run lint                   # eslint over .ts and .html
bun run lint:fix               # autofix what's safe
bun run format                 # prettier --write
bun run format:check           # CI check
```

## Stack

- **Framework**: Angular 21 (standalone components, signals, new control flow)
- **Package manager**: bun 1.2.9
- **Unit tests**: Vitest 4 + jsdom
- **E2E tests**: Playwright (chromium only in v1)
- **Styling**: Tailwind 4 (CSS-first via `@import "tailwindcss"`)
- **Lint**: ESLint 9 flat config + typescript-eslint + angular-eslint
- **Format**: Prettier 3
- **Form helpers**: `@angular/cdk` (Stepper for the registration wizard)
- **Password strength**: `@zxcvbn-ts/core`

## Project layout

```
src/
├── app/
│   ├── core/                  — auth/http/tenant services, interceptors
│   ├── features/              — home, register, login, dashboard
│   ├── shared/                — components, validators, data
│   ├── app.config.ts          — providers (router, http, zoneless)
│   ├── app.routes.ts          — route table
│   └── app.component.ts       — <router-outlet>
├── styles.css                 — Tailwind 4 entry
└── main.ts                    — bootstrapApplication
e2e/                           — Playwright specs (added in PR11/PR12)
playwright.config.ts           — Playwright configuration
eslint.config.js               — ESLint 9 flat config
```

## Tailwind 4

This project uses **CSS-first Tailwind 4** — there is intentionally
**no `tailwind.config.js`**. Theme tokens are defined inside
`src/styles.css` using `@theme { --color-primary-600: ...; }` per
the [Tailwind 4 docs](https://tailwindcss.com/docs/theme).

PostCSS is configured via `.postcssrc.json` (committed) with the
`@tailwindcss/postcss` plugin.

## License

Proprietary — internal use only.

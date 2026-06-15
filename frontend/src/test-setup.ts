/**
 * Vitest setup file for the Angular 21 frontend.
 *
 * The Angular CLI's {@code @angular/build:unit-test} builder
 * already injects an {@code init-testbed.js} virtual file
 * that calls {@code getTestBed().initTestEnvironment(...)} with
 * {@code platformBrowserTesting()} (per the builder's
 * {@code build-options.js}). We do NOT call
 * {@code initTestEnvironment} again here — that would throw
 * "TestBed already initialized".
 *
 * The file exists as a placeholder for:
 * - any future global test setup (e.g. zone.js polyfills, mock
 *   matchers, custom expect extensions)
 * - a contract surface for the orchestrator's PRs that may
 *   need a hand-written TestBed init in case the builder's
 *   auto-injection is bypassed (e.g. running the specs through
 *   the vitest CLI directly, not through ng test)
 *
 * Importing the Angular testing module here is enough to
 * satisfy a "did the test infrastructure load?" assertion and
 * ensures the file is part of the test bundle.
 */
import '@angular/core/testing';

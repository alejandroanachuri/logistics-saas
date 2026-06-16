import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, shareReplay, tap, map, catchError, throwError } from 'rxjs';

/**
 * Wire shape for the {@code GET /api/v1/reference/provinces}
 * endpoint. The backend serves 24 AR provinces (PR2b
 * {@code V10__seed_provinces_reference.sql} and the
 * {@code reference-data} spec); the wizard's province
 * {@code <select>} renders them as
 * {@code <option value={code}>{name}</option>}.
 */
export interface Province {
  /** ISO-style provincial code, e.g. {@code AR-B} for Buenos Aires. */
  code: string;
  /** Human-readable province name in Spanish, e.g. {@code Buenos Aires}. */
  displayName: string;
}

/**
 * Typed wrapper over the public province reference endpoint
 * with a small in-memory cache. The province list is a static
 * reference dataset (24 AR provinces seeded by V10) that
 * rarely changes, so the cache lives for the SPA session and
 * is NOT refreshed. Concurrent subscribers share the first
 * in-flight request via {@code shareReplay(1)}.
 *
 * <p>The {@code authInterceptor} already attaches
 * {@code withCredentials: true} to every request; this
 * service does NOT pass credentials options explicitly. The
 * endpoint is publicly accessible per the backend
 * {@code SecurityConfig} (no auth required), so a 401 will
 * never fire in practice; if it ever does the
 * {@code errorInterceptor} will refresh-and-retry as usual.
 *
 * <p>Error surface: a failed HTTP call (network, 4xx, 5xx)
 * is reflected in two ways:
 * <ol>
 *   <li>The {@code list()} observable errors out — subscribers
 *       can react via their own error handler.
 *   <li>The {@code error()} signal is set to a Spanish user-
 *       facing message; the company-step component reads it
 *       to render a visible error banner with a "Reintentar"
 *       button. v1 of this service silently swallowed the
 *       error (left the list empty) which made the province
 *       dropdown render as empty with no user-visible cue
 *       that the backend was unreachable.
 * </ol>
 */
@Injectable({ providedIn: 'root' })
export class ProvincesService {
  private readonly http = inject(HttpClient);

  private cache: Province[] | null = null;

  /**
   * Last error message for the most recent {@code list()}
   * call, or {@code null} if the last call succeeded or has
   * not yet been made. Read by the company-step component to
   * render the error banner + "Reintentar" button.
   */
  readonly error = signal<string | null>(null);

  /**
   * {@code GET /api/v1/reference/provinces}. Returns the
   * in-memory cache on every call after the first; the
   * first subscriber triggers exactly one HTTP request.
   *
   * <p>The backend wraps the array in {@code { data: [...] }}
   * (a forward-compatible envelope per the {@code reference-data}
   * spec). The service destructures the envelope before
   * caching so subscribers always see a plain {@code Province[]}.
   *
   * <p>On HTTP failure (network error, 4xx, 5xx) the
   * observable re-throws the error AND the {@code error}
   * signal is set to a Spanish user-facing message. The
   * cache is left untouched so a subsequent {@code refresh()}
   * starts from a clean state.
   */
  list(): Observable<Province[]> {
    if (this.cache !== null) {
      return of(this.cache);
    }
    return this.http.get<{ data: Province[] }>('/api/v1/reference/provinces').pipe(
      tap(() => this.error.set(null)),
      tap((envelope) => (this.cache = envelope.data)),
      map((envelope) => envelope.data),
      catchError((err: unknown) => {
        this.error.set(this.toUserMessage(err));
        return throwError(() => err);
      }),
      shareReplay(1),
    );
  }

  /**
   * Drop the cache and clear the error state. The next
   * {@code list()} call triggers a fresh HTTP request. Used
   * by the company-step's "Reintentar" button when the
   * first load failed.
   */
  refresh(): Observable<Province[]> {
    this.cache = null;
    this.error.set(null);
    return this.list();
  }

  /**
   * Convenience lookup against the cached list. Returns
   * {@code undefined} (not throwing) when the code is
   * unknown — callers can branch on the undefined case
   * without a try/catch.
   */
  getByCode(code: string): Province | undefined {
    return this.cache?.find((p) => p.code === code);
  }

  /**
   * Best-effort mapping of an unknown error to a short
   * Spanish user-facing message. The full diagnostic stays
   * in the console (caller's logging layer can pick it up
   * from the rethrown error); the banner only needs a
   * phrase the user can act on.
   */
  private toUserMessage(err: unknown): string {
    if (err && typeof err === 'object') {
      const e = err as { status?: number; error?: { error?: { code?: string } } };
      if (e.status === 0) {
        return 'No pudimos cargar las provincias. Revisá tu conexión.';
      }
      if (e.status && e.status >= 500) {
        return 'El servicio de provincias no está disponible. Probá de nuevo en unos minutos.';
      }
    }
    return 'No pudimos cargar las provincias. Probá de nuevo.';
  }
}

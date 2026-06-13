import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, shareReplay, tap } from 'rxjs';

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
 */
@Injectable({ providedIn: 'root' })
export class ProvincesService {
  private readonly http = inject(HttpClient);

  private cache: Province[] | null = null;

  /**
   * {@code GET /api/v1/reference/provinces}. Returns the
   * in-memory cache on every call after the first; the
   * first subscriber triggers exactly one HTTP request.
   */
  list(): Observable<Province[]> {
    if (this.cache !== null) {
      return of(this.cache);
    }
    return this.http.get<Province[]>('/api/v1/reference/provinces').pipe(
      tap((list) => (this.cache = list)),
      shareReplay(1),
    );
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
}

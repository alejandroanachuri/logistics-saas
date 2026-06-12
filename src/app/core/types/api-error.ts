/**
 * Canonical error envelope shape returned by every backend
 * endpoint (PRD line 505, `ErrorEnvelope.java` in the
 * backend). Mirrored here so the {@code errorInterceptor}
 * can parse a 4xx / 5xx body and re-throw a typed
 * {@code HttpErrorResponse} that the call site can branch on.
 *
 * The interceptor:
 * - parses {@code error: { code, message, details }} if present
 * - maps known codes to canonical Spanish copy
 * - falls back to {@code error.message} from the envelope
 * - normalises {@code status === 0} (network failure) to
 *   {@code NETWORK_ERROR}
 */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, string>;
}

export interface ApiErrorEnvelope {
  error: ApiErrorBody;
}

/**
 * The shape of an {@code HttpErrorResponse} after the
 * {@code errorInterceptor} has parsed the envelope. The
 * interceptor preserves the original {@code status} from the
 * server response and attaches the parsed envelope at
 * {@code error} so call sites can do
 * {@code (err.error as ApiErrorEnvelope).error.code}.
 *
 * <p>{@code headers} is optional because v1 of the
 * {@code errorInterceptor} does not yet project response
 * headers onto the rethrown error. Call sites that need
 * response headers (e.g. {@code LoginService} reading
 * {@code Retry-After} for {@code ACCOUNT_LOCKED} minutes)
 * type-narrow with a {@code { get(k): string | null } }
 * minimal contract; a future PR will surface the full
 * {@code HttpHeaders} from the interceptor.
 */
export interface ApiHttpError {
  status: number;
  statusText: string;
  url?: string;
  error: ApiErrorEnvelope | { code: 'NETWORK_ERROR'; message: string };
  headers?: { get(name: string): string | null };
}

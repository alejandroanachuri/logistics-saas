import { HttpInterceptorFn } from '@angular/common/http';

/**
 * {@code authInterceptor} — sets {@code withCredentials: true}
 * on every outgoing request so the httpOnly {@code access_token}
 * and {@code refresh_token} cookies issued by the backend are
 * sent (PRD line 972, 504). The interceptor does NOT read,
 * write, or otherwise touch the cookie values from JavaScript;
 * it only flips the credentials flag.
 *
 * <p>Wired first in the chain (per
 * {@code api-client-and-interceptors.md}) so the credentials
 * flag is set before the {@code errorInterceptor} sees the
 * request. There is no auto-attached {@code Authorization}
 * header in v1 — the backend spec is cookie-only.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const cloned = req.clone({ withCredentials: true });
  return next(cloned);
};

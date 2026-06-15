import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RegisterRequest, RegisterResponse } from '../types';

/**
 * Typed wrapper over the public tenant registration
 * endpoint. The wizard (PR12) is the only call site; this
 * service is a thin boundary so the wizard can subscribe to
 * a typed {@code Observable<RegisterResponse>} without
 * scattering the URL across the component.
 *
 * <p>Registration does NOT log the user in (the spec
 * explicitly disallows it: the wizard shows a success
 * screen and the user clicks "Ir a iniciar sesión" to land
 * on {@code /login}).
 */
@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);

  /**
   * {@code POST /api/v1/auth/register} with the full
   * typed payload (company + admin + consent).
   */
  submit(payload: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>('/api/v1/auth/register', payload);
  }
}

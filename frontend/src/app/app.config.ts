import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/api/auth-interceptor';
import { errorInterceptor } from './core/api/error-interceptor';
import { AuthRehydrator } from './core/bootstrap/auth-rehydrator';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    // etapa-2-usuarios follow-up: rehydrate auth from session cookies
    // before the router evaluates guards. Without this, cold-booting
    // directly to /dashboard/team (or any deep link) redirects to
    // /login because both guards see an empty _currentUser.
    provideAppInitializer(() => inject(AuthRehydrator).rehydrate()),
  ],
};

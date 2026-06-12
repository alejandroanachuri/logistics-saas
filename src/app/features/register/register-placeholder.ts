import { Component } from '@angular/core';

/**
 * Placeholder for the F1 register wizard. The real wizard
 * (CDK stepper with Company + Admin + Consent) lands in PR12;
 * this component keeps the {@code /register} route reachable
 * in PR10 so the public shell renders end-to-end.
 */
@Component({
  selector: 'app-register-placeholder',
  standalone: true,
  template: `
    <main class="w-full max-w-md">
      <section class="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 class="text-2xl font-semibold text-slate-900">Registrar empresa</h1>
        <p class="mt-2 text-sm text-slate-600">Wizard coming in PR12</p>
      </section>
    </main>
  `,
})
export class RegisterPlaceholderComponent {}

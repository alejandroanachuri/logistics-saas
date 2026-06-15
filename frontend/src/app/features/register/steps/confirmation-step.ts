import { ChangeDetectionStrategy, Component, Signal, computed, input } from '@angular/core';
import {
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { AdminFormGroup } from './admin-step';
import { CompanyFormGroup } from './company-step';

/**
 * Typed shape of the step 3 consent form. The consents are
 * the only fields the user actually edits on this step —
 * the company + admin summaries are read-only.
 */
export type ConfirmationFormGroup = FormGroup<{
  acceptsTerms: FormControl<boolean>;
  acceptsPrivacy: FormControl<boolean>;
}>;

/**
 * Step 3 of the F1 register wizard. Renders a read-only
 * summary of the company data (step 1) + admin user data
 * (step 2) + 2 consent checkboxes. The submit button is
 * NOT part of this step's template — the stepper drives
 * the {@code submit()} call (the brief mandates
 * "the button is the stepper's, not the step's").
 *
 * <p>Standalone Angular 21 component — no
 * {@code NgModule}. Lives in the future stepper as
 * {@code <app-confirmation-step [companyForm]="companyForm"
 * [adminForm]="adminForm"></app-confirmation-step>}.
 *
 * <p>Summary rendering strategy: the template binds
 * the form values through computed signals derived from
 * the {@code companyForm} / {@code adminForm} signal
 * inputs (Angular 21 {@code InputSignal<T>} — see PR12a
 * Discovery #24). The consents are owned by this
 * component's own {@code form} group; the stepper reads
 * {@code this.form.value} on submit to populate the
 * {@code RegisterRequest.acceptsTerms} /
 * {@code RegisterRequest.acceptsPrivacy} fields.
 *
 * <p>Security: the template NEVER renders the
 * {@code password} or {@code passwordConfirmation} values.
 * The {@code adminForm} signal input is read for
 * {@code firstName}, {@code lastName}, {@code username},
 * and {@code email} only.
 */
@Component({
  selector: 'app-confirmation-step',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './confirmation-step.html',
})
export class ConfirmationStepComponent {
  /**
   * Signal input for the company form (owned by
   * {@code CompanyStepComponent}, surfaced by the
   * stepper via {@code @ViewChild}). Required —
   * {@code input.required<FormGroup>()}. The template
   * reads the form values through the derived
   * {@code companySummary} computed.
   */
  readonly companyForm = input.required<CompanyFormGroup>();

  /**
   * Signal input for the admin form (owned by
   * {@code AdminStepComponent}, surfaced by the
   * stepper via {@code @ViewChild}). Required.
   */
  readonly adminForm = input.required<AdminFormGroup>();

  /**
   * Local consent form — the only mutable state on this
   * step. Both controls use {@code Validators.requiredTrue}
   * (the consent must be explicit, not just truthy).
   */
  readonly form: ConfirmationFormGroup;

  /**
   * Computed projection of the company form's value into
   * the render shape the template needs. Re-evaluates
   * whenever the company form's value changes (signal
   * change detection).
   *
   * <p>The shape is intentionally flat + label-rich so
   * the template can render it with a single
   * {@code @for} loop.
   */
  readonly companySummary: Signal<ReadonlyArray<{ label: string; value: string }>>;

  /**
   * Computed projection of the admin form's value. The
   * {@code password} / {@code passwordConfirmation} fields
   * are EXCLUDED — the brief mandates the summary must
   * never echo a password back.
   */
  readonly adminSummary: Signal<ReadonlyArray<{ label: string; value: string }>>;

  constructor(fb: NonNullableFormBuilder) {
    this.form = fb.group({
      acceptsTerms: fb.control(false, { validators: [Validators.requiredTrue] }),
      acceptsPrivacy: fb.control(false, { validators: [Validators.requiredTrue] }),
    }) as ConfirmationFormGroup;

    this.companySummary = computed(() => {
      // `input.required<T>()` throws if the bound value
      // is missing. The first change-detection cycle
      // runs BEFORE the template binding has been
      // applied, so we guard the access.
      const form = (this.companyForm() ?? null) as { value?: Record<string, unknown> } | null;
      const v = (form?.value ?? {}) as Record<string, unknown>;
      const addr = (v['address'] ?? {}) as Record<string, unknown>;
      return [
        { label: 'Razón social', value: (v['legalName'] as string) ?? '' },
        { label: 'Nombre comercial', value: (v['commercialName'] as string) ?? '' },
        { label: 'CUIT', value: (v['cuit'] as string) ?? '' },
        { label: 'Tipo de contribuyente', value: (v['taxType'] as string) ?? '' },
        { label: 'Slug', value: (v['slug'] as string) ?? '' },
        { label: 'Email de contacto', value: (v['contactEmail'] as string) ?? '' },
        { label: 'Teléfono de contacto', value: (v['contactPhone'] as string) ?? '' },
        {
          label: 'Domicilio',
          value: `${(addr['line'] as string) ?? ''} ${(addr['number'] as string) ?? ''}, ${(addr['city'] as string) ?? ''}`,
        },
      ];
    });

    this.adminSummary = computed(() => {
      // Same null-guard rationale as companySummary.
      const form = (this.adminForm() ?? null) as { value?: Record<string, unknown> } | null;
      const v = (form?.value ?? {}) as Record<string, unknown>;
      // SECURITY: NEVER include password or passwordConfirmation.
      return [
        { label: 'Nombre', value: (v['firstName'] as string) ?? '' },
        { label: 'Apellido', value: (v['lastName'] as string) ?? '' },
        { label: 'Usuario', value: (v['username'] as string) ?? '' },
        { label: 'Email', value: (v['email'] as string) ?? '' },
      ];
    });
  }
}

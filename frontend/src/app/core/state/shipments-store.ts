import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ShipmentsService } from '../services/shipments.service';
import { BranchesService } from '../services/branches.service';
import { ServiceLevelsService } from '../services/service-levels.service';
import {
  Branch,
  CreateShipmentRequest,
  CreateShipmentResponse,
  PageResponse,
  ServiceLevel,
  ShipmentDetail,
  ShipmentListFilters,
  ShipmentSummary,
  TrackingEvent,
  UpdateShipmentRequest,
} from '../types';

export type WizardStep = 1 | 2 | 3;

export interface ShipmentPaginationState {
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

/**
 * ShipmentsStore — signal-based state for the shipment
 * management pages + the create-shipment wizard (etapa-3-envios
 * PR-5). Mirrors the {@code CompanyUsersStore} pattern: signals
 * for derived reads, methods for actions.
 *
 * <p>The store holds four logical groups of state:
 * <ol>
 *   <li>List + pagination + filters — for the shipment list
 *       page.</li>
 *   <li>Detail + timeline — for the shipment detail page.</li>
 *   <li>Catalogs — branches + service levels, fetched once at
 *       wizard mount.</li>
 *   <li>Wizard state — current step (1, 2, or 3) + accumulated
 *       create-request draft. Cleared on submit + on cancel.</li>
 * </ol>
 *
 * <p>Error policy: on failure, the relevant signal keeps its
 * previous value (so the UI does not flash empty during an
 * intermittent failure) and the error is rethrown to the
 * caller. Callers catch and surface the localized copy from
 * the {@code errorInterceptor}.
 */
@Injectable({ providedIn: 'root' })
export class ShipmentsStore {
  private readonly shipmentsService = inject(ShipmentsService);
  private readonly branchesService = inject(BranchesService);
  private readonly serviceLevelsService = inject(ServiceLevelsService);

  // List state
  private readonly _currentShipments = signal<ShipmentSummary[] | null>(null);
  private readonly _pagination = signal<ShipmentPaginationState>({
    page: 1,
    size: 20,
    total: 0,
  });
  private readonly _listFilters = signal<ShipmentListFilters>({});
  private readonly _isLoading = signal<boolean>(false);

  // Detail + timeline state
  private readonly _currentShipment = signal<ShipmentDetail | null>(null);
  private readonly _currentTimeline = signal<TrackingEvent[]>([]);
  private readonly _isLoadingDetail = signal<boolean>(false);

  // Catalog state
  private readonly _branches = signal<Branch[]>([]);
  private readonly _serviceLevels = signal<ServiceLevel[]>([]);
  private readonly _isLoadingCatalogs = signal<boolean>(false);

  // Wizard state
  private readonly _wizardStep = signal<WizardStep>(1);
  private readonly _wizardData = signal<Partial<CreateShipmentRequest>>({});
  private readonly _isSubmittingWizard = signal<boolean>(false);

  // List reads
  readonly currentShipments = this._currentShipments.asReadonly();
  readonly pagination = this._pagination.asReadonly();
  readonly listFilters = this._listFilters.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();

  // Detail reads
  readonly currentShipment = this._currentShipment.asReadonly();
  readonly currentTimeline = this._currentTimeline.asReadonly();
  readonly isLoadingDetail = this._isLoadingDetail.asReadonly();

  // Catalog reads
  readonly branches = this._branches.asReadonly();
  readonly serviceLevels = this._serviceLevels.asReadonly();
  readonly isLoadingCatalogs = this._isLoadingCatalogs.asReadonly();

  // Wizard reads
  readonly wizardStep = this._wizardStep.asReadonly();
  readonly wizardData = this._wizardData.asReadonly();
  readonly isSubmittingWizard = this._isSubmittingWizard.asReadonly();

  /** Convenience flag: the list has loaded at least one page
   * and is currently empty. The shipment-list page uses this
   * to decide between {@code <app-data-table>} and
   * {@code <app-empty-state>}. */
  readonly isListEmpty = computed(
    () => !this._isLoading() && (this._currentShipments()?.length ?? 0) === 0,
  );

  // ----- List actions -----------------------------------------------------

  /** Fetch a page of shipments and update the list + pagination
   * signals on success. */
  async loadList(filters: ShipmentListFilters): Promise<void> {
    this._listFilters.set({
      status: filters.status,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo,
      search: filters.search,
    });
    this._isLoading.set(true);
    try {
      const page: PageResponse<ShipmentSummary> = await firstValueFrom(
        this.shipmentsService.list(filters),
      );
      this._currentShipments.set(page.data);
      this._pagination.set({ page: page.page, size: page.size, total: page.total });
    } finally {
      this._isLoading.set(false);
    }
  }

  // ----- Detail + timeline actions ---------------------------------------

  /** Fetch a single shipment detail and update the
   * currentShipment signal. */
  async loadDetail(id: string): Promise<ShipmentDetail> {
    this._isLoadingDetail.set(true);
    try {
      const detail = await firstValueFrom(this.shipmentsService.get(id));
      this._currentShipment.set(detail);
      return detail;
    } finally {
      this._isLoadingDetail.set(false);
    }
  }

  /** Fetch the event history for a shipment (or package). */
  async loadTimeline(id: string): Promise<void> {
    const events = await firstValueFrom(this.shipmentsService.getTimeline(id));
    this._currentTimeline.set(events);
  }

  /** Update a shipment (only valid for PRE_ALTA). Returns the
   * refreshed detail so the caller does not need a follow-up
   * GET. */
  async update(id: string, req: UpdateShipmentRequest): Promise<ShipmentDetail> {
    const detail = await firstValueFrom(this.shipmentsService.update(id, req));
    this._currentShipment.set(detail);
    return detail;
  }

  /** Transition a shipment PRE_ALTA → CREADO via the
   * /validate endpoint. Returns the refreshed detail. */
  async validate(id: string): Promise<ShipmentDetail> {
    const detail = await firstValueFrom(this.shipmentsService.validate(id));
    this._currentShipment.set(detail);
    return detail;
  }

  /** Transition a shipment PRE_ALTA → CANCELADO via the
   * /reject endpoint with a rejection reason. Returns the
   * refreshed detail. */
  async reject(id: string, reason: string): Promise<ShipmentDetail> {
    const detail = await firstValueFrom(this.shipmentsService.reject(id, reason));
    this._currentShipment.set(detail);
    return detail;
  }

  /** Transition a shipment any → CANCELADO via the /cancel
   * endpoint. ADMIN only. Returns the refreshed detail. */
  async cancel(id: string, reason: string): Promise<ShipmentDetail> {
    const detail = await firstValueFrom(this.shipmentsService.cancel(id, reason));
    this._currentShipment.set(detail);
    return detail;
  }

  /** Clear the cached detail + timeline (used by the
   * shipment-detail / timeline pages when navigating away). */
  clearDetail(): void {
    this._currentShipment.set(null);
    this._currentTimeline.set([]);
  }

  // ----- Catalog actions --------------------------------------------------

  /** Fetch the branch + service-level catalogs. Idempotent —
   * callers may invoke this on every wizard mount; the second
   * call simply re-sets the same arrays. */
  async loadCatalogs(): Promise<void> {
    this._isLoadingCatalogs.set(true);
    try {
      const [branches, serviceLevels] = await Promise.all([
        firstValueFrom(this.branchesService.list()),
        firstValueFrom(this.serviceLevelsService.list()),
      ]);
      this._branches.set(branches);
      this._serviceLevels.set(serviceLevels);
    } finally {
      this._isLoadingCatalogs.set(false);
    }
  }

  // ----- Wizard actions ---------------------------------------------------

  /** Move the wizard forward by one step (no-op at step 3). */
  wizardNext(): void {
    const step = this._wizardStep();
    if (step < 3) {
      this._wizardStep.set((step + 1) as WizardStep);
    }
  }

  /** Move the wizard backward by one step (no-op at step 1). */
  wizardBack(): void {
    const step = this._wizardStep();
    if (step > 1) {
      this._wizardStep.set((step - 1) as WizardStep);
    }
  }

  /** Jump to a specific step. Used by the stepper UI for
   * direct navigation between completed steps. */
  wizardGoToStep(step: WizardStep): void {
    this._wizardStep.set(step);
  }

  /** Merge partial data into the wizard draft. The wizard
   * forms use this to accumulate step data without losing
   * earlier steps. */
  wizardPatchData(patch: Partial<CreateShipmentRequest>): void {
    this._wizardData.update((current) => ({ ...current, ...patch }));
  }

  /** Submit the wizard draft. On success the response is
   * returned so the caller can route to the detail page, and
   * the wizard state is reset. On failure the wizard state is
   * preserved so the user can retry without re-entering
   * data. */
  async wizardSubmit(): Promise<CreateShipmentResponse> {
    this._isSubmittingWizard.set(true);
    try {
      const req = this._wizardData() as CreateShipmentRequest;
      const resp = await firstValueFrom(this.shipmentsService.create(req));
      this.wizardReset();
      return resp;
    } finally {
      this._isSubmittingWizard.set(false);
    }
  }

  /** Reset the wizard state (step + draft + submitting flag).
   * Called on cancel, on successful submit, and on initial
   * wizard mount. */
  wizardReset(): void {
    this._wizardStep.set(1);
    this._wizardData.set({});
    this._isSubmittingWizard.set(false);
  }
}

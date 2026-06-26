import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { ShipmentsStore } from './shipments-store';
import { ShipmentsService } from '../services/shipments.service';
import { BranchesService } from '../services/branches.service';
import { ServiceLevelsService } from '../services/service-levels.service';
import {
  Branch,
  CreateShipmentResponse,
  PageResponse,
  ServiceLevel,
  Shipment,
  ShipmentDetail,
  ShipmentSummary,
  TrackingEvent,
  UpdateShipmentRequest,
} from '../types';

function makeSummary(id: string): ShipmentSummary {
  return {
    id,
    trackingId: `LGST-${id.toUpperCase()}`,
    code: null,
    status: 'PRE_ALTA',
    senderName: 'Juan Pérez',
    receiverName: 'Acme SA',
    totalWeightKg: 1.5,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

function makeShipment(id: string): Shipment {
  return {
    id,
    trackingId: `LGST-${id.toUpperCase()}`,
    code: null,
    shipmentType: 'NORMAL',
    senderId: 'c1',
    receiverId: 'c2',
    deliveryAddressId: 'a1',
    originBranchId: 'b1',
    destinationBranchId: 'b2',
    serviceLevelId: 's1',
    paymentType: 'PAGO_ORIGEN',
    deliveryMode: 'DOMICILIO',
    deliveryInstructions: null,
    status: 'PRE_ALTA',
    promisedDeliveryDate: null,
    slaStatus: 'EN_PLAZO',
    totalWeightKg: 1.5,
    totalCost: 1500,
    createdBy: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
  };
}

function makeDetail(id: string): ShipmentDetail {
  return {
    id,
    trackingId: `LGST-${id.toUpperCase()}`,
    code: null,
    status: 'PRE_ALTA',
    sender: { id: 'c1', name: 'Juan Pérez' },
    receiver: { id: 'c2', name: 'Acme SA' },
    deliveryAddress: null,
    originBranch: null,
    destinationBranch: null,
    serviceLevel: null,
    paymentType: 'PAGO_ORIGEN',
    deliveryMode: 'DOMICILIO',
    deliveryInstructions: null,
    packages: [],
    totalWeightKg: 1.5,
    totalCost: 1500,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
    latestEvent: null,
  };
}

describe('ShipmentsStore', () => {
  let shipmentsService: {
    list: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
    getTimeline: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    validate: ReturnType<typeof vi.fn>;
    reject: ReturnType<typeof vi.fn>;
    cancel: ReturnType<typeof vi.fn>;
  };
  let branchesService: { list: ReturnType<typeof vi.fn> };
  let serviceLevelsService: { list: ReturnType<typeof vi.fn> };
  let store: ShipmentsStore;

  beforeEach(() => {
    shipmentsService = {
      list: vi.fn(),
      get: vi.fn(),
      getTimeline: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      validate: vi.fn(),
      reject: vi.fn(),
      cancel: vi.fn(),
    };
    branchesService = { list: vi.fn() };
    serviceLevelsService = { list: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: ShipmentsService, useValue: shipmentsService },
        { provide: BranchesService, useValue: branchesService },
        { provide: ServiceLevelsService, useValue: serviceLevelsService },
        ShipmentsStore,
      ],
    });
    store = TestBed.inject(ShipmentsStore);
  });

  describe('initial state', () => {
    it('starts empty: no shipments, no detail, wizard at step 1', () => {
      expect(store.currentShipments()).toBeNull();
      expect(store.currentShipment()).toBeNull();
      expect(store.currentTimeline()).toEqual([]);
      expect(store.branches()).toEqual([]);
      expect(store.serviceLevels()).toEqual([]);
      expect(store.wizardStep()).toBe(1);
      expect(store.wizardData()).toEqual({});
      expect(store.pagination()).toEqual({ page: 1, size: 20, total: 0 });
      expect(store.isLoading()).toBe(false);
    });
  });

  describe('loadList', () => {
    it('updates the list + pagination signals on success', async () => {
      const resp: PageResponse<ShipmentSummary> = {
        data: [makeSummary('s1')],
        total: 1,
        page: 1,
        size: 20,
      };
      shipmentsService.list.mockReturnValue(of(resp));

      await store.loadList({ page: 1, size: 20, status: 'PRE_ALTA' });

      expect(store.currentShipments()).toEqual(resp.data);
      expect(store.pagination()).toEqual({ page: 1, size: 20, total: 1 });
      expect(store.listFilters()).toEqual({ status: 'PRE_ALTA' });
    });

    it('leaves previous list intact on error and rethrows', async () => {
      shipmentsService.list.mockReturnValueOnce(
        of({ data: [makeSummary('s1')], total: 1, page: 1, size: 20 }),
      );
      await store.loadList({ page: 1, size: 20 });
      expect(store.currentShipments()?.length).toBe(1);

      shipmentsService.list.mockReturnValueOnce(throwError(() => new Error('boom')));
      await expect(store.loadList({ page: 2, size: 20 })).rejects.toThrow('boom');

      expect(store.currentShipments()?.length).toBe(1);
    });
  });

  describe('loadDetail + loadTimeline', () => {
    it('loadDetail updates currentShipment on success', async () => {
      const detail = makeDetail('s1');
      shipmentsService.get.mockReturnValue(of(detail));

      await store.loadDetail('s1');

      expect(store.currentShipment()).toEqual(detail);
      expect(store.isLoadingDetail()).toBe(false);
    });

    it('loadTimeline updates currentTimeline on success', async () => {
      const events: TrackingEvent[] = [
        {
          id: 'e1',
          packageId: 'p1',
          eventType: 'CREADO',
          eventTimestamp: '2026-01-01T00:00:00Z',
          branchId: null,
          userId: 'u1',
          eventSource: 'OPERADOR_SUCURSAL',
          metadata: null,
          createdAt: '2026-01-01T00:00:00Z',
        },
      ];
      shipmentsService.getTimeline.mockReturnValue(of(events));

      await store.loadTimeline('s1');

      expect(store.currentTimeline()).toEqual(events);
    });
  });

  describe('FSM transitions', () => {
    it('update syncs the cached detail', async () => {
      const detail = makeDetail('s1');
      shipmentsService.get.mockReturnValue(of(detail));
      await store.loadDetail('s1');

      const updated: ShipmentDetail = {
        ...detail,
        status: 'PRE_ALTA',
        updatedAt: '2026-02-01T00:00:00Z',
      };
      shipmentsService.update.mockReturnValue(of(updated));

      const req: UpdateShipmentRequest = { deliveryInstructions: 'Timbre 3B' };
      const result = await store.update('s1', req);

      expect(result).toEqual(updated);
      expect(store.currentShipment()).toEqual(updated);
    });

    it('validate / reject / cancel each refresh currentShipment', async () => {
      const detail = makeDetail('s1');

      shipmentsService.validate.mockReturnValue(of({ ...detail, status: 'CREADO' }));
      const validated = await store.validate('s1');
      expect(validated.status).toBe('CREADO');

      shipmentsService.reject.mockReturnValue(of({ ...detail, status: 'CANCELADO' }));
      const rejected = await store.reject('s1', 'Dirección inválida');
      expect(rejected.status).toBe('CANCELADO');

      shipmentsService.cancel.mockReturnValue(of({ ...detail, status: 'CANCELADO' }));
      const cancelled = await store.cancel('s1', 'Cliente canceló');
      expect(cancelled.status).toBe('CANCELADO');
    });
  });

  describe('loadCatalogs', () => {
    it('populates branches + serviceLevels from the catalog services', async () => {
      const branches: Branch[] = [
        { id: 'b1', code: 'B-CABA', name: 'CABA', addressId: null, isActive: true },
      ];
      const serviceLevels: ServiceLevel[] = [
        { id: 's1', code: 'STD', name: 'Standard', isActive: true },
      ];
      branchesService.list.mockReturnValue(of(branches));
      serviceLevelsService.list.mockReturnValue(of(serviceLevels));

      await store.loadCatalogs();

      expect(store.branches()).toEqual(branches);
      expect(store.serviceLevels()).toEqual(serviceLevels);
      expect(store.isLoadingCatalogs()).toBe(false);
    });
  });

  describe('wizard', () => {
    it('wizardNext / wizardBack move between steps (clamped 1..3)', () => {
      expect(store.wizardStep()).toBe(1);

      store.wizardNext();
      expect(store.wizardStep()).toBe(2);

      store.wizardNext();
      expect(store.wizardStep()).toBe(3);

      // Clamped at 3 — extra Next is a no-op.
      store.wizardNext();
      expect(store.wizardStep()).toBe(3);

      store.wizardBack();
      expect(store.wizardStep()).toBe(2);

      store.wizardBack();
      store.wizardBack();
      // Clamped at 1.
      expect(store.wizardStep()).toBe(1);
    });

    it('wizardGoToStep jumps to the requested step', () => {
      store.wizardGoToStep(3);
      expect(store.wizardStep()).toBe(3);
    });

    it('wizardPatchData merges partial data into the wizard draft', () => {
      store.wizardPatchData({ senderId: 'c1' });
      store.wizardPatchData({ receiverId: 'c2' });
      store.wizardPatchData({ deliveryAddressId: 'a1' });

      expect(store.wizardData()).toEqual({
        senderId: 'c1',
        receiverId: 'c2',
        deliveryAddressId: 'a1',
      });
    });

    it('wizardSubmit posts the draft, returns the response, and resets the wizard', async () => {
      store.wizardPatchData({
        senderId: 'c1',
        receiverId: 'c2',
        deliveryAddressId: 'a1',
        paymentType: 'PAGO_ORIGEN',
        packages: [{ weightKg: 1.5, contentDescription: 'Documentos' }],
      });

      const resp: CreateShipmentResponse = {
        shipment: makeShipment('s-new'),
        packages: [],
      };
      shipmentsService.create.mockReturnValue(of(resp));

      let result: CreateShipmentResponse | undefined;
      await store.wizardSubmit().then((r) => (result = r));

      expect(result).toEqual(resp);
      expect(store.wizardStep()).toBe(1);
      expect(store.wizardData()).toEqual({});
      expect(store.isSubmittingWizard()).toBe(false);
    });

    it('wizardSubmit preserves the draft on failure and clears isSubmittingWizard', async () => {
      store.wizardPatchData({ senderId: 'c1' });
      shipmentsService.create.mockReturnValueOnce(throwError(() => new Error('boom')));

      await expect(store.wizardSubmit()).rejects.toThrow('boom');

      // Draft preserved so the user can retry.
      expect(store.wizardData()).toEqual({ senderId: 'c1' });
      expect(store.isSubmittingWizard()).toBe(false);
    });

    it('wizardReset clears step + draft', () => {
      store.wizardGoToStep(3);
      store.wizardPatchData({ senderId: 'c1' });

      store.wizardReset();

      expect(store.wizardStep()).toBe(1);
      expect(store.wizardData()).toEqual({});
    });
  });

  describe('clearDetail', () => {
    it('resets currentShipment + currentTimeline', async () => {
      shipmentsService.get.mockReturnValue(of(makeDetail('s1')));
      await store.loadDetail('s1');

      store.clearDetail();

      expect(store.currentShipment()).toBeNull();
      expect(store.currentTimeline()).toEqual([]);
    });
  });
});

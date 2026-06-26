import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { ShipmentsService } from './shipments.service';
import {
  CreateShipmentRequest,
  CreateShipmentResponse,
  PageResponse,
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
    deliveryAddress: { id: 'a1', displayLabel: 'Av. Corrientes 1234, CABA' },
    originBranch: { id: 'b1', code: 'B-CABA', name: 'Sucursal CABA' },
    destinationBranch: null,
    serviceLevel: { id: 's1', code: 'STD', name: 'Standard' },
    paymentType: 'PAGO_ORIGEN',
    deliveryMode: 'DOMICILIO',
    deliveryInstructions: null,
    packages: [
      {
        id: 'p1',
        shipmentId: id,
        qrCode: `LGST-${id.toUpperCase()}-1`,
        previousStatus: null,
        status: 'PRE_ALTA',
        weightKg: 1.5,
        volumeCm3: null,
        dimensionsCm: null,
        contentDescription: 'Documentos',
        declaredValue: null,
        declaredCurrency: 'ARS',
        hasInsurance: false,
        isFragile: false,
        isUrgent: false,
        requiresSignature: false,
        requiresIdCheck: false,
        category: 'DOCUMENTOS',
        receptionCondition: 'BUENO',
        receptionNotes: null,
      },
    ],
    totalWeightKg: 1.5,
    totalCost: 1500,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
    latestEvent: null,
  };
}

describe('ShipmentsService', () => {
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    patch: ReturnType<typeof vi.fn>;
  };
  let service: ShipmentsService;

  beforeEach(() => {
    httpMock = { get: vi.fn(), post: vi.fn(), patch: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, ShipmentsService],
    });
    service = TestBed.inject(ShipmentsService);
  });

  describe('list', () => {
    it('GETs /api/v1/shipments with page, size, sort as query params', () => {
      const resp: PageResponse<ShipmentSummary> = {
        data: [makeSummary('s1')],
        total: 1,
        page: 1,
        size: 20,
      };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: PageResponse<ShipmentSummary> | undefined;
      service.list({ page: 1, size: 20, sort: 'createdAt,desc' }).subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledTimes(1);
      const [url, options] = httpMock.get.mock.calls[0];
      expect(url).toBe('/api/v1/shipments');
      expect(options.params.get('page')).toBe('1');
      expect(options.params.get('size')).toBe('20');
      expect(options.params.get('sort')).toBe('createdAt,desc');
      expect(emitted).toEqual(resp);
    });

    it('includes status + dateFrom + dateTo + search filters only when provided', () => {
      httpMock.get.mockReturnValue(of({ data: [], total: 0, page: 1, size: 20 }));

      service
        .list({
          status: 'PRE_ALTA',
          dateFrom: '2026-01-01T00:00:00Z',
          dateTo: '2026-01-31T23:59:59Z',
          search: 'LGST',
        })
        .subscribe();

      const params: HttpParams = httpMock.get.mock.calls[0][1].params;
      expect(params.get('status')).toBe('PRE_ALTA');
      expect(params.get('dateFrom')).toBe('2026-01-01T00:00:00Z');
      expect(params.get('dateTo')).toBe('2026-01-31T23:59:59Z');
      expect(params.get('search')).toBe('LGST');
    });

    it('omits optional filters when not provided', () => {
      httpMock.get.mockReturnValue(of({ data: [], total: 0, page: 1, size: 20 }));

      service.list({ page: 1, size: 20 }).subscribe();

      const params: HttpParams = httpMock.get.mock.calls[0][1].params;
      expect(params.has('status')).toBe(false);
      expect(params.has('dateFrom')).toBe(false);
      expect(params.has('search')).toBe(false);
    });
  });

  describe('get', () => {
    it('GETs /api/v1/shipments/{id} and returns the detail with packages', () => {
      const detail = makeDetail('s1');
      httpMock.get.mockReturnValue(of(detail));

      let emitted: ShipmentDetail | undefined;
      service.get('s1').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/shipments/s1');
      expect(emitted).toEqual(detail);
    });
  });

  describe('getTimeline', () => {
    it('GETs /api/v1/shipments/{id}/timeline and returns the events list', () => {
      const events: TrackingEvent[] = [
        {
          id: 'e1',
          packageId: 'p1',
          eventType: 'CREADO',
          eventTimestamp: '2026-01-01T00:00:00Z',
          branchId: 'b1',
          userId: 'u1',
          eventSource: 'OPERADOR_SUCURSAL',
          metadata: null,
          createdAt: '2026-01-01T00:00:00Z',
        },
      ];
      httpMock.get.mockReturnValue(of(events));

      let emitted: TrackingEvent[] | undefined;
      service.getTimeline('s1').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/shipments/s1/timeline');
      expect(emitted).toEqual(events);
    });
  });

  describe('create', () => {
    it('POSTs the request body and returns the create envelope (shipment + packages)', () => {
      const req: CreateShipmentRequest = {
        senderId: 'c1',
        receiverId: 'c2',
        deliveryAddressId: 'a1',
        paymentType: 'PAGO_ORIGEN',
        packages: [{ weightKg: 1.5, contentDescription: 'Documentos' }],
      };
      const resp: CreateShipmentResponse = {
        shipment: makeShipment('s-new'),
        packages: makeDetail('s-new').packages,
      };
      httpMock.post.mockReturnValue(of(resp));

      let emitted: CreateShipmentResponse | undefined;
      service.create(req).subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/shipments', req);
      expect(emitted).toEqual(resp);
    });
  });

  describe('update', () => {
    it('PATCHes /api/v1/shipments/{id} with the partial body and returns the refreshed detail', () => {
      const detail = makeDetail('s1');
      const req: UpdateShipmentRequest = { deliveryInstructions: 'Timbre 3B' };
      httpMock.patch.mockReturnValue(of(detail));

      let emitted: ShipmentDetail | undefined;
      service.update('s1', req).subscribe((r) => (emitted = r));

      expect(httpMock.patch).toHaveBeenCalledWith('/api/v1/shipments/s1', req);
      expect(emitted).toEqual(detail);
    });
  });

  describe('validate', () => {
    it('POSTs /api/v1/shipments/{id}/validate and returns the detail', () => {
      const detail = makeDetail('s1');
      httpMock.post.mockReturnValue(of(detail));

      let emitted: ShipmentDetail | undefined;
      service.validate('s1').subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/shipments/s1/validate', null);
      expect(emitted).toEqual(detail);
    });
  });

  describe('reject', () => {
    it('POSTs /api/v1/shipments/{id}/reject with the rejection reason and returns the detail', () => {
      const detail = makeDetail('s1');
      httpMock.post.mockReturnValue(of(detail));

      let emitted: ShipmentDetail | undefined;
      service.reject('s1', 'Dirección inválida').subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/shipments/s1/reject', {
        rejectionReason: 'Dirección inválida',
      });
      expect(emitted).toEqual(detail);
    });
  });

  describe('cancel', () => {
    it('POSTs /api/v1/shipments/{id}/cancel with the reason and returns the detail', () => {
      const detail = makeDetail('s1');
      httpMock.post.mockReturnValue(of(detail));

      let emitted: ShipmentDetail | undefined;
      service.cancel('s1', 'Cliente canceló').subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/shipments/s1/cancel', {
        reason: 'Cliente canceló',
      });
      expect(emitted).toEqual(detail);
    });
  });
});

import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { ServiceLevelsService } from './service-levels.service';
import { ServiceLevel } from '../types';

function makeServiceLevel(id: string, code: string): ServiceLevel {
  return { id, code, name: `Nivel ${code}`, isActive: true };
}

describe('ServiceLevelsService', () => {
  let httpMock: { get: ReturnType<typeof vi.fn> };
  let service: ServiceLevelsService;

  beforeEach(() => {
    httpMock = { get: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, ServiceLevelsService],
    });
    service = TestBed.inject(ServiceLevelsService);
  });

  describe('list', () => {
    it('GETs /api/v1/service-levels and returns the service levels list', () => {
      const levels: ServiceLevel[] = [makeServiceLevel('s1', 'STD'), makeServiceLevel('s2', 'EXP')];
      httpMock.get.mockReturnValue(of(levels));

      let emitted: ServiceLevel[] | undefined;
      service.list().subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/service-levels');
      expect(emitted).toEqual(levels);
    });

    it('returns an empty array when the tenant has no service levels', () => {
      httpMock.get.mockReturnValue(of([]));

      let emitted: ServiceLevel[] | undefined;
      service.list().subscribe((r) => (emitted = r));

      expect(emitted).toEqual([]);
    });
  });
});

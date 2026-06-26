import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { BranchesService } from './branches.service';
import { Branch } from '../types';

function makeBranch(id: string, code: string): Branch {
  return { id, code, name: `Sucursal ${code}`, addressId: null, isActive: true };
}

describe('BranchesService', () => {
  let httpMock: { get: ReturnType<typeof vi.fn> };
  let service: BranchesService;

  beforeEach(() => {
    httpMock = { get: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, BranchesService],
    });
    service = TestBed.inject(BranchesService);
  });

  describe('list', () => {
    it('GETs /api/v1/branches and returns the branches list', () => {
      const branches: Branch[] = [makeBranch('b1', 'B-CABA'), makeBranch('b2', 'B-MDQ')];
      httpMock.get.mockReturnValue(of(branches));

      let emitted: Branch[] | undefined;
      service.list().subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/branches');
      expect(emitted).toEqual(branches);
    });

    it('returns an empty array when the tenant has no branches', () => {
      httpMock.get.mockReturnValue(of([]));

      let emitted: Branch[] | undefined;
      service.list().subscribe((r) => (emitted = r));

      expect(emitted).toEqual([]);
    });
  });
});

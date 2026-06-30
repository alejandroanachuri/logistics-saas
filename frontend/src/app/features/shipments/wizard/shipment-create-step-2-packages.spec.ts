import { TestBed, ComponentFixture } from '@angular/core/testing';
import { vi } from 'vitest';

import { ShipmentCreateStep2PackagesComponent } from './shipment-create-step-2-packages';

describe('ShipmentCreateStep2PackagesComponent', () => {
  let fixture: ComponentFixture<ShipmentCreateStep2PackagesComponent>;
  let component: ShipmentCreateStep2PackagesComponent;

  function render(): void {
    TestBed.configureTestingModule({
      imports: [ShipmentCreateStep2PackagesComponent],
    });
    fixture = TestBed.createComponent(ShipmentCreateStep2PackagesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render();
    expect(component).toBeTruthy();
  });

  // -------- initial state --------

  it('starts with exactly one package slot', () => {
    render();
    expect(component.packages().length).toBe(1);
  });

  // -------- add / remove --------

  it('addPackage appends a new slot', () => {
    render();
    component.addPackage();
    expect(component.packages().length).toBe(2);
    component.addPackage();
    expect(component.packages().length).toBe(3);
  });

  it('removePackage removes the slot at the given index', () => {
    render();
    component.addPackage();
    component.addPackage();
    component.removePackage(1);
    expect(component.packages().length).toBe(2);
  });

  // -------- patch --------

  it('updatePackage merges the new payload into the slot at the index', () => {
    render();
    component.updatePackage(0, {
      weightKg: 2.5,
      contentDescription: 'Caja con libros',
      category: 'GENERAL',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    expect(component.packages()[0]?.weightKg).toBe(2.5);
    expect(component.packages()[0]?.contentDescription).toBe('Caja con libros');
  });

  // -------- canAdvance --------

  it('canAdvance returns false when there are no packages', () => {
    render();
    // Force-empty by removing the default slot.
    while (component.packages().length > 0) {
      component.removePackage(component.packages().length - 1);
    }
    expect(component.canAdvance()).toBe(false);
  });

  it('canAdvance returns false when any package weight is 0 or missing', () => {
    render();
    component.updatePackage(0, {
      weightKg: 0,
      contentDescription: 'Caja',
      category: 'GENERAL',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    expect(component.canAdvance()).toBe(false);
  });

  it('canAdvance returns false when any package is missing contentDescription', () => {
    render();
    component.updatePackage(0, {
      weightKg: 1.5,
      contentDescription: '',
      category: 'GENERAL',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    expect(component.canAdvance()).toBe(false);
  });

  it('canAdvance returns true when every package has weight > 0 and content', () => {
    render();
    component.updatePackage(0, {
      weightKg: 1.5,
      contentDescription: 'Caja',
      category: 'GENERAL',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    expect(component.canAdvance()).toBe(true);
  });

  it('canAdvance returns true with 2+ valid packages', () => {
    render();
    component.addPackage();
    component.updatePackage(0, {
      weightKg: 1.5,
      contentDescription: 'Caja A',
      category: 'GENERAL',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    component.updatePackage(1, {
      weightKg: 0.5,
      contentDescription: 'Caja B',
      category: 'DOCUMENTOS',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    expect(component.canAdvance()).toBe(true);
  });

  // -------- totalWeightKg --------

  it('totalWeightKg sums the weight across all packages', () => {
    render();
    component.addPackage();
    component.updatePackage(0, {
      weightKg: 1.5,
      contentDescription: 'Caja A',
      category: 'GENERAL',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    component.updatePackage(1, {
      weightKg: 2.5,
      contentDescription: 'Caja B',
      category: 'GENERAL',
      isFragile: false,
      isUrgent: false,
      requiresSignature: false,
      requiresIdCheck: false,
    });
    expect(component.totalWeightKg()).toBe(4);
  });
});

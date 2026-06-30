import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { vi } from 'vitest';
import { Observable, of, throwError } from 'rxjs';

import { PublicTrackComponent } from './public-track';
import { PublicTrackService } from '../../core/services/public-track.service';
import { PublicTrackResponse } from '../../core/types';

function makeResponse(overrides: Partial<PublicTrackResponse> = {}): PublicTrackResponse {
  return {
    trackingId: 'LGST-A3K9P2RX',
    status: 'EN_TRANSITO',
    statusMessage: 'En tránsito a destino',
    isPartial: false,
    packageCount: 1,
    totalWeightKg: 1.5,
    receiverName: 'Juan P.',
    timeline: [
      {
        timestamp: '2026-01-15T10:00:00Z',
        message: 'Recibido en sucursal de origen',
      },
      {
        timestamp: '2026-01-15T14:30:00Z',
        message: 'En tránsito a destino',
      },
    ],
    ...overrides,
  };
}

describe('PublicTrackComponent', () => {
  let fixture: ComponentFixture<PublicTrackComponent>;
  let component: PublicTrackComponent;
  let publicTrackServiceMock: {
    track: ReturnType<typeof vi.fn>;
  };
  let routeTrackingId = 'LGST-A3K9P2RX';

  /**
   * Render with the given observable factory. Each test passes
   * a fresh closure that produces the response (success) or an
   * HttpErrorResponse (failure). The component subscribes once
   * during ngOnInit; the mockImplementation is what it sees.
   */
  function render(
    factory: (trackingId: string) => Observable<PublicTrackResponse> = () => of(makeResponse()),
  ): void {
    publicTrackServiceMock = {
      track: vi.fn().mockImplementation((id: string) => factory(id)),
    };

    TestBed.configureTestingModule({
      imports: [PublicTrackComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ lgstid: routeTrackingId }),
            },
          },
        },
        { provide: PublicTrackService, useValue: publicTrackServiceMock },
      ],
    });

    fixture = TestBed.createComponent(PublicTrackComponent);
    // The component declares `trackingId` as `input.required<string>()`,
    // so it must be bound explicitly before the first change-detection
    // pass. In production this happens via `withComponentInputBinding()`
    // + the router; in the unit-test sandbox we set it manually, which
    // is the pattern used by the other input-required specs.
    fixture.componentRef.setInput('trackingId', routeTrackingId);
    component = fixture.componentInstance;
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', async () => {
    render();
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  // -------- route param --------

  it('renders the trackingId from the route param, in monospace', async () => {
    routeTrackingId = 'LGST-ZZZZZZZZ';
    render(() => of(makeResponse({ trackingId: 'LGST-ZZZZZZZZ' })));
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    const headerEl = (fixture.nativeElement as HTMLElement).querySelector('[data-tracking-id]');
    expect(headerEl).toBeTruthy();
    expect(headerEl?.textContent?.trim()).toBe('LGST-ZZZZZZZZ');
    expect(headerEl?.className).toContain('font-mono');
  });

  // -------- service call --------

  it('calls publicTrackService.track on init with the route param', async () => {
    routeTrackingId = 'LGST-XYZ12345';
    render(() => of(makeResponse({ trackingId: 'LGST-XYZ12345' })));
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    expect(publicTrackServiceMock.track).toHaveBeenCalledWith('LGST-XYZ12345');
  });

  // -------- happy-path rendering --------

  it('renders response data: statusMessage, packageCount, receiverName', async () => {
    render(() =>
      of(
        makeResponse({
          statusMessage: 'En reparto',
          packageCount: 3,
          receiverName: 'María G.',
        }),
      ),
    );
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('En reparto');
    expect(text).toContain('3');
    expect(text).toContain('María G.');
  });

  it('shows the isPartial indicator when isPartial is true', async () => {
    render(() => of(makeResponse({ isPartial: true })));
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    const partialEl = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-partial-indicator]',
    );
    expect(partialEl).toBeTruthy();
    expect(partialEl?.textContent).toContain('Entregado parcialmente');
  });

  it('does not show the isPartial indicator when isPartial is false', async () => {
    render(() => of(makeResponse({ isPartial: false })));
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    const partialEl = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-partial-indicator]',
    );
    expect(partialEl).toBeNull();
  });

  // -------- timeline --------

  it('renders timeline events with timestamp + message', async () => {
    render();
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    const events = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-public-event]');
    expect(events.length).toBe(2);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Recibido en sucursal de origen');
    expect(text).toContain('En tránsito a destino');
  });

  // -------- 404 error --------

  it('shows the canonical 404 message when the service returns 404', async () => {
    render((id: string) =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
            error: {
              code: 'TRACKING_NOT_FOUND',
              message: 'No tracking found for ' + id,
            },
          }),
      ),
    );
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    const errorEl = (fixture.nativeElement as HTMLElement).querySelector('[data-error-message]');
    expect(errorEl).toBeTruthy();
    expect(errorEl?.textContent).toContain('No encontramos un envío con ese código.');
  });

  // -------- generic error --------

  it('shows a generic error message when the service throws a non-404 error', async () => {
    render(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            statusText: 'Internal Server Error',
            error: { code: 'INTERNAL_SERVER_ERROR' },
          }),
      ),
    );
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();

    const errorEl = (fixture.nativeElement as HTMLElement).querySelector('[data-error-message]');
    expect(errorEl).toBeTruthy();
    expect(errorEl?.textContent).toContain('No pudimos cargar el estado del envío.');
  });
});

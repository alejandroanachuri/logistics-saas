import { TestBed } from '@angular/core/testing';

import { ColumnDef, DataTableComponent } from './data-table';

interface Row {
  readonly id: string;
  readonly name: string;
  readonly email: string;
}

const COLUMNS: ColumnDef<Row>[] = [
  { key: 'name', label: 'Nombre', accessor: (r) => r.name },
  { key: 'email', label: 'Email', accessor: (r) => r.email },
];

const ROWS: Row[] = [
  { id: '1', name: 'Juan', email: 'juan@test.com' },
  { id: '2', name: 'Ana', email: 'ana@test.com' },
];

describe('DataTableComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [DataTableComponent] });
  });

  it('renders one row per item with cell text from the column accessor', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 2);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Juan');
    expect(fixture.nativeElement.textContent).toContain('juan@test.com');
    expect(fixture.nativeElement.textContent).toContain('Ana');
    expect(fixture.nativeElement.textContent).toContain('ana@test.com');
  });

  it('renders the column headers from the column definitions', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 2);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nombre');
    expect(fixture.nativeElement.textContent).toContain('Email');
  });

  it('emits sortChange with key + ascending direction when a sortable header is clicked (initial state)', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    const cols: ColumnDef<Row>[] = [
      { key: 'name', label: 'Nombre', accessor: (r) => r.name, sortable: true },
      { key: 'email', label: 'Email', accessor: (r) => r.email },
    ];
    fixture.componentRef.setInput('columns', cols);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 2);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    let emitted: { key: string; direction: 'asc' | 'desc' } | undefined;
    fixture.componentInstance.sortChange.subscribe((e) => (emitted = e));

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-sort-key="name"]',
    )!;
    button.click();

    expect(emitted).toEqual({ key: 'name', direction: 'asc' });
  });

  it('emits sortChange with descending direction when a currently-asc column is clicked', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    const cols: ColumnDef<Row>[] = [
      { key: 'name', label: 'Nombre', accessor: (r) => r.name, sortable: true, sortDirection: 'asc' },
    ];
    fixture.componentRef.setInput('columns', cols);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 2);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    let emitted: { key: string; direction: 'asc' | 'desc' } | undefined;
    fixture.componentInstance.sortChange.subscribe((e) => (emitted = e));

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-sort-key="name"]',
    )!;
    button.click();

    expect(emitted).toEqual({ key: 'name', direction: 'desc' });
  });

  it('emits pageChange with the next page on "Siguiente" click', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 25);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    let emitted: { page: number; size: number } | undefined;
    fixture.componentInstance.pageChange.subscribe((e) => (emitted = e));

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-page-next]')!;
    btn.click();

    expect(emitted).toEqual({ page: 2, size: 10 });
  });

  it('emits pageChange with the previous page on "Anterior" click', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 25);
    fixture.componentRef.setInput('page', 3);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    let emitted: { page: number; size: number } | undefined;
    fixture.componentInstance.pageChange.subscribe((e) => (emitted = e));

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-page-prev]')!;
    btn.click();

    expect(emitted).toEqual({ page: 2, size: 10 });
  });

  it('disables "Anterior" on the first page and "Siguiente" on the last page', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 25);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    const prev: HTMLButtonElement = fixture.nativeElement.querySelector('[data-page-prev]')!;
    expect(prev.disabled).toBe(true);

    fixture.componentRef.setInput('page', 3); // last page (total=25, size=10 → pages 1..3)
    fixture.detectChanges();

    const next: HTMLButtonElement = fixture.nativeElement.querySelector('[data-page-next]')!;
    expect(next.disabled).toBe(true);
  });

  it('shows a "Sin resultados" placeholder when the data array is empty', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('data', []);
    fixture.componentRef.setInput('total', 0);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin resultados');
  });

  it('renders the pagination summary with current page and total', () => {
    const fixture = TestBed.createComponent(DataTableComponent<Row>);
    fixture.componentRef.setInput('columns', COLUMNS);
    fixture.componentRef.setInput('data', ROWS);
    fixture.componentRef.setInput('total', 25);
    fixture.componentRef.setInput('page', 2);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    const summary: HTMLElement = fixture.nativeElement.querySelector('[data-pagination-summary]')!;
    expect(summary.textContent).toContain('Página 2 de 3');
    expect(summary.textContent).toContain('25 resultados');
  });
});

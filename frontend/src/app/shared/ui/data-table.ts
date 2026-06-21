import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export interface ColumnDef<T = unknown> {
  /** Unique key for the column (used as the track-by value in headers). */
  readonly key: string;
  /** Header label rendered in <th>. */
  readonly label: string;
  /**
   * Cell accessor — receives the row and returns the cell text.
   * Keeps the table a pure presentational primitive: the parent
   * owns the data shape, the table just renders it.
   */
  readonly accessor: (row: T) => string;
  /** When true, the header is clickable and emits sortChange. */
  readonly sortable?: boolean;
  /** Current sort direction for this column (when sortable). */
  readonly sortDirection?: 'asc' | 'desc' | null;
  /** When true, hide the column on viewports narrower than md. */
  readonly hideOnMobile?: boolean;
}

export interface PageEvent {
  readonly page: number;
  readonly size: number;
}

export interface SortEvent {
  readonly key: string;
  readonly direction: 'asc' | 'desc';
}

export interface RowActionEvent<T = unknown> {
  readonly row: T;
  readonly action: string;
}

/**
 * DataTable — pure HTML generic data table with sort, pagination,
 * and mobile-responsive layout. The parent owns the data, the
 * filter state, and the action handlers.
 *
 * <p>Mobile (<768px): renders stacked cards instead of a table
 * for touch-friendly reading. Desktop: renders a standard table
 * with sortable headers and pagination footer.
 *
 * <p>Actions: emits `pageChange` (with the next page + size),
 * `sortChange` (with the column key + direction toggle), and
 * `rowAction` (with the row + the action label).
 *
 * Standalone, OnPush, generic over T (the row type).
 */
@Component({
  selector: 'app-data-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Desktop table -->
    <div class="hidden overflow-x-auto rounded-md border border-outline-variant md:block">
      <table class="w-full text-sm">
        <thead class="bg-surface-container-low text-on-surface-variant">
          <tr>
            @for (col of columns(); track col.key) {
              <th
                [class]="thClasses(col)"
                scope="col"
              >
                @if (col.sortable) {
                  <button
                    type="button"
                    [attr.aria-label]="'Ordenar por ' + col.label"
                    [attr.aria-sort]="ariaSortFor(col)"
                    [attr.data-sort-key]="col.key"
                    (click)="onSortClick(col)"
                    class="inline-flex items-center gap-1 font-semibold uppercase tracking-wide hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                  >
                    <span>{{ col.label }}</span>
                    <span aria-hidden="true">{{ sortGlyph(col) }}</span>
                  </button>
                } @else {
                  <span class="font-semibold uppercase tracking-wide">{{ col.label }}</span>
                }
              </th>
            }
          </tr>
        </thead>
        <tbody class="bg-surface-container-lowest">
          @for (row of data(); track trackBy($index, row)) {
            <tr data-row class="border-t border-outline-variant">
              @for (col of columns(); track col.key) {
                <td [class]="tdClasses(col)" class="px-4 py-3 text-on-surface">
                  {{ col.accessor(row) }}
                </td>
              }
            </tr>
          } @empty {
            <tr>
              <td [attr.colspan]="columns().length" class="px-4 py-12 text-center text-on-surface-variant">
                Sin resultados.
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    <!-- Mobile cards -->
    <div class="space-y-3 md:hidden">
      @for (row of data(); track trackBy($index, row)) {
        <article
          data-row
          class="rounded-md border border-outline-variant bg-surface-container-lowest p-4"
        >
          @for (col of columns(); track col.key) {
            @if (!col.hideOnMobile) {
              <div class="flex justify-between gap-3 py-1 text-sm">
                <span class="font-medium text-on-surface-variant">{{ col.label }}</span>
                <span class="text-on-surface">{{ col.accessor(row) }}</span>
              </div>
            }
          }
        </article>
      } @empty {
        <p class="rounded-md border border-outline-variant bg-surface-container-lowest p-6 text-center text-sm text-on-surface-variant">
          Sin resultados.
        </p>
      }
    </div>

    <!-- Pagination footer -->
    <nav class="mt-4 flex flex-wrap items-center justify-between gap-2" aria-label="Paginación">
      <p class="text-sm text-on-surface-variant" data-pagination-summary>
        Página {{ currentPage() }} de {{ totalPages() }} · {{ total() }} resultados
      </p>
      <div class="flex gap-2">
        <button
          type="button"
          data-page-prev
          [disabled]="currentPage() <= 1"
          [attr.aria-label]="'Página anterior'"
          (click)="onPrevPage()"
          class="inline-flex h-9 items-center rounded-md border border-outline-variant bg-surface-container-lowest px-3 text-sm font-medium text-on-surface hover:bg-surface-container-low disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Anterior
        </button>
        <button
          type="button"
          data-page-next
          [disabled]="currentPage() >= totalPages()"
          [attr.aria-label]="'Página siguiente'"
          (click)="onNextPage()"
          class="inline-flex h-9 items-center rounded-md border border-outline-variant bg-surface-container-lowest px-3 text-sm font-medium text-on-surface hover:bg-surface-container-low disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Siguiente
        </button>
      </div>
    </nav>
  `,
})
export class DataTableComponent<T = unknown> {
  readonly columns = input.required<ColumnDef<T>[]>();
  readonly data = input.required<T[]>();
  readonly total = input.required<number>();
  readonly page = input.required<number>();
  readonly size = input<number>(10);

  readonly pageChange = output<PageEvent>();
  readonly sortChange = output<SortEvent>();
  readonly rowAction = output<RowActionEvent<T>>();

  protected readonly currentPage = computed(() => Math.max(1, this.page()));
  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.total() / this.size())),
  );

  protected thClasses(col: ColumnDef<T>): string {
    const base = 'px-4 py-3 text-left text-xs';
    return col.hideOnMobile ? `${base} hidden md:table-cell` : base;
  }

  protected tdClasses(col: ColumnDef<T>): string {
    return col.hideOnMobile ? 'hidden md:table-cell' : '';
  }

  protected trackBy(index: number, _row: T): number {
    return index;
  }

  protected sortGlyph(col: ColumnDef<T>): string {
    if (!col.sortable) return '';
    if (col.sortDirection === 'asc') return '↑';
    if (col.sortDirection === 'desc') return '↓';
    return '↕';
  }

  protected ariaSortFor(col: ColumnDef<T>): 'ascending' | 'descending' | 'none' {
    if (!col.sortable) return 'none';
    if (col.sortDirection === 'asc') return 'ascending';
    if (col.sortDirection === 'desc') return 'descending';
    return 'none';
  }

  protected onSortClick(col: ColumnDef<T>): void {
    if (!col.sortable) return;
    const nextDirection: 'asc' | 'desc' = col.sortDirection === 'asc' ? 'desc' : 'asc';
    this.sortChange.emit({ key: col.key, direction: nextDirection });
  }

  protected onPrevPage(): void {
    const p = this.currentPage();
    if (p <= 1) return;
    this.pageChange.emit({ page: p - 1, size: this.size() });
  }

  protected onNextPage(): void {
    const p = this.currentPage();
    const last = this.totalPages();
    if (p >= last) return;
    this.pageChange.emit({ page: p + 1, size: this.size() });
  }
}

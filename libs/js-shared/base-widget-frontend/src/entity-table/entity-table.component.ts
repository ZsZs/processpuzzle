import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { formatValue, ValueFormat, valueAtPath } from '../value-format/value-format';

/** One column: the value at `path` of every row, under `header`. */
export interface EntityTableColumn {
  path: string;
  header?: string;
  format?: ValueFormat;
  align?: 'start' | 'center' | 'end';
}

/** A footer cell under the column of the same `path`. */
export interface EntityTableTotal {
  path: string;
  aggregate: 'sum' | 'count';
}

/**
 * A plain table of objects — an order's line items, every open claim. Rows arrive already resolved through
 * `rows`; which objects they are is the container's business (a document binds `rows` to a port path such as
 * `order.lineItems`), so the widget works the same outside a document.
 *
 * A native `<table>` rather than `mat-table`, because the table is printed: a native one repeats its header
 * on every page and breaks between rows by itself.
 */
@Component({
  selector: 'pp-entity-table',
  standalone: true,
  template: `
    <table class="pp-entity-table" data-testid="entity-table">
      @if (caption()) {
        <caption>{{ caption() }}</caption>
      }
      <thead>
        <tr>
          @for (column of columns(); track $index) {
            <th scope="col" [style.text-align]="column.align ?? 'start'">{{ column.header ?? column.path }}</th>
          }
        </tr>
      </thead>
      <tbody>
        @for (row of rowList(); track $index) {
          <tr>
            @for (column of columns(); track $index) {
              <td [style.text-align]="column.align ?? 'start'">{{ cell(row, column) }}</td>
            }
          </tr>
        } @empty {
          <tr class="pp-entity-table__empty">
            <td [attr.colspan]="columns().length || 1"></td>
          </tr>
        }
      </tbody>
      @if (footer(); as footer) {
        <tfoot>
          <tr>
            @for (cell of footer; track $index) {
              <td [style.text-align]="columns()[$index].align ?? 'start'">{{ cell }}</td>
            }
          </tr>
        </tfoot>
      }
    </table>
  `,
  styles: [
    `
      .pp-entity-table {
        width: 100%;
        border-collapse: collapse;
      }
      caption {
        text-align: start;
        font-weight: 500;
        padding-bottom: 4px;
      }
      th,
      td {
        padding: 4px 8px;
        border-bottom: 1px solid rgba(0, 0, 0, 0.12);
      }
      th {
        font-weight: 500;
      }
      tfoot td {
        font-weight: 500;
        border-bottom: none;
        border-top: 2px solid rgba(0, 0, 0, 0.38);
      }
      .pp-entity-table__empty td {
        height: 1.5em;
      }
      tr {
        break-inside: avoid;
      }
    `,
  ],
})
export class EntityTableComponent {
  readonly rows = input<unknown>();
  readonly columns = input<EntityTableColumn[]>([]);
  readonly totals = input<EntityTableTotal[]>([]);
  readonly caption = input<string>();

  private readonly locale = toSignal(inject(TranslocoService).langChanges$);

  /** Anything but an array — a port still loading, a path naming a single object — is no rows. */
  protected readonly rowList = computed(() => {
    const rows = this.rows();
    return Array.isArray(rows) ? rows : [];
  });

  /** One cell per column, or no footer at all when no total is configured. */
  protected readonly footer = computed(() => {
    const totals = this.totals() ?? [];
    if (totals.length === 0) return undefined;
    return this.columns().map((column) => {
      const total = totals.find((candidate) => candidate.path === column.path);
      if (!total) return '';
      if (total.aggregate === 'count') return formatValue(this.rowList().length, 'number', this.locale());
      const sum = this.rowList().reduce<number>((acc, row) => {
        const value = Number(valueAtPath(row, column.path));
        return Number.isFinite(value) ? acc + value : acc;
      }, 0);
      return formatValue(sum, column.format === 'text' ? 'number' : column.format, this.locale());
    });
  });

  protected cell(row: unknown, column: EntityTableColumn): string {
    return formatValue(valueAtPath(row, column.path), column.format, this.locale());
  }
}

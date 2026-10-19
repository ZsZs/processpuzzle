import { render, screen } from '@testing-library/angular';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { describe, expect, it } from 'vitest';
import { EntityTableColumn, EntityTableComponent, EntityTableTotal } from './entity-table.component';

const columns: EntityTableColumn[] = [
  { path: 'productName', header: 'Product' },
  { path: 'quantity', header: 'Qty', format: 'number', align: 'end' },
  { path: 'unitPrice', format: 'currency:EUR' },
];
const rows = [
  { productName: 'Bolt', quantity: 2, unitPrice: 1.5 },
  { productName: 'Nut', quantity: 3, unitPrice: 'n/a' },
];

async function renderTable(inputs: { rows?: unknown; columns?: EntityTableColumn[]; totals?: EntityTableTotal[]; caption?: string }) {
  return render(EntityTableComponent, { inputs, providers: [provideTranslocoTesting({ translations: {} })] });
}

describe('EntityTableComponent', () => {
  it('renders one row per object and one cell per column, headed by header or path', async () => {
    await renderTable({ rows, columns, caption: 'Line items' });

    expect(screen.getByText('Line items')).toBeTruthy();
    expect(screen.getAllByRole('columnheader').map((th) => th.textContent?.trim())).toEqual(['Product', 'Qty', 'unitPrice']);
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(screen.getByText('€1.50')).toBeTruthy();
    expect(screen.getByText('Qty').getAttribute('style')).toContain('text-align: end');
  });

  it('sums and counts into a footer under the matching columns', async () => {
    const { container } = await renderTable({
      rows,
      columns,
      totals: [
        { path: 'quantity', aggregate: 'sum' },
        { path: 'productName', aggregate: 'count' },
        { path: 'unitPrice', aggregate: 'sum' },
      ],
    });

    const footer = Array.from(container.querySelectorAll('tfoot td')).map((td) => td.textContent?.trim());
    expect(footer).toEqual(['2', '5', '€1.50']);
  });

  it('formats a sum of a text column as a number', async () => {
    const { container } = await renderTable({ rows, columns: [{ path: 'quantity', format: 'text' }], totals: [{ path: 'quantity', aggregate: 'sum' }] });

    expect(container.querySelector('tfoot td')?.textContent?.trim()).toBe('5');
  });

  it('treats anything but an array as no rows, and has no footer without totals', async () => {
    const { container } = await renderTable({ rows: { not: 'an array' }, columns });

    expect(container.querySelectorAll('tbody tr.pp-entity-table__empty')).toHaveLength(1);
    expect(container.querySelector('tfoot')).toBeNull();
  });

  it('renders an empty row spanning one column when nothing is configured', async () => {
    const { container } = await renderTable({});

    expect(container.querySelector('tbody td')?.getAttribute('colspan')).toBe('1');
  });
});

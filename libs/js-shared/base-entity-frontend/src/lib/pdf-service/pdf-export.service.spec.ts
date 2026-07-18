import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PdfExportService } from './pdf-export.service';
import type { PdfColumnDefinition } from './pdf-export.types';

const save = vi.fn();
const autoTable = vi.fn();

vi.mock('jspdf', () => ({
  default: class {
    internal = { pageSize: { getWidth: () => 210, getHeight: () => 297 } };
    setFontSize = vi.fn();
    setFont = vi.fn();
    setTextColor = vi.fn();
    text = vi.fn();
    getNumberOfPages = () => 1;
    setPage = vi.fn();
    save = save;
  },
}));

vi.mock('jspdf-autotable', () => ({ default: (...args: unknown[]) => autoTable(...args) }));

describe('PdfExportService', () => {
  let service: PdfExportService;
  const columns: PdfColumnDefinition[] = [
    { field: 'name', header: 'Name' },
    { field: 'active', header: 'Active', formatter: (v) => (v ? 'yes' : 'no') },
  ];

  beforeEach(() => {
    service = new PdfExportService();
    save.mockClear();
    autoTable.mockClear();
  });

  it('returns failure without touching jsPDF when there is no data', async () => {
    const result = await service.export([], columns, { filename: 'orders' });

    expect(result).toEqual({ success: false, filename: 'orders.pdf', rowCount: 0, error: 'No data to export.' });
    expect(save).not.toHaveBeenCalled();
  });

  it('generates and saves a PDF for non-empty data', async () => {
    const entities = [
      { name: 'Alice', active: true },
      { name: 'Bob', active: false },
    ];

    const result = await service.export(entities, columns, { title: 'People', filename: 'people' });

    expect(result).toEqual({ success: true, filename: 'people.pdf', rowCount: 2 });
    expect(save).toHaveBeenCalledWith('people.pdf');
    expect(service.exporting()).toBe(false);
  });

  it('applies column formatters when building the table body', async () => {
    await service.export([{ name: 'Alice', active: true }], columns);

    const options = autoTable.mock.calls[0][1] as { body: string[][] };
    expect(options.body).toEqual([['Alice', 'yes']]);
  });
});

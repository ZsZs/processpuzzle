import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PdfExportService } from './pdf-export.service';
import type { PdfColumnDefinition } from './pdf-export.types';

const save = vi.fn();
const setPage = vi.fn();
const autoTable = vi.fn();
const constructorOptions: Array<Record<string, unknown>> = [];

vi.mock('jspdf', () => ({
  default: class {
    internal = { pageSize: { getWidth: () => 210, getHeight: () => 297 } };
    setFontSize = vi.fn();
    setFont = vi.fn();
    setTextColor = vi.fn();
    text = vi.fn();
    getNumberOfPages = () => 1;
    setPage = setPage;
    save = save;
    constructor(options: Record<string, unknown>) {
      constructorOptions.push(options);
    }
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
    setPage.mockClear();
    autoTable.mockClear();
    constructorOptions.length = 0;
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

    const result = await service.export(entities, columns, { title: 'People', subtitle: '2 records', filename: 'people' });

    expect(result).toEqual({ success: true, filename: 'people.pdf', rowCount: 2 });
    expect(save).toHaveBeenCalledWith('people.pdf');
    expect(service.exporting()).toBe(false);
  });

  it('defaults the filename when none is provided', async () => {
    const result = await service.export([{ name: 'Alice', active: true }], columns);

    expect(result.filename).toBe('export.pdf');
    expect(save).toHaveBeenCalledWith('export.pdf');
  });

  it('applies column formatters when building the table body', async () => {
    await service.export([{ name: 'Alice', active: true }], columns);

    const options = autoTable.mock.calls[0][1] as { body: string[][] };
    expect(options.body).toEqual([['Alice', 'yes']]);
  });

  it('coerces raw values with the built-in formatter when a column has none', async () => {
    const plainColumns: PdfColumnDefinition[] = [
      { field: 'flag', header: 'Flag' },
      { field: 'when', header: 'When' },
      { field: 'iso', header: 'Iso' },
      { field: 'plain', header: 'Plain' },
      { field: 'count', header: 'Count' },
      { field: 'big', header: 'Big' },
      { field: 'meta', header: 'Meta' },
      { field: 'sym', header: 'Sym' },
      { field: 'empty', header: 'Empty' },
    ];
    const when = new Date('2024-01-18T00:00:00.000Z');

    await service.export([{ flag: true, when, iso: '2024-03-04', plain: 'hello', count: 1234, big: 10n, meta: { a: 1 }, sym: Symbol('x'), empty: null }], plainColumns);

    const options = autoTable.mock.calls[0][1] as { body: string[][] };
    expect(options.body[0]).toEqual(['Yes', when.toLocaleDateString(), new Date('2024-03-04').toLocaleDateString(), 'hello', (1234).toLocaleString(), '10', '{"a":1}', '', '']);
  });

  it('honours an explicit orientation', async () => {
    await service.export([{ name: 'Alice', active: true }], columns, { orientation: 'landscape' });

    expect(constructorOptions[0]['orientation']).toBe('landscape');
  });

  it('auto-selects landscape when there are more than five columns', async () => {
    const wide = Array.from({ length: 6 }, (_, i) => ({ field: `f${i}`, header: `H${i}` }));

    await service.export([{ f0: 'a' }], wide);

    expect(constructorOptions[0]['orientation']).toBe('landscape');
  });

  it('auto-selects portrait for a narrow table', async () => {
    await service.export([{ name: 'Alice', active: true }], columns);

    expect(constructorOptions[0]['orientation']).toBe('portrait');
  });

  it('draws page footers by default', async () => {
    await service.export([{ name: 'Alice', active: true }], columns);

    expect(setPage).toHaveBeenCalledWith(1);
  });

  it('skips the footer pass when includeFooter is false', async () => {
    await service.export([{ name: 'Alice', active: true }], columns, { includeFooter: false });

    expect(setPage).not.toHaveBeenCalled();
  });

  it('returns a failure result and clears the flag when generation throws', async () => {
    autoTable.mockImplementationOnce(() => {
      throw new Error('boom');
    });

    const result = await service.export([{ name: 'Alice', active: true }], columns, { filename: 'people' });

    expect(result).toEqual({ success: false, filename: 'people.pdf', rowCount: 1, error: 'boom' });
    expect(service.exporting()).toBe(false);
  });
});

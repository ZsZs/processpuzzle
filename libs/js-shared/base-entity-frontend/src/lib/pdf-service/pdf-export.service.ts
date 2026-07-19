import { Injectable, signal } from '@angular/core';
import type { PdfColumnDefinition, PdfExportOptions, PdfExportResult } from './pdf-export.types';

/**
 * Generates and downloads a PDF table from entity store data, client-side.
 *
 * Uses jsPDF + jspdf-autotable, both lazy-loaded on first call so the ~500 KB
 * bundle is never fetched until the user actually requests an export.
 *
 * Usage in BaseEntityList:
 *
 * ```typescript
 * private readonly pdfExport = inject(PdfExportService);
 *
 * async onExportPdf() {
 *   await this.pdfExport.export(
 *     this.store.entities(),          // signal snapshot → plain array
 *     this.pdfColumns(),              // derived from entity descriptor
 *     { title: 'Orders', filename: 'orders-export' }
 *   );
 * }
 * ```
 *
 * Install dependencies:
 *   npm install jspdf jspdf-autotable
 *   npm install --save-dev @types/jspdf-autotable
 */
@Injectable({ providedIn: 'root' })
export class PdfExportService {

  /** Tracks whether a PDF generation is currently in progress. */
  readonly exporting = signal(false);

  /**
   * Exports entities to a PDF and triggers a browser download.
   *
   * @param entities  The raw entity array — typically `store.entities()`.
   * @param columns   Column definitions derived from the entity descriptor.
   * @param options   Title, filename, orientation, page size.
   */
  async export(
    entities: Record<string, unknown>[],
    columns: PdfColumnDefinition[],
    options: PdfExportOptions = {}
  ): Promise<PdfExportResult> {
    const filename = `${options.filename ?? 'export'}.pdf`;

    if (entities.length === 0) {
      return { success: false, filename, rowCount: 0, error: 'No data to export.' };
    }

    this.exporting.set(true);
    try {
      return await this.generate(entities, columns, options, filename);
    } catch (err) {
      const error = err instanceof Error ? err.message : 'PDF generation failed.';
      console.error('[PdfExportService]', error);
      return { success: false, filename, rowCount: entities.length, error };
    } finally {
      this.exporting.set(false);
    }
  }

  private async generate(
    entities: Record<string, unknown>[],
    columns: PdfColumnDefinition[],
    options: PdfExportOptions,
    filename: string
  ): Promise<PdfExportResult> {
    // Lazy-load — neither jsPDF nor autotable is bundled into the main chunk.
    const [{ default: jsPDF }, { default: autoTable }] = await Promise.all([
      import('jspdf'),
      import('jspdf-autotable'),
    ]);

    const orientation = options.orientation ?? (columns.length > 5 ? 'landscape' : 'portrait');
    const doc = new jsPDF({
      orientation,
      unit: 'mm',
      format: options.pageSize ?? 'a4',
    });

    const pageWidth = doc.internal.pageSize.getWidth();
    const margin = 14;
    const contentWidth = pageWidth - margin * 2;

    // ── Title block ───────────────────────────────────────────────────────────
    let cursorY = margin;

    if (options.title) {
      doc.setFontSize(16);
      doc.setFont('helvetica', 'bold');
      doc.text(options.title, margin, cursorY);
      cursorY += 7;
    }

    if (options.subtitle) {
      doc.setFontSize(10);
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(100);
      doc.text(options.subtitle, margin, cursorY);
      doc.setTextColor(0);
      cursorY += 5;
    }

    // Export timestamp — always shown so the reader knows when this was generated.
    doc.setFontSize(8);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(150);
    doc.text(`Exported ${new Date().toLocaleString()}`, margin, cursorY);
    doc.setTextColor(0);
    cursorY += 4;

    // ── Table ─────────────────────────────────────────────────────────────────
    const head = [columns.map(c => c.header)];

    const body = entities.map(entity =>
      columns.map(col => {
        const value = entity[col.field];
        return col.formatter
          ? col.formatter(value, entity)
          : this.formatValue(value);
      })
    );

    // Distribute column widths proportionally from the weight hints.
    const totalWeight = columns.reduce((sum, c) => sum + (c.width ?? 1), 0);
    const columnStyles: Record<number, object> = {};
    columns.forEach((col, i) => {
      columnStyles[i] = {
        cellWidth: ((col.width ?? 1) / totalWeight) * contentWidth,
        halign: col.align ?? 'left',
      };
    });

    autoTable(doc, {
      head,
      body,
      startY: cursorY,
      margin: { left: margin, right: margin },
      columnStyles,
      headStyles: {
        fillColor: [41, 128, 185],   // neutral blue — swap to your brand color
        textColor: 255,
        fontStyle: 'bold',
        fontSize: 9,
      },
      bodyStyles: {
        fontSize: 8,
        textColor: 40,
      },
      alternateRowStyles: {
        fillColor: [245, 247, 250],
      },
      // Repeat column headers on every page for long exports.
      showHead: 'everyPage',
    });

    // Page footer with filename + page numbers. Added in a post-pass so the total
    // page count is known (it isn't yet while autotable is still laying out pages).
    if (options.includeFooter !== false) {
      const pageCount = doc.getNumberOfPages();
      for (let page = 1; page <= pageCount; page++) {
        doc.setPage(page);
        this.drawFooter(doc, page, pageCount, filename);
      }
    }

    doc.save(filename);

    return { success: true, filename, rowCount: entities.length };
  }

  /**
   * Formats a raw entity field value as a readable string for the PDF cell.
   * Handles the common cases; override per-column with a custom `formatter`.
   */
  private formatValue(value: unknown): string {
    if (value === null || value === undefined) return '';
    if (value instanceof Date) return value.toLocaleDateString();
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (typeof value === 'number') return value.toLocaleString();
    if (typeof value === 'bigint') return value.toString();
    if (typeof value === 'string') return this.formatString(value);
    if (typeof value === 'object') return JSON.stringify(value);
    return ''; // symbol / function — not meaningful in a table cell
  }

  /** Renders ISO date strings as locale dates, leaving all other strings untouched. */
  private formatString(value: string): string {
    const isoDatePattern = /^\d{4}-\d{2}-\d{2}(T.*)?$/;
    if (!isoDatePattern.test(value)) return value;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
  }

  private drawFooter(
    doc: InstanceType<typeof import('jspdf').default>,
    pageNumber: number,
    pageCount: number,
    filename: string
  ): void {
    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();

    doc.setFontSize(7);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(150);

    // Left: filename
    doc.text(filename, 14, pageHeight - 8);

    // Right: page numbers
    doc.text(
      `Page ${pageNumber} of ${pageCount}`,
      pageWidth - 14,
      pageHeight - 8,
      { align: 'right' }
    );

    doc.setTextColor(0);
  }
}

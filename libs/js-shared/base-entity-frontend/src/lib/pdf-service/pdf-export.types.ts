/**
 * Defines a single column in the exported PDF table.
 * Maps directly from your entity descriptor's field definitions.
 */
export interface PdfColumnDefinition {
  /** Field key on the entity object — used to extract the value */
  field: string;

  /** Display label used as the column header in the PDF */
  header: string;

  /**
   * Optional value formatter. If omitted, the raw value is coerced to string.
   * Receives the raw field value and the full entity row.
   */
  formatter?: (value: unknown, row: Record<string, unknown>) => string;

  /**
   * Relative column width weight. Columns without a width share the remaining
   * space equally. Example: set to 2 for a "description" column that should be
   * twice as wide as a typical "status" column.
   */
  width?: number;

  /** Text alignment within the column. Defaults to 'left'. */
  align?: 'left' | 'center' | 'right';
}

export interface PdfExportOptions {
  /** PDF filename (without extension). Defaults to 'export'. */
  filename?: string;

  /** Document title rendered at the top of the first page. */
  title?: string;

  /** Optional subtitle line rendered below the title. */
  subtitle?: string;

  /**
   * Page orientation. The service auto-selects 'landscape' when there are
   * more than 5 columns unless you override this explicitly.
   */
  orientation?: 'portrait' | 'landscape';

  /** ISO 216 page size. Defaults to 'a4'. */
  pageSize?: 'a4' | 'a3' | 'letter';

  /**
   * Whether to include a page footer with page number and export timestamp.
   * Defaults to true.
   */
  includeFooter?: boolean;
}

export interface PdfExportResult {
  success: boolean;
  filename: string;
  rowCount: number;
  error?: string;
}

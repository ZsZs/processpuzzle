/**
 * How a bound value is shown — one vocabulary for a document's `dataField` node and the `entity-table`
 * widget's columns, so a value reads the same wherever it is placed.
 *
 * `currency:<ISO 4217 code>` carries its currency in the format itself, because the code is a property of the
 * placement (this invoice is in EUR), not of the number.
 */
export type ValueFormat = 'text' | 'number' | 'date' | 'datetime' | `currency:${string}`;

export const VALUE_FORMATS: readonly ValueFormat[] = ['text', 'number', 'date', 'datetime', 'currency:EUR', 'currency:USD', 'currency:HUF'];

/**
 * The value at a dot-separated `path` below `root`: `customer.address.city`. An empty path is the root itself.
 *
 * Absent anywhere along the way is `undefined`, never an error — a report is rendered against data the
 * author did not see, and one missing attribute must not take the page with it.
 */
export function valueAtPath(root: unknown, path: string | undefined): unknown {
  if (!path) return root;
  let current: unknown = root;
  for (const segment of path.split('.')) {
    if (current === null || typeof current !== 'object' || Array.isArray(current)) return undefined;
    current = (current as Record<string, unknown>)[segment];
  }
  return current;
}

/**
 * `value` as text in `format` for `locale`. `null` and `undefined` are the empty string; a value the format
 * cannot read — a word given `number`, an unparsable date — falls back to its plain text rather than `NaN`.
 * An object (an ADDRESS, an embedded component) is the join of its non-empty scalar values.
 *
 * `format` is a {@link ValueFormat}, typed as a plain string because it arrives from stored JSON; an unknown
 * one reads as `text`.
 */
export function formatValue(value: unknown, format: string | undefined, locale: string | undefined): string {
  if (value === null || value === undefined) return '';
  const effective = format ?? 'text';
  if (effective === 'number' || effective.startsWith('currency:')) return formatNumber(value, effective, locale);
  if (effective === 'date' || effective === 'datetime') return formatDate(value, effective, locale);
  return asText(value);
}

function formatNumber(value: unknown, format: string, locale: string | undefined): string {
  const number = typeof value === 'number' ? value : Number(value);
  if (typeof value === 'boolean' || value === '' || !Number.isFinite(number)) return asText(value);
  const options: Intl.NumberFormatOptions = format.startsWith('currency:') ? { style: 'currency', currency: format.slice('currency:'.length) } : {};
  try {
    return new Intl.NumberFormat(locale, options).format(number);
  } catch {
    // An unknown currency code is an authoring mistake; show the number rather than nothing.
    return new Intl.NumberFormat(locale).format(number);
  }
}

function formatDate(value: unknown, format: 'date' | 'datetime', locale: string | undefined): string {
  let date: Date | undefined;
  if (value instanceof Date) date = value;
  else if (typeof value === 'string' || typeof value === 'number') date = new Date(value);
  if (!date || Number.isNaN(date.getTime())) return asText(value);
  const options: Intl.DateTimeFormatOptions = format === 'date' ? { dateStyle: 'medium' } : { dateStyle: 'medium', timeStyle: 'short' };
  return new Intl.DateTimeFormat(locale, options).format(date);
}

function asText(value: unknown): string {
  if (Array.isArray(value)) return value.map(asText).filter(Boolean).join(', ');
  if (typeof value === 'object' && value !== null) {
    return Object.values(value)
      .filter((part) => part !== null && part !== undefined && typeof part !== 'object')
      .map(String)
      .filter((part) => part.trim() !== '')
      .join(', ');
  }
  return String(value);
}

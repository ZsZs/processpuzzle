/**
 * The named display style of a DATE / DATE_TIME attribute.
 *
 * A designer picks a style, not a pattern: the style is rendered with `Intl.DateTimeFormat` in the *active*
 * language, so `short` reads `09.12.1960` under `de` and `1960. 12. 09.` under `hu` without anyone authoring
 * either. Mirrors the contract's `AttributeDateFormat`.
 */
export type DateStyle = 'none' | 'short' | 'medium' | 'long' | 'full';
export type TimeStyle = 'none' | 'short' | 'medium' | 'long';
export type DateFormat = { dateStyle?: DateStyle; timeStyle?: TimeStyle };

export const DATE_STYLES: readonly DateStyle[] = ['none', 'short', 'medium', 'long', 'full'];
export const TIME_STYLES: readonly TimeStyle[] = ['none', 'short', 'medium', 'long'];

export const DEFAULT_DATE_FORMAT: Readonly<Required<DateFormat>> = { dateStyle: 'medium', timeStyle: 'none' };
export const DEFAULT_DATE_TIME_FORMAT: Readonly<Required<DateFormat>> = { dateStyle: 'medium', timeStyle: 'short' };

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * The format an attribute is rendered with: its own `dateFormat`, each missing style filled from the value
 * kind's default. A descriptor carries no value kind, so a hand-written DATE control defaults to a date; the
 * metadata path resolves DATE_TIME here before the descriptor is built (see `attrDescriptorOf`).
 */
export function effectiveDateFormat(attr: { dateFormat?: DateFormat } | undefined, valueKind: 'DATE' | 'DATE_TIME' = 'DATE'): Required<DateFormat> {
  const fallback = valueKind === 'DATE_TIME' ? DEFAULT_DATE_TIME_FORMAT : DEFAULT_DATE_FORMAT;
  return { dateStyle: attr?.dateFormat?.dateStyle ?? fallback.dateStyle, timeStyle: attr?.dateFormat?.timeStyle ?? fallback.timeStyle };
}

/** Whether the format edits a calendar day only, stored as a local `YYYY-MM-DD` rather than an instant. */
export function isDateOnly(format: DateFormat): boolean {
  return (format.timeStyle ?? 'none') === 'none';
}

/** The `Intl.DateTimeFormat` options of a format; a `none` style is left out rather than passed through. */
export function intlOptionsOf(format: DateFormat): Intl.DateTimeFormatOptions {
  const options: Intl.DateTimeFormatOptions = {};
  if (format.dateStyle && format.dateStyle !== 'none') options.dateStyle = format.dateStyle;
  if (format.timeStyle && format.timeStyle !== 'none') options.timeStyle = format.timeStyle;
  if (!options.dateStyle && !options.timeStyle) options.dateStyle = DEFAULT_DATE_FORMAT.dateStyle as Intl.DateTimeFormatOptions['dateStyle'];
  return options;
}

/**
 * A stored value as a `Date`, or `undefined` when it is not one. A bare `YYYY-MM-DD` is read as *local*
 * midnight: `new Date('1960-12-09')` is UTC midnight, which west of Greenwich is the evening before.
 */
export function toDateValue(value: unknown): Date | undefined {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? undefined : value;
  if (typeof value !== 'string' || value.trim() === '') return undefined;
  const dateOnly = DATE_ONLY.exec(value.trim());
  if (dateOnly) return new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]));
  if (!/^\d{4}-\d{2}-\d{2}T/.test(value.trim())) return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

/** A picked date in the shape it is saved in: a local `YYYY-MM-DD` for a date-only format, the instant otherwise. */
export function toStoredDate(date: Date, format: DateFormat): string | Date {
  if (!isDateOnly(format)) return date;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Renders `value` in `locale`; a value that is not a date is returned unchanged. */
export function formatDateValue<T>(value: T, format: DateFormat, locale: string): string | T {
  const date = toDateValue(value);
  if (!date) return value;
  return new Intl.DateTimeFormat(locale, intlOptionsOf(format)).format(date);
}

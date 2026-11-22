import { inject, Pipe, PipeTransform } from '@angular/core';
import { injectActiveLang } from '@processpuzzle/util';
import { formatDimensions, formatMeasure, formatMoney, formatQuantity } from './measure-format';
import { UNIT_CATALOG } from './unit-catalog.token';

/**
 * A `QUANTITY` value on one line, the number in the active language's notation: `{{ row[attr.attrName] | ppQuantity }}`.
 * Impure so it re-renders on a language switch, as `ppDate` and `ppAddress` do.
 */
@Pipe({ name: 'ppQuantity', standalone: true, pure: false })
export class QuantityPipe implements PipeTransform {
  private readonly activeLang = injectActiveLang();
  private readonly catalog = inject(UNIT_CATALOG);

  transform(value: unknown): string {
    return formatQuantity(value, this.activeLang(), this.catalog);
  }
}

/** A `MONEY` value in the active language's currency notation: `{{ row[attr.attrName] | ppMoney }}`. */
@Pipe({ name: 'ppMoney', standalone: true, pure: false })
export class MoneyPipe implements PipeTransform {
  private readonly activeLang = injectActiveLang();
  private readonly catalog = inject(UNIT_CATALOG);

  transform(value: unknown): string {
    return formatMoney(value, this.activeLang(), this.catalog);
  }
}

/** A `DIMENSIONS` value on one line: `{{ row[attr.attrName] | ppDimensions }}`. */
@Pipe({ name: 'ppDimensions', standalone: true, pure: false })
export class DimensionsPipe implements PipeTransform {
  private readonly activeLang = injectActiveLang();
  private readonly catalog = inject(UNIT_CATALOG);

  transform(value: unknown): string {
    return formatDimensions(value, this.activeLang(), this.catalog);
  }
}

/**
 * Whatever an attribute holds, when it may be a measure: `{{ row[attr.attrName] | ppMeasure }}`. A calculated attribute is
 * the case — a calculation wrote it, its control type says so and no longer says money or quantity. Anything that is not a
 * measure shows as it is.
 */
@Pipe({ name: 'ppMeasure', standalone: true, pure: false })
export class MeasurePipe implements PipeTransform {
  private readonly activeLang = injectActiveLang();
  private readonly catalog = inject(UNIT_CATALOG);

  transform(value: unknown): string {
    return formatMeasure(value, this.activeLang(), this.catalog) ?? (value === null || value === undefined ? '' : String(value));
  }
}

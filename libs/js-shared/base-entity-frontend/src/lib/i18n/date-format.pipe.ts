import { Pipe, PipeTransform } from '@angular/core';
import { injectActiveLang } from '@processpuzzle/util';
import { DateFormat, effectiveDateFormat, formatDateValue } from '../base-entity/date-format';

/**
 * Renders a DATE / DATE_TIME value in its attribute's {@link DateFormat}, in the active language.
 *
 * Usage: `{{ row[attr.attrName] | ppDate: attr.dateFormat }}`. A value that is not a date passes through.
 *
 * Impure so it re-renders on a language switch; reading the language signal inside `transform` is what
 * marks the view for check when it changes.
 */
@Pipe({ name: 'ppDate', standalone: true, pure: false })
export class DateFormatPipe implements PipeTransform {
  private readonly activeLang = injectActiveLang();

  transform(value: unknown, dateFormat?: DateFormat): unknown {
    return formatDateValue(value, effectiveDateFormat({ dateFormat }), this.activeLang());
  }
}

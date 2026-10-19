import { Pipe, PipeTransform } from '@angular/core';
import { injectActiveLang } from '@processpuzzle/util';
import { formatAddress } from './address-formats';
import { countryName } from './countries';
import { addressOf } from './postal-address';

/**
 * An `ADDRESS` value on one line, in its country's order, the country named in the active language.
 *
 * Usage: `{{ row[attr.attrName] | ppAddress }}`. Impure so it re-renders on a language switch, as `ppDate` does.
 */
@Pipe({ name: 'ppAddress', standalone: true, pure: false })
export class AddressPipe implements PipeTransform {
  private readonly activeLang = injectActiveLang();

  transform(value: unknown): string {
    const language = this.activeLang();
    return formatAddress(addressOf(value), (code) => countryName(code, language));
  }
}

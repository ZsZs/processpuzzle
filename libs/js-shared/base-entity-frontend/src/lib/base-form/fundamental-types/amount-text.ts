import { ValidatorFn } from '@angular/forms';

const AMOUNT = /^-?\d+(\.\d+)?$/;

/** What the user typed, ready to be read as a decimal: white space dropped and a decimal comma made a point. */
export function cleanAmount(text: string | null | undefined): string {
  return (text ?? '').replace(/\s+/g, '').replace(',', '.');
}

export function isAmountText(text: string): boolean {
  return AMOUNT.test(text);
}

/** The digits after the decimal point of an amount that is valid decimal text. */
export function decimalPlaces(text: string): number {
  const point = text.indexOf('.');
  return point < 0 ? 0 : text.length - point - 1;
}

/** An amount shown with at least `places` decimal places: `1299.9` as `1299.90` in euro. Longer amounts are left as they are. */
export function paddedToPlaces(text: string, places: number | undefined): string {
  if (!places || places <= 0) return text;
  const missing = places - decimalPlaces(text);
  if (missing <= 0) return text;
  return (text.includes('.') ? text : text + '.') + '0'.repeat(missing);
}

/**
 * Validates an amount field: empty is fine (a required attribute says so itself), anything else must be a plain
 * decimal with at most `maxPlaces()` decimal places when the attribute limits them.
 */
export function amountValidator(maxPlaces: () => number | undefined): ValidatorFn {
  return (control) => {
    const text = cleanAmount(control.value as string | null);
    if (!text) return null;
    if (!isAmountText(text)) return { amount: true };
    const max = maxPlaces();
    return max !== undefined && decimalPlaces(text) > max ? { scale: { max } } : null;
  };
}

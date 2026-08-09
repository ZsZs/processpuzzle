/**
 * Text matching for Playwright locator filters.
 *
 * `hasText` treats a string as a substring match, so filtering a list of rows by an identification value picks
 * up every row whose value merely contains it — "Order 1" also matches "Order 10". A `RegExp` anchored at both
 * ends is what makes the match exact, and the value has to be escaped before it can be embedded in one.
 */

/** `value` with every regular-expression metacharacter escaped, so it matches itself literally. */
export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, String.raw`\$&`);
}

/** A pattern matching `value` and nothing longer — what `hasText` needs to mean "this row", not "a row like it". */
export function exactText(value: string): RegExp {
  return new RegExp(`^${escapeRegExp(value)}$`);
}

/**
 * How many values an attribute holds: the lower bound is 0 or 1, the upper bound 1, a fixed maximum `x`
 * (given by `maxOccurs`) or unbounded `n`. Absent means a single value. Value counts are not validated yet.
 */
export type Multiplicity = '0..1' | '0..x' | '0..n' | '1..x' | '1..n';

export const MULTIPLICITIES: readonly Multiplicity[] = ['0..1', '0..x', '0..n', '1..x', '1..n'];

export interface MultiplicityHolder {
  multiplicity?: Multiplicity;
  maxOccurs?: number;
}

/** True for the `x` forms, whose upper bound is `maxOccurs`. */
export function isBoundedByMaxOccurs(multiplicity: Multiplicity | undefined): boolean {
  return multiplicity === '0..x' || multiplicity === '1..x';
}

/** The upper bound of the attribute's multiplicity; `Infinity` for `n`, and a missing `maxOccurs` counts as 1. */
export function upperBound(attr: MultiplicityHolder): number {
  if (attr.multiplicity === '0..n' || attr.multiplicity === '1..n') return Infinity;
  if (isBoundedByMaxOccurs(attr.multiplicity)) return attr.maxOccurs ?? 1;
  return 1;
}

/** An attribute is multi-valued when its upper bound exceeds 1. */
export function isMultiValued(attr: MultiplicityHolder): boolean {
  return upperBound(attr) > 1;
}

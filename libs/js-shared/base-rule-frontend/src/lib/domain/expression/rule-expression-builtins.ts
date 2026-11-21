import { dims, isMeasureRef, money, qty } from '../measure-bindings';
import { FORBIDDEN_NAMES } from './rule-expression-parser';

/**
 * What a rule expression can name and call. Nothing else of the page is reachable: there is no `window`, `document`,
 * `fetch`, `localStorage` or `Function`, and the only functions a rule can run are its own arrow functions, the ones listed
 * here, and the methods of the value they are called on, by the lists below. Every method listed leaves its value as it was —
 * a rule reads the entity, it never changes it.
 */

const ARRAY_METHODS: ReadonlySet<string> = new Set([
  'at', 'concat', 'every', 'filter', 'find', 'findIndex', 'findLast', 'findLastIndex', 'flat', 'flatMap', 'includes', 'indexOf',
  'join', 'lastIndexOf', 'map', 'reduce', 'reduceRight', 'slice', 'some', 'toString',
]);

const STRING_METHODS: ReadonlySet<string> = new Set([
  'at', 'charAt', 'concat', 'endsWith', 'includes', 'indexOf', 'lastIndexOf', 'localeCompare', 'match', 'padEnd', 'padStart',
  'repeat', 'replace', 'replaceAll', 'slice', 'split', 'startsWith', 'substring', 'toLowerCase', 'toString', 'toUpperCase', 'trim',
  'trimEnd', 'trimStart',
]);

const NUMBER_METHODS: ReadonlySet<string> = new Set(['toFixed', 'toPrecision', 'toString']);

const DATE_METHODS: ReadonlySet<string> = new Set([
  'getDate', 'getDay', 'getFullYear', 'getHours', 'getMilliseconds', 'getMinutes', 'getMonth', 'getSeconds', 'getTime', 'toISOString',
]);

/** The functions a rule may call by their own name, and the members of the namespaces it may call. */
const CALLABLE_GLOBALS: ReadonlySet<unknown> = new Set([Number, String, Boolean, parseInt, parseFloat, isNaN, isFinite, qty, money, dims]);

const NAMESPACE_METHODS: ReadonlyMap<unknown, ReadonlySet<string>> = new Map<unknown, ReadonlySet<string>>([
  [Math, new Set(Object.getOwnPropertyNames(Math))],
  [Number, new Set(['isFinite', 'isInteger', 'isNaN', 'isSafeInteger', 'parseFloat', 'parseInt'])],
  [String, new Set(['fromCharCode'])],
  [Array, new Set(['isArray'])],
  [Object, new Set(['entries', 'keys', 'values'])],
  [Date, new Set(['now', 'parse', 'UTC'])],
]);

/** The names a rule starts with; the entity is added under `entity`. */
export const GLOBAL_VALUES: ReadonlyMap<string, unknown> = new Map<string, unknown>([
  ['undefined', undefined],
  ['NaN', Number.NaN],
  ['Infinity', Number.POSITIVE_INFINITY],
  ['Math', Math],
  ['Number', Number],
  ['String', String],
  ['Boolean', Boolean],
  ['Array', Array],
  ['Object', Object],
  ['Date', Date],
  ['parseInt', parseInt],
  ['parseFloat', parseFloat],
  ['isNaN', isNaN],
  ['isFinite', isFinite],
  ['qty', qty],
  ['money', money],
  ['dims', dims],
]);

/** The arrow functions the interpreter made — the only functions of a rule's own. */
const CLOSURES = new WeakSet<object>();

export function registerClosure(fn: object): void {
  CLOSURES.add(fn);
}

/** Whether `name` may be called on `value`, by what kind of value it is. */
export function methodAllowed(value: unknown, name: string): boolean {
  if (FORBIDDEN_NAMES.has(name)) return false;
  if (Array.isArray(value)) return ARRAY_METHODS.has(name);
  if (typeof value === 'string') return STRING_METHODS.has(name);
  if (typeof value === 'number') return NUMBER_METHODS.has(name);
  if (value instanceof RegExp) return name === 'test';
  if (value instanceof Date) return DATE_METHODS.has(name);
  if (isMeasureRef(value)) return typeof (value as Record<string, unknown>)[name] === 'function';
  return NAMESPACE_METHODS.get(value)?.has(name) ?? false;
}

/** Whether a function value may be called as a plain function: a rule's own arrow function, or a listed global. */
export function functionAllowed(fn: unknown): boolean {
  return typeof fn === 'function' && (CLOSURES.has(fn) || CALLABLE_GLOBALS.has(fn));
}

import type { StoredDocument } from './base-document.model.js';
import { DEFAULT_PAGE_SIZE } from './base-document.config.js';

/**
 * `where` / `order` / `page` / `size` of `listDocuments`, evaluated in memory.
 *
 * Firestore cannot run this query: it has no substring or case-insensitive compare, no top-level
 * `OR` across different fields, and an equality filter combined with an unrelated `orderBy` requires
 * a hand-maintained composite index for every field pair a user might pick. Worse, `orderBy` silently
 * *excludes* documents that lack the sort field, so ordering a list by `subject` would make every
 * document without a subject disappear.
 *
 * Evaluating here avoids all four, keeps `firestore.indexes.json` empty and makes `totalElements`
 * exact. What it costs is reads proportional to the organization's document count — bounded by
 * `MAX_LIST_SCAN` in the caller.
 *
 * The supported grammar is exactly what the frontend's `toRsql` can emit
 * (`base-entity-frontend/src/lib/base-entity-service/rsql.ts`): the eight comparison operators below,
 * `;` for AND, `,` for OR, and parenthesised groups. Anything else is rejected rather than guessed at,
 * so an unsupported filter surfaces as a 400 instead of silently matching everything.
 */

export type DocumentPredicate = (document: StoredDocument) => boolean;

const OPERATORS = ['==', '!=', '=lt=', '=le=', '=gt=', '=ge=', '=in=', '=out='] as const;
type Operator = (typeof OPERATORS)[number];

const SELECTOR_PATTERN = /[A-Za-z0-9_.-]/;
const OPERATOR_PATTERN = /^=[a-zA-Z]+=/;

export class QuerySyntaxError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'QuerySyntaxError';
  }
}

export interface SortOrder {
  readonly property: string;
  readonly descending: boolean;
}

export interface DocumentQuery {
  readonly where?: string;
  readonly order?: string;
  readonly page?: string;
  readonly size?: string;
}

export interface PagedDocuments {
  readonly content: StoredDocument[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly number: number;
  readonly size: number;
}

export function parseWhere(where: string | undefined): DocumentPredicate {
  if (where === undefined || where.trim() === '') return () => true;

  const parser = new RsqlParser(where);
  const predicate = parser.parseOr();
  parser.expectEnd();
  return predicate;
}

/**
 * Port of `com.processpuzzle.core.rsql.SortParser`: comma-separated tokens where an `asc`/`desc`
 * token optionally follows a property, so `title,desc,slug` sorts by title descending then slug
 * ascending. Kept identical so the two backends order a list the same way.
 */
export function parseOrder(order: string | undefined): SortOrder[] {
  if (order === undefined || order.trim() === '') return [];

  const tokens = order.split(',');
  const orders: SortOrder[] = [];
  let index = 0;

  while (index < tokens.length) {
    const property = tokens[index].trim();
    if (property === '') throw new QuerySyntaxError(`Invalid order: empty property in '${order}'.`);

    const next = tokens[index + 1]?.trim().toLowerCase();
    if (next === 'asc' || next === 'desc') {
      orders.push({ property, descending: next === 'desc' });
      index += 2;
    } else {
      orders.push({ property, descending: false });
      index += 1;
    }
  }

  return orders;
}

export function parsePageNumber(value: string | undefined, parameter: 'page' | 'size', fallback: number): number {
  if (value === undefined || value.trim() === '') return fallback;

  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) throw new QuerySyntaxError(`Invalid '${parameter}': '${value}' is not a non-negative integer.`);
  return parsed;
}

/** Filters, sorts and pages in that order, so `totalElements` counts matches rather than reads. */
export function applyQuery(documents: readonly StoredDocument[], query: DocumentQuery): PagedDocuments {
  const predicate = parseWhere(query.where);
  const orders = parseOrder(query.order);
  const page = parsePageNumber(query.page, 'page', 0);
  const size = parsePageNumber(query.size, 'size', DEFAULT_PAGE_SIZE);

  const matched = documents.filter(predicate);
  const sorted = sortDocuments(matched, orders);
  const start = page * size;

  return {
    content: size === 0 ? [] : sorted.slice(start, start + size),
    totalElements: matched.length,
    totalPages: size === 0 ? 0 : Math.ceil(matched.length / size),
    number: page,
    size,
  };
}

/** `id` breaks ties so paging is stable — two documents with the same title must not swap pages. */
function sortDocuments(documents: readonly StoredDocument[], orders: readonly SortOrder[]): StoredDocument[] {
  return [...documents].sort((left, right) => {
    for (const order of orders) {
      const comparison = compare(fieldOf(left, order.property), fieldOf(right, order.property));
      if (comparison !== 0) return order.descending ? -comparison : comparison;
    }
    return left.id.localeCompare(right.id);
  });
}

function fieldOf(document: StoredDocument, property: string): unknown {
  return property.split('.').reduce<unknown>((value, segment) => (typeof value === 'object' && value !== null ? (value as Record<string, unknown>)[segment] : undefined), document);
}

/** Nulls sort last; numbers numerically; everything else by its string form (ISO dates included). */
function compare(left: unknown, right: unknown): number {
  if (left === right) return 0;
  if (left === null || left === undefined) return 1;
  if (right === null || right === undefined) return -1;
  if (typeof left === 'number' && typeof right === 'number') return left - right;
  return String(left).localeCompare(String(right));
}

function coerce(token: string, sample: unknown): unknown {
  if (token === 'null') return null;
  if (typeof sample === 'number') return Number(token);
  if (typeof sample === 'boolean') return token === 'true';
  return token;
}

function equals(field: unknown, token: string): boolean {
  // An array field matches when it contains the value — `array-contains` is what the frontend's
  // OP_MAP funnels into `=in=`, and role lists are the fields users actually filter on.
  if (Array.isArray(field)) return field.some((item) => String(item) === token);
  return coerce(token, field) === field;
}

function evaluate(field: unknown, operator: Operator, value: string | string[]): boolean {
  switch (operator) {
    case '==':
      return equals(field, asScalar(operator, value));
    case '!=':
      return !equals(field, asScalar(operator, value));
    case '=in=':
      return asList(operator, value).some((token) => equals(field, token));
    case '=out=':
      return !asList(operator, value).some((token) => equals(field, token));
    case '=lt=':
      return relational(field, asScalar(operator, value)) < 0;
    case '=le=':
      return relational(field, asScalar(operator, value)) <= 0;
    case '=gt=':
      return relational(field, asScalar(operator, value)) > 0;
    case '=ge=':
      return relational(field, asScalar(operator, value)) >= 0;
  }
}

/** Missing fields never satisfy an ordering comparison, in either direction. */
function relational(field: unknown, token: string): number {
  if (field === null || field === undefined) return Number.NaN;
  return compare(field, coerce(token, field));
}

function asScalar(operator: Operator, value: string | string[]): string {
  if (Array.isArray(value)) throw new QuerySyntaxError(`Operator '${operator}' takes a single value, not a list.`);
  return value;
}

function asList(operator: Operator, value: string | string[]): string[] {
  return Array.isArray(value) ? value : [value];
}

/**
 * Recursive-descent parser over the RSQL subset. Hand-written rather than pulled from npm because the
 * grammar is eight operators wide and the dependency would still need a translation layer onto
 * `StoredDocument`.
 */
class RsqlParser {
  private position = 0;

  constructor(private readonly text: string) {}

  parseOr(): DocumentPredicate {
    const alternatives = [this.parseAnd()];
    while (this.consumeIf(',')) alternatives.push(this.parseAnd());
    if (alternatives.length === 1) return alternatives[0];
    return (document) => alternatives.some((predicate) => predicate(document));
  }

  expectEnd(): void {
    this.skipSpace();
    if (this.position < this.text.length) throw new QuerySyntaxError(`Unexpected '${this.text[this.position]}' at position ${this.position} in '${this.text}'.`);
  }

  private parseAnd(): DocumentPredicate {
    const terms = [this.parseTerm()];
    while (this.consumeIf(';')) terms.push(this.parseTerm());
    if (terms.length === 1) return terms[0];
    return (document) => terms.every((predicate) => predicate(document));
  }

  private parseTerm(): DocumentPredicate {
    this.skipSpace();
    if (this.consumeIf('(')) {
      const grouped = this.parseOr();
      this.expect(')');
      return grouped;
    }
    return this.parseComparison();
  }

  private parseComparison(): DocumentPredicate {
    const selector = this.readSelector();
    const operator = this.readOperator();
    const value = this.readValue();
    return (document) => evaluate(fieldOf(document, selector), operator, value);
  }

  private readSelector(): string {
    this.skipSpace();
    const start = this.position;
    while (this.position < this.text.length && SELECTOR_PATTERN.test(this.text[this.position])) this.position += 1;

    const selector = this.text.slice(start, this.position);
    if (selector === '') throw new QuerySyntaxError(`Expected a field name at position ${start} in '${this.text}'.`);
    return selector;
  }

  private readOperator(): Operator {
    const remainder = this.text.slice(this.position);
    if (remainder.startsWith('==') || remainder.startsWith('!=')) {
      this.position += 2;
      return remainder.slice(0, 2) as Operator;
    }

    const match = OPERATOR_PATTERN.exec(remainder);
    if (match && (OPERATORS as readonly string[]).includes(match[0])) {
      this.position += match[0].length;
      return match[0] as Operator;
    }

    const shown = match?.[0] ?? remainder.slice(0, 4);
    throw new QuerySyntaxError(`Unsupported operator '${shown}' at position ${this.position} in '${this.text}'. Supported: ${OPERATORS.join(' ')}.`);
  }

  private readValue(): string | string[] {
    this.skipSpace();
    if (!this.consumeIf('(')) return this.readScalar();

    const values = [this.readScalar()];
    while (this.consumeIf(',')) values.push(this.readScalar());
    this.expect(')');
    return values;
  }

  private readScalar(): string {
    this.skipSpace();
    if (this.text[this.position] === '"') return this.readQuoted();

    const start = this.position;
    while (this.position < this.text.length && !';,)'.includes(this.text[this.position])) this.position += 1;
    return this.text.slice(start, this.position).trim();
  }

  private readQuoted(): string {
    this.position += 1;
    let value = '';
    while (this.position < this.text.length && this.text[this.position] !== '"') {
      if (this.text[this.position] === '\\' && this.position + 1 < this.text.length) this.position += 1;
      value += this.text[this.position];
      this.position += 1;
    }
    this.expect('"');
    return value;
  }

  private consumeIf(character: string): boolean {
    this.skipSpace();
    if (this.text[this.position] !== character) return false;
    this.position += 1;
    return true;
  }

  private expect(character: string): void {
    if (!this.consumeIf(character)) throw new QuerySyntaxError(`Expected '${character}' at position ${this.position} in '${this.text}'.`);
  }

  private skipSpace(): void {
    while (this.position < this.text.length && this.text[this.position] === ' ') this.position += 1;
  }
}

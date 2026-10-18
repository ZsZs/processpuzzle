import { isMap, isScalar, isSeq, parseDocument, type Document, type Node, type YAMLMap } from 'yaml';

export const HIT_POLICIES = ['FIRST', 'UNIQUE', 'ANY', 'COLLECT'] as const;
export type HitPolicy = (typeof HIT_POLICIES)[number];

/**
 * One rule: a unary test per input (`-` matches anything) and the FEEL expression of its output — the YAML's
 * `then`, named `result` here because an object with a `then` property passes for a promise.
 */
export interface TableRuleModel {
  when: string[];
  result: string;
}

/** A decision table of a calculation source, as the table editor edits it. */
export interface DecisionTableModel {
  name: string;
  hitPolicy: HitPolicy;
  inputs: string[];
  output: string;
  rules: TableRuleModel[];
}

const NUMBER = /^-?\d+(\.\d+)?$/;
const THEN = 'then';

/**
 * The source's YAML errors — empty when it parses. The editors refuse to rewrite a source that does not parse,
 * since there is no tree to put an edit into.
 */
export function yamlErrorsOf(source: string): string[] {
  return parseDocument(source).errors.map((error) => error.message);
}

/** The decision tables the source declares, in source order; none when it does not parse. */
export function readTables(source: string): DecisionTableModel[] {
  const document = parseDocument(source);
  const tables = document.errors.length === 0 ? document.get('tables', true) : undefined;
  if (!isMap(tables)) return [];
  return tables.items.map((pair) => toModel(String(isScalar(pair.key) ? pair.key.value : pair.key), pair.value as Node | null));
}

/**
 * The source with one table written — replacing the table of the same name, or of `previousName` when the table
 * was renamed, or added at the end. Everything else in the document stays as it was, comments included; the
 * table's own leading comment survives the rewrite.
 *
 * @throws Error when the source does not parse
 */
export function writeTable(source: string, table: DecisionTableModel, previousName?: string): string {
  const document = parsed(source);
  const tables = tablesOf(document);
  const replaced = (previousName ? tables.get(previousName, true) : undefined) ?? tables.get(table.name, true);
  const node = document.createNode({
    hitPolicy: table.hitPolicy,
    inputs: table.inputs,
    output: table.output,
    // A Map rather than an object literal, for the reason TableRuleModel gives.
    rules: table.rules.map((rule) => new Map<string, unknown>([['when', rule.when], [THEN, outputValue(rule.result)]])),
  }) as YAMLMap;
  flowStyle(node);
  if (replaced && isMap(replaced)) {
    node.commentBefore = replaced.commentBefore;
    node.comment = replaced.comment;
  }
  if (previousName && previousName !== table.name && tables.has(previousName)) {
    const index = tables.items.findIndex((pair) => isScalar(pair.key) && pair.key.value === previousName);
    tables.items[index] = document.createPair(table.name, node);
  } else {
    tables.set(table.name, node);
  }
  return document.toString();
}

/** The source without the named table. @throws Error when the source does not parse */
export function removeTable(source: string, name: string): string {
  const document = parsed(source);
  tablesOf(document).delete(name);
  return document.toString();
}

/** A table to start from: one input, one rule that matches anything. */
export function newTable(name: string): DecisionTableModel {
  return { name, hitPolicy: 'FIRST', inputs: ['input'], output: 'output', rules: [{ when: ['-'], result: '0' }] };
}

function parsed(source: string): Document {
  const document = parseDocument(source);
  if (document.errors.length > 0) throw new Error(document.errors[0].message);
  return document;
}

function tablesOf(document: Document): YAMLMap {
  const existing = document.get('tables', true);
  if (isMap(existing)) return existing;
  const tables = document.createNode({}) as YAMLMap;
  document.set('tables', tables);
  return tables;
}

/** Inputs and rules on one line each, as the proposal writes them. */
function flowStyle(node: YAMLMap): void {
  const inputs = node.get('inputs', true);
  if (isSeq(inputs)) inputs.flow = true;
  const rules = node.get('rules', true);
  if (isSeq(rules)) rules.items.forEach((rule) => isMap(rule) && (rule.flow = true));
}

/** A number stays a number, so `then: 0.10` is not rewritten as the string `'0.10'`. */
function outputValue(result: string): string | number {
  return NUMBER.test(result.trim()) ? Number(result.trim()) : result;
}

function toModel(name: string, node: Node | null): DecisionTableModel {
  const value = (isMap(node) ? node.toJSON() : {}) as Record<string, unknown>;
  const inputs = Array.isArray(value['inputs']) ? (value['inputs'] as unknown[]).map((input) => textOf(input)) : [];
  const rules = Array.isArray(value['rules']) ? (value['rules'] as Record<string, unknown>[]) : [];
  const hitPolicy = textOf(value['hitPolicy'], 'FIRST').toUpperCase();
  return {
    name,
    hitPolicy: (HIT_POLICIES as readonly string[]).includes(hitPolicy) ? (hitPolicy as HitPolicy) : 'FIRST',
    inputs,
    output: textOf(value['output']),
    rules: rules.map((rule) => ({
      when: Array.isArray(rule?.['when']) ? (rule['when'] as unknown[]).map((cell) => textOf(cell, '-')) : [],
      result: textOf(rule?.[THEN]),
    })),
  };
}

/** A scalar as YAML wrote it; `fallback` for none, and anything structured as JSON rather than `[object Object]`. */
function textOf(value: unknown, fallback = ''): string {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') return value.toString();
  return JSON.stringify(value);
}

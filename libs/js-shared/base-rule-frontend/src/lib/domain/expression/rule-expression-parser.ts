import { parse } from 'acorn';
import type { ArrowFunctionExpression, Expression, ExpressionStatement, Node, Program, Property } from 'estree';

/** Why a rule expression cannot be compiled or run: unsupported syntax, a name a rule may not touch, a refused operation. */
export class RuleExpressionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'RuleExpressionError';
  }
}

/**
 * Names a rule can never read: through them a value reaches its constructor, and with it the `Function` constructor
 * and everything of the page. The interpreter refuses them wherever they appear, computed or not.
 */
export const FORBIDDEN_NAMES: ReadonlySet<string> = new Set(['constructor', '__proto__', 'prototype']);

/** What a rule author is told when a construct is outside the language rules are written in. */
const UNSUPPORTED: Readonly<Record<string, string>> = {
  AssignmentExpression: 'an assignment — a rule only reads',
  UpdateExpression: '++ or -- — a rule only reads',
  FunctionExpression: 'a function expression — use an arrow function',
  ThisExpression: '`this`',
  TaggedTemplateExpression: 'a tagged template',
  SequenceExpression: 'a comma expression',
  AwaitExpression: '`await`',
  YieldExpression: '`yield`',
  ClassExpression: 'a class',
  ImportExpression: '`import()`',
  MetaProperty: '`new.target` or `import.meta`',
  Super: '`super`',
  ObjectPattern: 'destructuring',
  ArrayPattern: 'destructuring',
};

function unsupported(what: string): never {
  throw new RuleExpressionError(`${what} is not supported in a rule expression`);
}

/**
 * The AST of a rule expression, checked: one expression (no statements), made only of the constructs the interpreter runs —
 * literals, names, property access, calls, arrow functions with an expression body, operators, `?:`, template strings,
 * array and object literals, optional chaining and `new Date(...)`. A rule is data a designer authors, so what it may do is
 * decided here, once, rather than discovered while it runs.
 */
export function parseRuleExpression(source: string): Expression {
  let program: Program;
  try {
    // Parenthesized so `{ ... }` is an object and not a block; the newline lets a trailing line comment end before the `)`.
    program = parse(`(${source}\n)`, { ecmaVersion: 2022, sourceType: 'script' }) as unknown as Program;
  } catch (error) {
    throw new RuleExpressionError(describeSyntaxError(error, source.length));
  }
  const [statement] = program.body;
  if (program.body.length !== 1 || statement.type !== 'ExpressionStatement') {
    throw new RuleExpressionError('a rule is a single expression');
  }
  const expression = (statement as ExpressionStatement).expression;
  check(expression);
  return expression;
}

/** acorn's message without its `(line:column)`, and the offset in the author's text rather than in the wrapped one. */
function describeSyntaxError(error: unknown, length: number): string {
  const raw = error instanceof Error ? error.message : String(error);
  const position = (error as { pos?: number }).pos;
  const message = raw.replace(/ \(\d+:\d+\)$/, '');
  return position === undefined ? message : `${message} at character ${Math.min(length, Math.max(0, position - 1))}`;
}

function check(node: Node | null): void {
  if (node === null) return; // a hole in an array literal
  switch (node.type) {
    case 'Literal':
    case 'Identifier':
      return;
    case 'MemberExpression':
      if (node.property.type === 'PrivateIdentifier') unsupported('a private member');
      if (!node.computed && node.property.type === 'Identifier') refuseForbidden(node.property.name);
      check(node.object);
      if (node.computed) check(node.property);
      return;
    case 'CallExpression':
      check(node.callee);
      node.arguments.forEach(check);
      return;
    case 'NewExpression':
      if (node.callee.type !== 'Identifier' || node.callee.name !== 'Date') unsupported('`new` of anything but Date');
      node.arguments.forEach(check);
      return;
    case 'ArrowFunctionExpression':
      checkArrow(node);
      return;
    case 'UnaryExpression':
      if (node.operator === 'delete' || node.operator === 'void') unsupported(`\`${node.operator}\``);
      check(node.argument);
      return;
    case 'BinaryExpression':
      if (node.operator === 'instanceof') unsupported('`instanceof`');
      check(node.left);
      check(node.right);
      return;
    case 'LogicalExpression':
      check(node.left);
      check(node.right);
      return;
    case 'ConditionalExpression':
      check(node.test);
      check(node.consequent);
      check(node.alternate);
      return;
    case 'TemplateLiteral':
      node.expressions.forEach(check);
      return;
    case 'ArrayExpression':
      node.elements.forEach(check);
      return;
    case 'ObjectExpression':
      node.properties.forEach((property) => (property.type === 'SpreadElement' ? unsupported('object spread') : checkProperty(property)));
      return;
    case 'SpreadElement':
      check(node.argument);
      return;
    case 'ChainExpression':
      check(node.expression);
      return;
    default:
      unsupported(UNSUPPORTED[node.type] ?? `\`${node.type}\``);
  }
}

function checkArrow(node: ArrowFunctionExpression): void {
  if (node.async || node.generator) unsupported('an async or generator function');
  if (node.body.type === 'BlockStatement') unsupported('an arrow function with a block body — write the rule as one expression');
  if (node.params.some((param) => param.type !== 'Identifier')) unsupported('destructuring, default or rest parameters');
  check(node.body);
}

function checkProperty(property: Property): void {
  if (property.kind !== 'init' || property.method) unsupported('a getter, setter or method in an object literal');
  if (property.computed) unsupported('a computed key in an object literal');
  if (property.key.type === 'Identifier') refuseForbidden(property.key.name);
  check(property.value as Node);
}

function refuseForbidden(name: string): void {
  if (FORBIDDEN_NAMES.has(name)) throw new RuleExpressionError(`'${name}' is not available in a rule expression`);
}

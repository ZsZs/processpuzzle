import type {
  ArrowFunctionExpression,
  Expression,
  Identifier,
  LogicalExpression,
  MemberExpression,
  NewExpression,
  Node,
  ObjectExpression,
  SimpleCallExpression,
  SpreadElement,
  TemplateLiteral,
  UnaryExpression,
} from 'estree';
import { functionAllowed, GLOBAL_VALUES, methodAllowed, registerClosure } from './rule-expression-builtins';
import { FORBIDDEN_NAMES, RuleExpressionError } from './rule-expression-parser';

/** What an optional chain (`a?.b`) answers once a link of it is missing; the chain turns it into `undefined`. */
const SHORT_CIRCUIT: unique symbol = Symbol('short-circuit');

/** The names visible at one point of a rule: its arrow functions' parameters, then the globals and `entity`. */
export class Scope {
  private readonly values = new Map<string, unknown>();

  constructor(private readonly parent?: Scope) {}

  define(name: string, value: unknown): void {
    this.values.set(name, value);
  }

  has(name: string): boolean {
    return this.values.has(name) || (this.parent?.has(name) ?? false);
  }

  lookup(name: string): unknown {
    if (this.values.has(name)) return this.values.get(name);
    if (this.parent) return this.parent.lookup(name);
    throw new ReferenceError(`${name} is not defined`);
  }
}

/** The scope a rule starts in: the allowed globals, and the entity under evaluation as `entity`. */
export function rootScope(entity: unknown): Scope {
  const scope = new Scope();
  GLOBAL_VALUES.forEach((value, name) => scope.define(name, value));
  scope.define('entity', entity);
  return scope;
}

/** Runs a checked rule expression. The interpreter executes the AST itself; no text becomes code. */
export function interpret(expression: Expression, scope: Scope): unknown {
  const result = evaluate(expression, scope);
  return result === SHORT_CIRCUIT ? undefined : result;
}

function evaluate(node: Node, scope: Scope): unknown {
  switch (node.type) {
    case 'Literal':
      return 'regex' in node && node.regex ? new RegExp(node.regex.pattern, node.regex.flags) : node.value;
    case 'Identifier':
      return scope.lookup(node.name);
    case 'TemplateLiteral':
      return templateValue(node, scope);
    case 'ArrayExpression':
      return spreadValues(node.elements, scope);
    case 'ObjectExpression':
      return objectValue(node, scope);
    case 'ArrowFunctionExpression':
      return closure(node, scope);
    case 'UnaryExpression':
      return unaryValue(node, scope);
    case 'BinaryExpression':
      return binaryValue(node.operator, evaluate(node.left, scope), evaluate(node.right, scope));
    case 'LogicalExpression':
      return logicalValue(node, scope);
    case 'ConditionalExpression':
      return evaluate(node.test, scope) ? evaluate(node.consequent, scope) : evaluate(node.alternate, scope);
    case 'MemberExpression':
      return memberValue(node, scope);
    case 'CallExpression':
      return callValue(node, scope);
    case 'NewExpression':
      return newValue(node, scope);
    case 'ChainExpression': {
      const value = evaluate(node.expression, scope);
      return value === SHORT_CIRCUIT ? undefined : value;
    }
    default:
      // The parser refused everything else; reaching here would mean the two have drifted apart.
      throw new RuleExpressionError(`${node.type} is not supported in a rule expression`);
  }
}

// region values

function templateValue(node: TemplateLiteral, scope: Scope): string {
  let text = node.quasis[0].value.cooked ?? '';
  node.expressions.forEach((expression, index) => {
    text += `${evaluate(expression, scope)}${node.quasis[index + 1].value.cooked ?? ''}`;
  });
  return text;
}

/** The values of an array literal's elements or a call's arguments, a `...spread` of an array flattened in place. */
function spreadValues(nodes: ReadonlyArray<Expression | SpreadElement | null>, scope: Scope): unknown[] {
  const values: unknown[] = [];
  for (const element of nodes) {
    if (element === null) {
      values.push(undefined);
    } else if (element.type === 'SpreadElement') {
      const spread = evaluate(element.argument, scope);
      if (!Array.isArray(spread)) throw new TypeError('only an array can be spread in a rule expression');
      values.push(...spread);
    } else {
      values.push(evaluate(element, scope));
    }
  }
  return values;
}

function objectValue(node: ObjectExpression, scope: Scope): Record<string, unknown> {
  const object: Record<string, unknown> = {};
  for (const property of node.properties) {
    if (property.type !== 'Property') throw new RuleExpressionError('object spread is not supported in a rule expression');
    const key = property.key.type === 'Identifier' ? property.key.name : String((property.key as { value: unknown }).value);
    object[key] = evaluate(property.value as Node, scope);
  }
  return object;
}

function closure(node: ArrowFunctionExpression, scope: Scope): (...args: unknown[]) => unknown {
  const names = node.params.map((param) => (param as Identifier).name);
  const fn = (...args: unknown[]): unknown => {
    const local = new Scope(scope);
    names.forEach((name, index) => local.define(name, args[index]));
    return interpret(node.body as Expression, local);
  };
  registerClosure(fn);
  return fn;
}

// endregion

// region operators

function unaryValue(node: UnaryExpression, scope: Scope): unknown {
  if (node.operator === 'typeof' && node.argument.type === 'Identifier' && !scope.has(node.argument.name)) {
    return 'undefined'; // `typeof undeclared` is a string, not a ReferenceError
  }
  const operand = evaluate(node.argument, scope);
  switch (node.operator) {
    case '!':
      return !operand;
    case '-':
      return -(operand as number);
    case '+':
      return +(operand as number);
    case '~':
      return ~(operand as number);
    case 'typeof':
      return typeof operand;
    default:
      throw new RuleExpressionError(`\`${node.operator}\` is not supported in a rule expression`);
  }
}

function logicalValue(node: LogicalExpression, scope: Scope): unknown {
  const left = evaluate(node.left, scope);
  switch (node.operator) {
    case '&&':
      return left ? evaluate(node.right, scope) : left;
    case '||':
      return left ? left : evaluate(node.right, scope);
    default:
      return left ?? evaluate(node.right, scope);
  }
}

/** `==` between two values as JavaScript reads it, for the cases a rule meets: nullish, primitives, and a measure (refused). */
function looseEquals(left: unknown, right: unknown): boolean {
  if (left === null || left === undefined || right === null || right === undefined) {
    return (left === null || left === undefined) && (right === null || right === undefined);
  }
  if (typeof left === typeof right) return left === right;
  const primitive = (value: unknown): unknown => (typeof value === 'object' ? primitive((value as { valueOf(): unknown }).valueOf()) : value);
  const [a, b] = [primitive(left), primitive(right)];
  return typeof a === 'object' || typeof b === 'object' ? String(a) === String(b) : Number(a) === Number(b);
}

function binaryValue(operator: string, left: unknown, right: unknown): unknown {
  const [a, b] = [left as number, right as number];
  switch (operator) {
    case '+':
      return a + b;
    case '-':
      return a - b;
    case '*':
      return a * b;
    case '/':
      return a / b;
    case '%':
      return a % b;
    case '**':
      return a ** b;
    case '&':
      return a & b;
    case '|':
      return a | b;
    case '^':
      return a ^ b;
    case '<<':
      return a << b;
    case '>>':
      return a >> b;
    case '>>>':
      return a >>> b;
    case '<':
      return a < b;
    case '>':
      return a > b;
    case '<=':
      return a <= b;
    case '>=':
      return a >= b;
    case '===':
      return left === right;
    case '!==':
      return left !== right;
    case '==':
      return looseEquals(left, right);
    case '!=':
      return !looseEquals(left, right);
    case 'in':
      return (left as string) in (right as object);
    default:
      throw new RuleExpressionError(`\`${operator}\` is not supported in a rule expression`);
  }
}

// endregion

// region property access, calls and construction

function propertyName(node: MemberExpression, scope: Scope): string {
  const name = node.computed ? String(evaluate(node.property, scope)) : (node.property as Identifier).name;
  if (FORBIDDEN_NAMES.has(name)) throw new RuleExpressionError(`'${name}' is not available in a rule expression`);
  return name;
}

function readProperty(object: unknown, name: string): unknown {
  if (object === null || object === undefined) throw new TypeError(`Cannot read properties of ${object} (reading '${name}')`);
  return (object as Record<string, unknown>)[name];
}

/**
 * A property read as a value. A function found that way — `entity.tags.push`, `Object.assign` — is refused unless it is one of
 * the rule's own: handed to an allowed method as a callback (`reduce(Object.getOwnPropertyDescriptor, ...)`) it would be
 * called with arguments the rule chose, which is how a list of allowed *calls* would become a way to reach what it excludes.
 * A listed method is still callable where it is called — see {@link callValue}.
 */
function memberValue(node: MemberExpression, scope: Scope): unknown {
  const object = evaluate(node.object, scope);
  if (object === SHORT_CIRCUIT || (node.optional && (object === null || object === undefined))) return SHORT_CIRCUIT;
  const name = propertyName(node, scope);
  const value = readProperty(object, name);
  if (typeof value === 'function' && !functionAllowed(value)) {
    throw new RuleExpressionError(`'${name}' is a function: it can be called in a rule expression, not passed around`);
  }
  return value;
}

/** What a call runs, and as what: a method on its value, or a function by itself (`receiver` then unset). */
interface CallTarget {
  fn: unknown;
  receiver: unknown;
}

function isNullish(value: unknown): boolean {
  return value === null || value === undefined;
}

function callValue(node: SimpleCallExpression, scope: Scope): unknown {
  const target = node.callee.type === 'MemberExpression' ? methodTarget(node.callee, node, scope) : functionTarget(node.callee, node, scope);
  if (target === SHORT_CIRCUIT || (node.optional && isNullish(target.fn))) return SHORT_CIRCUIT;
  return Reflect.apply(target.fn as (...args: unknown[]) => unknown, target.receiver, spreadValues(node.arguments, scope));
}

/** `value.name(...)`: callable only if `name` is listed for what kind of value `value` is. */
function methodTarget(callee: MemberExpression, node: SimpleCallExpression, scope: Scope): CallTarget | typeof SHORT_CIRCUIT {
  const receiver = evaluate(callee.object, scope);
  if (receiver === SHORT_CIRCUIT || (callee.optional && isNullish(receiver))) return SHORT_CIRCUIT;
  const name = propertyName(callee, scope);
  const fn = readProperty(receiver, name);
  if (!(node.optional && isNullish(fn)) && !methodAllowed(receiver, name)) {
    throw new RuleExpressionError(`'${name}' cannot be called on this value in a rule expression`);
  }
  return { fn, receiver };
}

/** `name(...)` or `(...)(...)`: callable only if it is one of the rule's own arrow functions or a listed global. */
function functionTarget(callee: Node, node: SimpleCallExpression, scope: Scope): CallTarget | typeof SHORT_CIRCUIT {
  const fn = evaluate(callee, scope);
  if (fn === SHORT_CIRCUIT) return SHORT_CIRCUIT;
  if (!(node.optional && isNullish(fn)) && !functionAllowed(fn)) {
    throw new RuleExpressionError('only the functions of a rule expression can be called here');
  }
  return { fn, receiver: undefined };
}

function newValue(node: NewExpression, scope: Scope): unknown {
  return Reflect.construct(Date, spreadValues(node.arguments, scope));
}

// endregion

import { WhereFilterOp } from '@angular/fire/firestore';
import { FilterCondition, FilterGroup, isFilterGroup } from './base-entity-load-response';

const OP_MAP: Record<string, string> = {
  '==': '==',
  '!=': '!=',
  '<': '=lt=',
  '<=': '=le=',
  '>': '=gt=',
  '>=': '=ge=',
  in: '=in=',
  'not-in': '=out=',
  'array-contains': '=in=',
  'array-contains-any': '=in=',
};

export function toRsql(input: FilterCondition[] | FilterGroup): string {
  if (Array.isArray(input)) {
    return input.map(conditionToRsql).join(';');
  }
  return groupToRsql(input);
}

function groupToRsql(group: FilterGroup): string {
  const separator = group.operator === 'OR' ? ',' : ';';
  const parts = group.conditions.map((node) => (isFilterGroup(node) ? `(${groupToRsql(node)})` : conditionToRsql(node)));
  return parts.join(separator);
}

function conditionToRsql(condition: FilterCondition): string {
  const rsqlOp = mapOperator(condition.operator);
  return `${condition.property}${rsqlOp}${encodeValue(condition.value, condition.operator)}`;
}

function mapOperator(operator: WhereFilterOp): string {
  const mapped = OP_MAP[operator as string];
  if (!mapped) throw new Error(`Unsupported filter operator for RSQL translation: ${operator}`);
  return mapped;
}

function encodeValue(value: unknown, operator: WhereFilterOp): string {
  if (operator === 'in' || operator === 'not-in' || operator === 'array-contains-any') {
    const items = Array.isArray(value) ? value : [value];
    return `(${items.map(encodeScalar).join(',')})`;
  }
  if (operator === 'array-contains') {
    return `(${encodeScalar(value)})`;
  }
  return encodeScalar(value);
}

function encodeScalar(value: unknown): string {
  if (value === null || value === undefined) return 'null';
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  const asString = String(value);
  if (/[\s,;()"'=<>!]/.test(asString)) {
    return `"${asString.replace(/"/g, '\\"')}"`;
  }
  return asString;
}

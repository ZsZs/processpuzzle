import { describe, expect, it } from 'vitest';
import { inputValue, isComplete, isPendingRelease, ProblemResolution, resolvedStep } from './release';

const rename: ProblemResolution = {
  step: { kind: 'entity', key: 'customer', operation: 'renameAttribute', subject: 'customerName', parameters: {} },
  input: 'to',
  inputKind: 'text',
  choices: ['name'],
};
const move: ProblemResolution = {
  step: { kind: 'entity', key: 'order', operation: 'mapValues', subject: 'status', parameters: { mapping: { OPEN: 'NEW' } } },
  input: 'mapping.PENDING',
  inputKind: 'text',
  choices: ['NEW', 'SHIPPED'],
};

describe('resolvedStep', () => {
  it('puts the value into the parameter the resolution asks for', () => {
    expect(resolvedStep(rename, 'name')).toEqual({ ...rename.step, parameters: { to: 'name' } });
  });

  it('puts the value under a key of a map parameter, keeping what the map held', () => {
    expect(resolvedStep(move, 'SHIPPED').parameters).toEqual({ mapping: { OPEN: 'NEW', PENDING: 'SHIPPED' } });
    expect(move.step.parameters).toEqual({ mapping: { OPEN: 'NEW' } });
  });

  it('stores numbers and yes/no as their kind', () => {
    const fill: ProblemResolution = { step: { kind: 'entity', key: 'invoice', operation: 'fillAttribute', subject: 'total' }, input: 'value', inputKind: 'number' };

    expect(resolvedStep(fill, '12.5').parameters).toEqual({ value: 12.5 });
    expect(inputValue('boolean', true)).toBe(true);
    expect(inputValue('boolean', 'false')).toBe(false);
    expect(inputValue('date', '2026-10-14')).toBe('2026-10-14');
  });

  it('drops the id the step template might carry, for the server to assign one', () => {
    expect(resolvedStep({ ...rename, step: { ...rename.step, id: 'old' } }, 'name').id).toBeUndefined();
  });
});

describe('isComplete', () => {
  it('needs a value, and a number for a number', () => {
    expect(isComplete(rename, undefined)).toBe(false);
    expect(isComplete(rename, '')).toBe(false);
    expect(isComplete(rename, 'name')).toBe(true);
    expect(isComplete({ ...rename, inputKind: 'number' }, 'abc')).toBe(false);
    expect(isComplete({ ...rename, inputKind: 'number' }, '3')).toBe(true);
  });

  it('takes an unticked yes/no, and a step that asks for nothing, as complete', () => {
    expect(isComplete({ ...rename, inputKind: 'boolean' }, undefined)).toBe(true);
    expect(isComplete({ step: rename.step }, undefined)).toBe(true);
  });
});

describe('isPendingRelease', () => {
  it('recognizes the body of a refused release', () => {
    expect(isPendingRelease({ changes: [], problems: [] })).toBe(true);
    expect(isPendingRelease({ message: 'no' })).toBe(false);
    expect(isPendingRelease(null)).toBe(false);
  });
});

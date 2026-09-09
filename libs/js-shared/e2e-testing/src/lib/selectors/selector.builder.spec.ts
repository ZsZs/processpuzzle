import { describe, expect, it } from 'vitest';
import { attrSelector, blockingViolationsSelector, buttonSelector, buttonTestId, formControlLocator, formControlSelector, toTestId } from './selector.builder';
import { formControlTestId } from './test-id';

/**
 * These encode the `data-testid` convention the generated components author. They are the one place where the
 * library states what the application's markup looks like without a browser present to contradict it, so the
 * examples below are the contract: change one and every suite addresses a different element.
 */

describe('toTestId', () => {
  it('camel-cases a multi-word entity name', () => {
    expect(toTestId('Test Entity Component')).toBe('testEntityComponent');
  });

  it('lower-cases the first word of a single-word name', () => {
    expect(toTestId('Address')).toBe('address');
  });

  it('leaves an already camel-cased name alone', () => {
    expect(toTestId('testEntity')).toBe('testEntity');
  });
});

describe('form control ids', () => {
  it('joins the entity and the attribute with a dash', () => {
    expect(formControlTestId('Test Entity', 'name')).toBe('testEntity-name');
  });

  it('addresses a control by test id', () => {
    expect(attrSelector('Test Entity', 'name')).toBe('[data-testid="testEntity-name"]');
  });

  it('returns the bare test id where a locator takes one', () => {
    expect(formControlSelector('Test Entity', 'name')).toBe('testEntity-name');
  });
});

describe('button ids', () => {
  it.each(['new', 'save', 'delete', 'edit', 'cancel'] as const)('names the %s button after its entity', (action) => {
    expect(buttonTestId('Test Entity', action)).toBe(`testEntity-${action}`);
    expect(buttonSelector('Test Entity', action)).toBe(`[data-testid="testEntity-${action}"]`);
  });
});

describe('blockingViolationsSelector', () => {
  it('addresses the error verdicts of the open form, which is not entity-scoped', () => {
    expect(blockingViolationsSelector()).toBe('[data-testid="rule-violations"] .severity-error');
  });
});

describe('formControlLocator', () => {
  it.each([
    ['TEXT_BOX', 'input, textarea'],
    ['CHECKBOX', 'input[type="checkbox"]'],
    ['DROPDOWN', 'mat-select'],
    ['TAGS', 'mat-chip-grid input'],
    ['RELATED_ENTITIES', 'ul'],
    ['ARTIFACT', 'ul'],
    ['FLEX_BOX', ''],
  ])('gives %s the element the control renders its value in', (type, expected) => {
    expect(formControlLocator(type as never)).toBe(expected);
  });
});

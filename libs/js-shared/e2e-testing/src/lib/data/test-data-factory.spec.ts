import { describe, expect, it } from 'vitest';
import type { BaseEntityAttrDescriptor, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import type { FixtureOverride } from '../controls/control-tester';
import { buildCreateDataForContext, buildUpdateDataForContext } from './test-data-factory';

/** Plain objects, as the suites receive descriptors off the registry JSON — see control-tester.spec.ts. */
function descriptor(entityName: string): BaseEntityDescriptor {
  const attrs = [
    { attrName: 'name', formControlType: 'TEXT_BOX', label: 'Name' },
    { attrName: 'valueKind', formControlType: 'DROPDOWN', selectables: [{ value: 'TEXT' }, { value: 'NUMBER' }, { value: 'DATE_TIME' }] },
  ] as unknown as BaseEntityAttrDescriptor[];
  return { entityName, attrDescriptors: attrs } as unknown as BaseEntityDescriptor;
}

const overrides: FixtureOverride[] = [{ entityName: 'Attribute', create: { valueKind: 'DATE_TIME' }, update: { valueKind: 'DATE_TIME' }, reason: 'test' }];

describe('fixtureOverrides', () => {
  it('replaces the generated value of a pinned field and keeps the others generated', () => {
    const generated = buildCreateDataForContext({ descriptor: descriptor('Attribute'), descriptorMap: new Map() });
    const pinned = buildCreateDataForContext({ descriptor: descriptor('Attribute'), descriptorMap: new Map(), fixtureOverrides: overrides });

    expect(generated['valueKind']).toBe('TEXT');
    expect(pinned).toEqual({ ...generated, valueKind: 'DATE_TIME' });
  });

  it('applies the update values on top of the generated update', () => {
    const context = { descriptor: descriptor('Attribute'), descriptorMap: new Map(), fixtureOverrides: overrides };
    const updated = buildUpdateDataForContext(context, buildCreateDataForContext(context));

    expect(updated['valueKind']).toBe('DATE_TIME');
  });

  it('leaves an entity the overrides do not name untouched', () => {
    const data = buildCreateDataForContext({ descriptor: descriptor('Other'), descriptorMap: new Map(), fixtureOverrides: overrides });

    expect(data['valueKind']).toBe('TEXT');
  });
});

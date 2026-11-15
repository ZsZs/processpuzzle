import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { contextLabel, matchesConditions, renderTemplate, resolveAttrName, valueAt } from './working-context-attributes';

const raceDescriptor = new BaseEntityDescriptor({
  entityName: 'Race',
  attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true), new BaseEntityAttrDescriptor('raceStatus', FormControlType.STATE, 'Status')],
});
const race = { id: 'r1', name: 'Spring Cup', raceStatus: 'Running', currentRound: { number: 3 } };

describe('working context attributes', () => {
  it('resolves `state` to the STATE attribute when the entity has none of that name', () => {
    expect(resolveAttrName('state', raceDescriptor)).toBe('raceStatus');
    expect(resolveAttrName('name', raceDescriptor)).toBe('name');
    expect(resolveAttrName('state', undefined)).toBe('state');
  });

  it('reads dotted paths', () => {
    expect(valueAt(race, 'currentRound.number')).toBe(3);
    expect(valueAt(race, 'currentRound.missing.deeper')).toBeUndefined();
  });

  it('renders templates, leaving missing values empty', () => {
    expect(renderTemplate('{{name}} – round {{ currentRound.number }}', race)).toBe('Spring Cup – round 3');
    expect(renderTemplate('{{name}} {{nothing}}', race)).toBe('Spring Cup');
  });

  it('matches conditions, with arrays as alternatives and `state` as the STATE attribute', () => {
    expect(matchesConditions(race, { state: 'Running' }, raceDescriptor)).toBe(true);
    expect(matchesConditions(race, { state: ['Planned', 'Running'] }, raceDescriptor)).toBe(true);
    expect(matchesConditions(race, { state: 'Finished' }, raceDescriptor)).toBe(false);
    expect(matchesConditions(race, {}, raceDescriptor)).toBe(false);
    expect(matchesConditions(undefined, { state: 'Running' }, raceDescriptor)).toBe(false);
  });

  it('labels by template, then title attribute, then id', () => {
    expect(contextLabel('{{name}}!', race, raceDescriptor, 'r1')).toBe('Spring Cup!');
    expect(contextLabel(undefined, race, raceDescriptor, 'r1')).toBe('Spring Cup');
    expect(contextLabel(undefined, undefined, raceDescriptor, 'r1')).toBe('r1');
  });
});

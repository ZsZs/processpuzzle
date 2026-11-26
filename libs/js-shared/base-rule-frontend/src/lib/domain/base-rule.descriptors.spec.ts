import { type AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { createBaseRuleDescriptor } from './base-rule.descriptors';

/** The attributes of a form, however deep the flexbox rows that lay them out. */
function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createBaseRuleDescriptor', () => {
  const attributes = () => flatten(createBaseRuleDescriptor().attrDescriptors);
  const attribute = (name: string) => attributes().find((attr) => attr.attrName === name) as BaseEntityAttrDescriptor;

  it('lets the AI write the expression from the description, knowing which entity the rule is for', () => {
    expect(attribute('expression').aiAssist).toEqual({ task: 'rule.expression', from: 'description', parameters: ['context'] });
  });

  it('names attributes the form really has as the description and the parameters', () => {
    const names = attributes().map((attr) => attr.attrName);
    const assist = attribute('expression').aiAssist;

    expect(names).toContain(assist?.from);
    assist?.parameters?.forEach((parameter) => expect(names).toContain(parameter));
  });

  it('asks for no AI help on the other fields', () => {
    expect(attribute('description').aiAssist).toBeUndefined();
    expect(attribute('message').aiAssist).toBeUndefined();
  });
});

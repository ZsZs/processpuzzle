import { FormControlType } from './abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from './base-entity-attr.descriptor';
import { BaseEntityDescriptor } from './base-entity.descriptor';
import { FlexboxDescriptor, FlexDirection } from './flexboxDescriptor';
import { describe, expect, it } from 'vitest';

describe('componentIdentification()', () => {
  it('finds the detail link descriptor inside nested flexbox descriptors', () => {
    const firstBranch = new FlexboxDescriptor(
      [
        new BaseEntityAttrDescriptor('firstBranchAttr1', FormControlType.TEXT_BOX),
        new BaseEntityAttrDescriptor('firstBranchAttr2', FormControlType.TEXT_BOX),
      ],
      FlexDirection.ROW,
    );
    const secondBranch = new FlexboxDescriptor(
      [
        new FlexboxDescriptor(
          [
            new BaseEntityAttrDescriptor('secondBranchAttr1', FormControlType.TEXT_BOX),
            new BaseEntityAttrDescriptor('detailsLink', FormControlType.TEXT_BOX, undefined, undefined, true),
          ],
          FlexDirection.ROW,
        ),
      ],
      FlexDirection.COLUMN,
    );
    const entityDescriptor = new BaseEntityDescriptor({
      attrDescriptors: [firstBranch, secondBranch],
      entityName: 'testEntity',
      entityTitle: 'Test Entity',
      store: {},
    });

    expect(entityDescriptor.componentIdentification()).toBe('detailsLink');
  });

  it('returns an empty string when no detail link descriptor exists', () => {
    const entityDescriptor = new BaseEntityDescriptor({
      attrDescriptors: [
        new FlexboxDescriptor([new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX)], FlexDirection.ROW),
      ],
      entityName: 'testEntity',
      entityTitle: 'Test Entity',
      store: {},
    });

    expect(entityDescriptor.componentIdentification()).toBe('');
  });

  it('overwrites the linked entity name for a named attribute', () => {
    const componentAttr = new BaseEntityAttrDescriptor('component', FormControlType.COMPONENTS);
    const entityDescriptor = new BaseEntityDescriptor({
      attrDescriptors: [componentAttr],
      entityName: 'testEntity',
      entityTitle: 'Test Entity',
      store: {},
    });

    entityDescriptor.overwriteLinkedEntityAttr('component', 'linkedEntity');

    expect(componentAttr.linkedEntityType).toBe('linkedEntity');
  });
});

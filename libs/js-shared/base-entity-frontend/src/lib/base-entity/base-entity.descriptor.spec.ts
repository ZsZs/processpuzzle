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

describe('i18n keys', () => {
  it('derives the key root from the entity name when no i18nScope is declared', () => {
    const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX);
    const entityDescriptor = new BaseEntityDescriptor({ attrDescriptors: [nameAttr], entityName: 'Base Entity' });

    expect(entityDescriptor.i18nKey()).toBe('base_entity._self');
    expect(nameAttr.i18nKey()).toBe('base_entity.name');
  });

  it('lets i18nScope override the derived key root', () => {
    const nameAttr = new BaseEntityAttrDescriptor('orderNumber', FormControlType.TEXT_BOX);
    const entityDescriptor = new BaseEntityDescriptor({ attrDescriptors: [nameAttr], entityName: 'Order', i18nScope: 'orders' });

    expect(entityDescriptor.i18nKey()).toBe('orders._self');
    expect(nameAttr.i18nKey()).toBe('orders.orderNumber');
  });

  it('honours a labelKey override on the attribute', () => {
    const nameAttr = new BaseEntityAttrDescriptor('orderNumber', FormControlType.TEXT_BOX);
    nameAttr.labelKey = 'number';
    const entityDescriptor = new BaseEntityDescriptor({ attrDescriptors: [nameAttr], entityName: 'Order Line' });

    expect(entityDescriptor.i18nKey()).toBe('order_line._self');
    expect(nameAttr.i18nKey()).toBe('order_line.number');
  });

  it('stamps the i18n context onto attributes nested in flexbox descriptors', () => {
    const nestedAttr = new BaseEntityAttrDescriptor('street', FormControlType.TEXT_BOX);
    const entityDescriptor = new BaseEntityDescriptor({
      attrDescriptors: [new FlexboxDescriptor([nestedAttr], FlexDirection.ROW)],
      entityName: 'Address',
      i18nScope: 'addresses',
    });

    expect(entityDescriptor.i18nKey()).toBe('addresses._self');
    expect(nestedAttr.i18nKey()).toBe('addresses.street');
  });
});

describe('titleAttrName()', () => {
  it('defaults to the isLinkToDetails attribute', () => {
    const keyAttr = new BaseEntityAttrDescriptor('key', FormControlType.TEXT_BOX, 'Key', undefined, true);
    const entityDescriptor = new BaseEntityDescriptor({ attrDescriptors: [keyAttr], entityName: 'Trunk Data' });

    expect(entityDescriptor.titleAttrName()).toBe('key');
  });

  it('lets an explicit titleKey override the default', () => {
    const keyAttr = new BaseEntityAttrDescriptor('key', FormControlType.TEXT_BOX, 'Key', undefined, true);
    const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX);
    const entityDescriptor = new BaseEntityDescriptor({ attrDescriptors: [keyAttr, nameAttr], entityName: 'Trunk Data', titleKey: 'name' });

    expect(entityDescriptor.titleAttrName()).toBe('name');
  });
});

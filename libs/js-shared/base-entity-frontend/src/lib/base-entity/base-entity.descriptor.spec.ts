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
    const componentAttr = new BaseEntityAttrDescriptor('component', FormControlType.RELATED_ENTITIES);
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

describe('component containment', () => {
  function componentDescriptor(options: { componentParent?: string | string[]; isEmbedded?: boolean } = {}) {
    const parentRefAttr = new BaseEntityAttrDescriptor('orderId', FormControlType.FOREIGN_KEY, 'Order');
    parentRefAttr.linkedEntityType = 'Order';
    return new BaseEntityDescriptor({ attrDescriptors: [parentRefAttr], entityName: 'Order Line', ...options });
  }

  it('leaves an entity without a componentParent stand-alone', () => {
    const descriptor = componentDescriptor();

    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isComponent()).toBe(false);
    expect(descriptor.isEmbedded).toBe(false);
    expect(descriptor.parentReferenceAttrName()).toBeUndefined();
  });

  it('normalizes a single componentParent into a list', () => {
    const descriptor = componentDescriptor({ componentParent: 'Order' });

    expect(descriptor.componentParents).toEqual(['Order']);
    expect(descriptor.isComponent()).toBe(true);
    expect(descriptor.isComponentOf('Order')).toBe(true);
    expect(descriptor.isComponentOf('Invoice')).toBe(false);
  });

  it('keeps every parent of a component that several entity types can aggregate', () => {
    const descriptor = componentDescriptor({ componentParent: ['Order', 'Invoice'] });

    expect(descriptor.componentParents).toEqual(['Order', 'Invoice']);
    expect(descriptor.isComponentOf('Invoice')).toBe(true);
  });

  it('rejects an embedded declaration without a componentParent', () => {
    expect(() => componentDescriptor({ isEmbedded: true })).toThrow(/isEmbedded without a componentParent/);
  });

  describe('parentReferenceAttrName()', () => {
    it('finds the foreign key pointing at the parent', () => {
      expect(componentDescriptor({ componentParent: 'Order' }).parentReferenceAttrName()).toBe('orderId');
    });

    it('searches through nested flexbox descriptors', () => {
      const parentRefAttr = new BaseEntityAttrDescriptor('orderId', FormControlType.FOREIGN_KEY, 'Order');
      parentRefAttr.linkedEntityType = 'Order';
      const descriptor = new BaseEntityDescriptor({
        attrDescriptors: [new FlexboxDescriptor([new FlexboxDescriptor([parentRefAttr], FlexDirection.ROW)], FlexDirection.COLUMN)],
        entityName: 'Order Line',
        componentParent: 'Order',
      });

      expect(descriptor.parentReferenceAttrName()).toBe('orderId');
    });

    it('ignores foreign keys pointing at anything other than the parent', () => {
      const otherRefAttr = new BaseEntityAttrDescriptor('productId', FormControlType.FOREIGN_KEY, 'Product');
      otherRefAttr.linkedEntityType = 'Product';
      const descriptor = new BaseEntityDescriptor({ attrDescriptors: [otherRefAttr], entityName: 'Order Line', componentParent: 'Order' });

      expect(descriptor.parentReferenceAttrName()).toBeUndefined();
    });

    it('returns undefined for an embedded component, which is located by position rather than by key', () => {
      expect(componentDescriptor({ componentParent: 'Order', isEmbedded: true }).parentReferenceAttrName()).toBeUndefined();
    });
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

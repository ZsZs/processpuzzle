// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';

function createOrderLineAttrDescriptors(): BaseEntityAttrDescriptor[] {
  const productNameAttr = new BaseEntityAttrDescriptor('productName', FormControlType.TEXT_BOX, 'Product', undefined, true);
  productNameAttr.required = true;
  const quantityAttr = new BaseEntityAttrDescriptor('quantity', FormControlType.TEXT_BOX, 'Quantity', undefined, false, { inputType: 'number' });
  quantityAttr.required = true;
  const unitPriceAttr = new BaseEntityAttrDescriptor('unitPrice', FormControlType.TEXT_BOX, 'Unit Price', undefined, false, { inputType: 'number' });
  unitPriceAttr.required = true;
  const orderAttr = new BaseEntityAttrDescriptor('orderId', FormControlType.FOREIGN_KEY, 'Order');
  orderAttr.disabled = false;
  orderAttr.linkedEntityType = 'Order';

  return [productNameAttr, quantityAttr, unitPriceAttr, orderAttr];
}

export function createOrderLineDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: 'Order Line', attrDescriptors: createOrderLineAttrDescriptors() });
}

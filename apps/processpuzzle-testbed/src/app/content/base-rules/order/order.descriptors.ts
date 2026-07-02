// eslint-disable-next-line @nx/enforce-module-boundaries
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { OrderStatus } from './order';

const statusSelectables = Object.values(OrderStatus).map((value) => ({ key: value, value: value }));

function createOrderAttrDescriptors(): AbstractAttrDescriptor[] {
  const orderNumberAttr = new BaseEntityAttrDescriptor('orderNumber', FormControlType.TEXT_BOX, 'Order #', undefined, true);
  orderNumberAttr.required = true;
  orderNumberAttr.isLinkToDetails = true;
  const customerNameAttr = new BaseEntityAttrDescriptor('customerName', FormControlType.TEXT_BOX, 'Customer');
  customerNameAttr.required = true;
  const statusAttr = new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', statusSelectables);
  const totalAttr = new BaseEntityAttrDescriptor('total', FormControlType.TEXT_BOX, 'Total', undefined, false, { inputType: 'number' });
  const shippingAddressAttr = new BaseEntityAttrDescriptor('shippingAddress', FormControlType.TEXTAREA, 'Shipping Address');
  const lineItemsAttr = new BaseEntityAttrDescriptor('lineItems', FormControlType.COMPONENTS, 'Line Items');
  lineItemsAttr.linkedEntityType = 'Order Line';

  const column_1 = new FlexboxDescriptor([orderNumberAttr, customerNameAttr, statusAttr, totalAttr], FlexDirection.COLUMN);
  const column_2 = new FlexboxDescriptor([shippingAddressAttr, lineItemsAttr], FlexDirection.COLUMN);
  const flexBoxContainer = new FlexboxDescriptor([column_1, column_2], FlexDirection.CONTAINER);
  flexBoxContainer.style = { 'column-gap': '20px' };
  return [flexBoxContainer];
}

export function createOrderDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: 'Order', attrDescriptors: createOrderAttrDescriptors() });
}

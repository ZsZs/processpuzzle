import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { INVOICE_I18N_SCOPE, PLAN_I18N_SCOPE, SUBSCRIPTION_I18N_SCOPE } from '../../platform-admin.i18n';
import { BillingInterval, InvoiceStatus, SubscriptionStatus } from './billing';

export const PLAN_ENTITY_NAME = 'Plan';
export const SUBSCRIPTION_ENTITY_NAME = 'Subscription';
export const INVOICE_ENTITY_NAME = 'Invoice';

const intervalSelectables = Object.keys(BillingInterval).map((key) => ({ key, value: key }));
const subscriptionStatusSelectables = Object.keys(SubscriptionStatus).map((key) => ({ key, value: key }));
const invoiceStatusSelectables = Object.keys(InvoiceStatus).map((key) => ({ key, value: key }));

/**
 * Marks an attribute read-only.
 *
 * There is no read-only flag on a descriptor and no plan for one: `isAbstract` plus per-attribute
 * `disabled` is the whole mechanism base-entity offers. `isAbstract` removes New and Delete from the
 * toolbar; `disabled` makes the form a viewer. Setting only one produces a screen that looks
 * read-only and still has a working Save button.
 *
 * Read-only here is not a UI preference: this platform has no payment provider, so every billing
 * operation in platform-admin-api.yaml is a GET and there is nothing a write could reach.
 */
function readOnly(attr: BaseEntityAttrDescriptor): BaseEntityAttrDescriptor {
  attr.disabled = true;
  return attr;
}

function createPlanAttrDescriptors(): AbstractAttrDescriptor[] {
  const codeAttr = readOnly(new BaseEntityAttrDescriptor('code', FormControlType.TEXT_BOX, 'Code'));
  codeAttr.isHeading = true;

  const nameAttr = readOnly(new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name'));
  const intervalAttr = readOnly(new BaseEntityAttrDescriptor('interval', FormControlType.DROPDOWN, 'Interval', intervalSelectables));
  const currencyAttr = readOnly(new BaseEntityAttrDescriptor('currency', FormControlType.TEXT_BOX, 'Currency'));

  // The formatted string, not `amountMinor`. The integer is what the API sends and what arithmetic has
  // to use; a column reading "4900" would be understood as forty-nine hundred by everyone who saw it.
  const priceAttr = readOnly(new BaseEntityAttrDescriptor('price', FormControlType.TEXT_BOX, 'Price'));

  const descriptionAttr = readOnly(new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description'));
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const row = new FlexboxDescriptor([codeAttr, nameAttr, priceAttr, intervalAttr, currencyAttr], FlexDirection.ROW);
  row.style = { 'column-gap': '10px' };
  const container = new FlexboxDescriptor([row, descriptionAttr], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

function createSubscriptionAttrDescriptors(): AbstractAttrDescriptor[] {
  const orgKeyAttr = readOnly(new BaseEntityAttrDescriptor('orgKey', FormControlType.TEXT_BOX, 'Organization'));
  orgKeyAttr.isHeading = true;

  const planCodeAttr = readOnly(new BaseEntityAttrDescriptor('planCode', FormControlType.TEXT_BOX, 'Plan'));
  const statusAttr = readOnly(new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', subscriptionStatusSelectables));
  const periodStartAttr = readOnly(new BaseEntityAttrDescriptor('currentPeriodStart', FormControlType.TEXT_BOX, 'Period from'));
  const periodEndAttr = readOnly(new BaseEntityAttrDescriptor('currentPeriodEnd', FormControlType.TEXT_BOX, 'Period to'));
  const canceledAtAttr = readOnly(new BaseEntityAttrDescriptor('canceledAt', FormControlType.TEXT_BOX, 'Cancelled'));
  canceledAtAttr.hideInTable = true;

  const row1 = new FlexboxDescriptor([orgKeyAttr, planCodeAttr, statusAttr], FlexDirection.ROW);
  row1.style = { 'column-gap': '10px' };
  const row2 = new FlexboxDescriptor([periodStartAttr, periodEndAttr, canceledAtAttr], FlexDirection.ROW);
  row2.style = { 'column-gap': '10px' };
  const container = new FlexboxDescriptor([row1, row2], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

function createInvoiceAttrDescriptors(): AbstractAttrDescriptor[] {
  const numberAttr = readOnly(new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX, 'Number'));
  numberAttr.isHeading = true;

  const orgKeyAttr = readOnly(new BaseEntityAttrDescriptor('orgKey', FormControlType.TEXT_BOX, 'Organization'));
  const statusAttr = readOnly(new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', invoiceStatusSelectables));
  const totalAttr = readOnly(new BaseEntityAttrDescriptor('total', FormControlType.TEXT_BOX, 'Total'));
  const periodStartAttr = readOnly(new BaseEntityAttrDescriptor('periodStart', FormControlType.TEXT_BOX, 'Period from'));
  const periodEndAttr = readOnly(new BaseEntityAttrDescriptor('periodEnd', FormControlType.TEXT_BOX, 'Period to'));
  const issuedAtAttr = readOnly(new BaseEntityAttrDescriptor('issuedAt', FormControlType.TEXT_BOX, 'Issued'));
  issuedAtAttr.hideInTable = true;
  const paidAtAttr = readOnly(new BaseEntityAttrDescriptor('paidAt', FormControlType.TEXT_BOX, 'Paid'));
  paidAtAttr.hideInTable = true;

  const row1 = new FlexboxDescriptor([numberAttr, orgKeyAttr, statusAttr, totalAttr], FlexDirection.ROW);
  row1.style = { 'column-gap': '10px' };
  const row2 = new FlexboxDescriptor([periodStartAttr, periodEndAttr, issuedAtAttr, paidAtAttr], FlexDirection.ROW);
  row2.style = { 'column-gap': '10px' };
  const container = new FlexboxDescriptor([row1, row2], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

/**
 * `isAbstract` on all three, for the reason given on {@link readOnly}: it is what removes New and
 * Delete from the toolbar, and without it a read-only form still offers to create rows the API has no
 * endpoint for.
 */
export function createPlanDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: PLAN_ENTITY_NAME,
    i18nScope: PLAN_I18N_SCOPE,
    titleKey: 'code',
    isAbstract: true,
    attrDescriptors: createPlanAttrDescriptors(),
  });
}

export function createSubscriptionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: SUBSCRIPTION_ENTITY_NAME,
    i18nScope: SUBSCRIPTION_I18N_SCOPE,
    titleKey: 'orgKey',
    isAbstract: true,
    attrDescriptors: createSubscriptionAttrDescriptors(),
  });
}

export function createInvoiceDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: INVOICE_ENTITY_NAME,
    i18nScope: INVOICE_I18N_SCOPE,
    titleKey: 'number',
    isAbstract: true,
    attrDescriptors: createInvoiceAttrDescriptors(),
  });
}

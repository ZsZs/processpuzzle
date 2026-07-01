import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, Selectable } from '@processpuzzle/base-entity';
import { Severity } from './base-rule';

const severitySelectables = Object.keys(Severity).map((key) => ({ key, value: key }));

function createBaseRuleAttrDescriptors(getContextOptions: () => Array<Selectable>): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;

  const contextAttr = new BaseEntityAttrDescriptor('context', FormControlType.DROPDOWN, 'Context', getContextOptions);
  contextAttr.required = true;
  contextAttr.placeholder = 'Entity type the rule applies to, e.g. Order';

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const expressionAttr = new BaseEntityAttrDescriptor('expression', FormControlType.TEXTAREA, 'Expression');
  expressionAttr.required = true;
  expressionAttr.placeholder = 'PPCL boolean expression evaluated against `entity`';
  expressionAttr.styleClass = 'monospace full-width';
  expressionAttr.hideInTable = true;

  const severityAttr = new BaseEntityAttrDescriptor('severity', FormControlType.DROPDOWN, 'Severity', severitySelectables);
  severityAttr.required = true;

  const messageAttr = new BaseEntityAttrDescriptor('message', FormControlType.TEXT_BOX, 'Message');
  messageAttr.hideInTable = true;
  messageAttr.style = { flex: '2' };

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.hideInTable = true;
  translocoIdAttr.style = { flex: '1' };

  const extendsRuleIdAttr = new BaseEntityAttrDescriptor('extendsRuleId', FormControlType.FOREIGN_KEY, 'Extends Rule');
  extendsRuleIdAttr.linkedEntityType = 'Base Rule';
  extendsRuleIdAttr.hideInTable = true;

  const overrideAttr = new BaseEntityAttrDescriptor('override', FormControlType.CHECKBOX, 'Override');
  const enabledAttr = new BaseEntityAttrDescriptor('enabled', FormControlType.CHECKBOX, 'Enabled');

  const row1 = new FlexboxDescriptor([nameAttr, contextAttr, enabledAttr], FlexDirection.ROW);
  row1.style = { 'column-gap': '10px' };
  const row2 = new FlexboxDescriptor([extendsRuleIdAttr, severityAttr, overrideAttr], FlexDirection.ROW);
  row2.style = { 'column-gap': '10px' };
  const row5 = new FlexboxDescriptor([messageAttr, translocoIdAttr], FlexDirection.ROW);
  row5.style = { 'column-gap': '10px' };
  const flexBoxContainer = new FlexboxDescriptor([row1, row2, descriptionAttr, expressionAttr, row5], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createBaseRuleDescriptor(getContextOptions: () => Array<Selectable> = () => []): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: 'Base Rule', attrDescriptors: createBaseRuleAttrDescriptors(getContextOptions) });
}

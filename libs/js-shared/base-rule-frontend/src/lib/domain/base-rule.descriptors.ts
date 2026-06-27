import {
  AbstractAttrDescriptor,
  BaseEntityAttrDescriptor,
  BaseEntityDescriptor,
  FlexboxDescriptor,
  FlexDirection,
  FormControlType,
} from '@processpuzzle/base-entity';
import { Severity } from './base-rule';

const severitySelectables = Object.keys(Severity).map((key) => ({ key, value: key }));

function createBaseRuleAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;

  const contextAttr = new BaseEntityAttrDescriptor('context', FormControlType.TEXT_BOX, 'Context');
  contextAttr.required = true;
  contextAttr.placeholder = 'Entity type the rule applies to, e.g. Order';

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.hideInTable = true;

  const expressionAttr = new BaseEntityAttrDescriptor('expression', FormControlType.TEXTAREA, 'Expression');
  expressionAttr.required = true;
  expressionAttr.placeholder = 'PPCL boolean expression evaluated against `entity`';
  expressionAttr.styleClass = 'monospace';
  expressionAttr.hideInTable = true;

  const severityAttr = new BaseEntityAttrDescriptor('severity', FormControlType.DROPDOWN, 'Severity', severitySelectables);
  severityAttr.required = true;

  const messageAttr = new BaseEntityAttrDescriptor('message', FormControlType.TEXT_BOX, 'Message');
  messageAttr.hideInTable = true;

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.hideInTable = true;

  const extendsRuleIdAttr = new BaseEntityAttrDescriptor('extendsRuleId', FormControlType.FOREIGN_KEY, 'Extends Rule');
  extendsRuleIdAttr.linkedEntityType = 'Base Rule';
  extendsRuleIdAttr.hideInTable = true;

  const overrideAttr = new BaseEntityAttrDescriptor('override', FormControlType.CHECKBOX, 'Override');
  const enabledAttr = new BaseEntityAttrDescriptor('enabled', FormControlType.CHECKBOX, 'Enabled');

  const column_1 = new FlexboxDescriptor([nameAttr, contextAttr, severityAttr, enabledAttr, overrideAttr], FlexDirection.COLUMN);
  const column_2 = new FlexboxDescriptor([descriptionAttr, expressionAttr, messageAttr, translocoIdAttr, extendsRuleIdAttr], FlexDirection.COLUMN);
  const flexBoxContainer = new FlexboxDescriptor([column_1, column_2], FlexDirection.CONTAINER);
  flexBoxContainer.style = { 'column-gap': '20px' };
  return [flexBoxContainer];
}

export function createBaseRuleDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: 'Base Rule', attrDescriptors: createBaseRuleAttrDescriptors() });
}

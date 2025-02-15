export enum FormControlType {
  CHECKBOX = 'CHECKBOX',
  DATE = 'DATE',
  DROPDOWN = 'DROPDOWN',
  FLEX_BOX = 'FLEX_BOX',
  FOREIGN_KEY = 'FOREIGN_KEY',
  LABEL = 'LABEL',
  RADIO = 'RADIO',
  TITLE = 'TITLE',
  TEXT_BOX = 'TEXT_BOX',
  TEXTAREA = 'TEXTAREA',
}

export abstract class AbstractAttrDescriptor {
  attrName: string;
  formControlType: FormControlType;
  disabled = false;
  style: { [p: string]: any } | null | undefined;

  protected constructor(attrName: string, formControlType: FormControlType) {
    this.attrName = attrName;
    this.formControlType = formControlType;
  }
}

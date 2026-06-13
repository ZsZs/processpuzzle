export enum FormControlType {
  ARTIFACT = 'ARTIFACT',
  CHECKBOX = 'CHECKBOX',
  COMPONENTS = 'COMPONENTS',
  DATE = 'DATE',
  DROPDOWN = 'DROPDOWN',
  FLEX_BOX = 'FLEX_BOX',
  FOREIGN_KEY = 'FOREIGN_KEY',
  LABEL = 'LABEL',
  LOOKUP = 'LOOKUP',
  RADIO = 'RADIO',
  TAGS = 'TAGS',
  TITLE = 'TITLE',
  TEXT_BOX = 'TEXT_BOX',
  TEXTAREA = 'TEXTAREA',
}

export abstract class AbstractAttrDescriptor {
  attrName: string;
  formControlType: FormControlType;
  disabled = false;
  style: { [p: string]: unknown } | null | undefined;

  protected constructor(attrName: string, formControlType: FormControlType) {
    this.attrName = attrName;
    this.formControlType = formControlType;
  }
}

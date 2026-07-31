export enum FormControlType {
  ADDITIONAL_PROPERTIES = 'ADDITIONAL_PROPERTIES',
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
  /** Optional override for the transloco key segment of this attribute. Defaults to {@link attrName}. */
  labelKey?: string;
  private _scopeRoot?: string;

  protected constructor(attrName: string, formControlType: FormControlType) {
    this.attrName = attrName;
    this.formControlType = formControlType;
  }

  /**
   * Called by the owning {@link BaseEntityDescriptor} to stamp the entity's transloco key root
   * (`i18nScope` override, or the value derived from the entity name) onto this attribute.
   */
  setI18nContext(scopeRoot: string): void {
    this._scopeRoot = scopeRoot;
  }

  /**
   * Full transloco key for this attribute's label: `<scopeRoot>.<labelKey|attrName>`. The root
   * identifies the entity (see {@link BaseEntityDescriptor.i18nKey}). Returns `undefined` until the
   * owning descriptor has stamped the context — a bare `attrName` would be a root-level key and could
   * collide with an unrelated translation in the consuming app.
   */
  i18nKey(): string | undefined {
    if (!this._scopeRoot) return undefined;
    return `${this._scopeRoot}.${this.labelKey ?? this.attrName}`;
  }
}

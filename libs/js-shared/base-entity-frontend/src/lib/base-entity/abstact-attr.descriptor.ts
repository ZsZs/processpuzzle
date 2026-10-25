export enum FormControlType {
  ADDITIONAL_PROPERTIES = 'ADDITIONAL_PROPERTIES',
  /**
   * A postal address — the value is one `PostalAddress` object. The fields and their order follow the selected
   * country; with `showMap` the address is also shown on a map, with coordinates derived from it.
   */
  ADDRESS = 'ADDRESS',
  ARTIFACT = 'ARTIFACT',
  AVATAR = 'AVATAR',
  /**
   * A value a calculation derives. Read-only; shows what the calculation would produce from the form's unsaved
   * state. Rendered by the component registered under `ENTITY_CALCULATED_CONTROL`, a plain label without one.
   */
  CALCULATED = 'CALCULATED',
  CHECKBOX = 'CHECKBOX',
  /**
   * An append-only history of rich-text comments, each stamped with its author and time — the value is an
   * array of `EntityComment`, oldest first. A new comment is written below the history and saved with the record.
   */
  COMMENTS = 'COMMENTS',
  COMPONENTS = 'COMPONENTS',
  /** Rendered by the descriptor's own `component` — the extension point for controls base-entity cannot know. */
  CUSTOM = 'CUSTOM',
  DATE = 'DATE',
  DROPDOWN = 'DROPDOWN',
  /**
   * Where the device was when the user pressed the control's button — the value is one `GeoLocation` object.
   * Shown read-only; pressing the button again replaces it. Disabled where the browser offers no location.
   */
  GEO_LOCATION = 'GEO_LOCATION',
  EMBEDDED_COMPONENTS = 'EMBEDDED_COMPONENTS',
  EMBEDDED_LIST = 'EMBEDDED_LIST',
  EMBEDDED_OBJECT = 'EMBEDDED_OBJECT',
  FLEX_BOX = 'FLEX_BOX',
  FOREIGN_KEY = 'FOREIGN_KEY',
  LABEL = 'LABEL',
  LOOKUP = 'LOOKUP',
  RADIO = 'RADIO',
  RELATED_ENTITIES = 'RELATED_ENTITIES',
  /**
   * The attribute a state machine governs. Shows the current state and offers only the transitions allowed from
   * it; rendered by the component registered under `ENTITY_STATE_CONTROL`, read-only as a label without one.
   */
  STATE = 'STATE',
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

  /**
   * Transloco key of the tooltip the generated form shows on this attribute's control:
   * `<scopeRoot>._tooltips.<labelKey|attrName>`. Under a `_tooltips` node of its own — the `_` prefix
   * like `_self` — so it can never collide with an attribute that happens to be called `tooltips`.
   * `undefined` for the same reason as {@link i18nKey}.
   */
  tooltipI18nKey(): string | undefined {
    if (!this._scopeRoot) return undefined;
    return `${this._scopeRoot}._tooltips.${this.labelKey ?? this.attrName}`;
  }
}

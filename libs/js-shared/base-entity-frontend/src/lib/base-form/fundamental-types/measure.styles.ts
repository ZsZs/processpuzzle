/** The layout the quantity, money and dimensions controls share: a fieldset like `ADDRESS`'s, one row of fields. */
export const MEASURE_STYLES = `
  :host {
    display: block;
    margin-bottom: 16px;
  }
  .pp-measure {
    margin: 0;
    padding: 0;
    border: none;
    min-width: 0;
  }
  .pp-measure__title {
    margin: 0 0 8px;
    padding: 0;
    font-size: 1rem;
    font-weight: 500;
  }
  .pp-measure__line {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 0 12px;
  }
  .pp-measure__line mat-form-field {
    flex: 1 1 0;
    min-width: 110px;
  }
  /* A unit is a short symbol: the field is as wide as the symbol and its arrow, not the Material default width. */
  .pp-measure__line mat-form-field.pp-measure__unit {
    flex: 0 0 auto;
  }
  .pp-measure__unit ::ng-deep .mat-mdc-form-field-infix {
    width: auto;
    min-width: 0;
  }
  .pp-measure__unit ::ng-deep .mat-mdc-select {
    min-width: 4.5rem;
  }
  .pp-measure__error {
    margin: 0 0 8px;
    font-size: 0.85rem;
    color: var(--mat-sys-error, #b3261e);
  }
`;

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
  .pp-measure__line mat-form-field.pp-measure__unit {
    flex: 0 1 220px;
  }
  .pp-measure__error {
    margin: 0 0 8px;
    font-size: 0.85rem;
    color: var(--mat-sys-error, #b3261e);
  }
`;

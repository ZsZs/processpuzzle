/**
 * The look the three extra tabs share: the white card the status bar above them uses, plain buttons — this library
 * declares no `@angular/material` peer — and compact tables.
 */
export const CALCULATION_TAB_STYLES = `
  .pp-calculation-tab {
    display: flex;
    flex-direction: column;
    gap: 12px;
    background-color: #ffffff;
    border-radius: 6px;
    padding: 16px 20px 24px;
  }
  .pp-calculation-tab__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }
  .pp-calculation-tab button {
    padding: 6px 14px;
    border: none;
    border-radius: 4px;
    background-color: var(--pp-button-primary-bg, rgb(24, 111, 206));
    color: var(--pp-button-primary-text, #eeeeee);
    cursor: pointer;
  }
  .pp-calculation-tab button.secondary {
    background-color: var(--pp-button-secondary-bg, #eeeeee);
    color: var(--pp-button-secondary-text, rgb(24, 111, 206));
    border: 1px solid currentColor;
  }
  .pp-calculation-tab button:disabled {
    opacity: 0.5;
    cursor: default;
  }
  .pp-calculation-tab input,
  .pp-calculation-tab select {
    padding: 4px 6px;
    border: 1px solid #cccccc;
    border-radius: 4px;
    font: inherit;
  }
  .pp-calculation-tab table {
    border-collapse: collapse;
    font-size: 13px;
  }
  .pp-calculation-tab th,
  .pp-calculation-tab td {
    border: 1px solid #dddddd;
    padding: 4px 6px;
    text-align: left;
    vertical-align: top;
  }
  .pp-calculation-tab th {
    background: #f4f6f8;
  }
  .pp-calculation-tab pre {
    margin: 0;
    white-space: pre-wrap;
    font-size: 12px;
  }
  .pp-calculation-tab .error {
    color: #b00020;
  }
  .pp-calculation-tab .ok {
    color: #1b5e20;
  }
  .pp-calculation-tab .muted {
    color: #666666;
  }
`;

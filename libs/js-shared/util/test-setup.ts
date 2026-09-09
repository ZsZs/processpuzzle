// Registers jest-dom's matchers (toHaveClass, toHaveTextContent, …) on Vitest's expect.
// Needed since util gained the navigate-back and error-snackbar components, whose specs use them;
// util had no component specs before and therefore no setup file at all.
import '@testing-library/jest-dom/vitest';

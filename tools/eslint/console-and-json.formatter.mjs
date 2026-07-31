/**
 * ESLint formatter for the `lint:ci` configurations.
 *
 * `eslint --format=json --output-file=...` swallows every diagnostic into the report file,
 * so a failing CI lint prints nothing but a non-zero exit code. This formatter writes the
 * machine-readable JSON report itself (for Sonar and later examination) and returns the
 * human-readable `stylish` output, which ESLint prints to the console.
 *
 * The report path is relative to the ESLint cwd (the project root) and can be overridden
 * with the ESLINT_JSON_REPORT environment variable.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { ESLint } from 'eslint';

export default async function format(results, context) {
  const eslint = new ESLint({ cwd: context?.cwd });
  const [json, stylish] = await Promise.all([eslint.loadFormatter('json'), eslint.loadFormatter('stylish')]);

  const reportPath = resolve(context?.cwd ?? process.cwd(), process.env.ESLINT_JSON_REPORT ?? 'reports/eslint/eslint.json');
  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(reportPath, await json.format(results, context), 'utf8');

  return stylish.format(results, context);
}

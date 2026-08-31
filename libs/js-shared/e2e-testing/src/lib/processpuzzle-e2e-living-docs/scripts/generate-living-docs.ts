#!/usr/bin/env tsx
/**
 * Stitches together three sources — none of which is hand-duplicated prose —
 * into one "living documentation" markdown page:
 *
 *   1. scenarios/*.manifest.ts    the narrative spine: step titles + which
 *                                 fixtures each step provisions/exercises
 *   2. fixtures/**\/*.yaml         the actual ProcessPuzzle metadata used to
 *                                 provision the platform — embedded verbatim
 *   3. test-results/results.json  Playwright's JSON reporter output — real
 *                                 pass/fail status and timing from the last run
 *
 * Run `npm run test:e2e` first so results.json exists; if it doesn't, the
 * doc is still generated but every step shows as "not yet run".
 *
 * NOTE: Playwright's JSON reporter schema has shifted across versions. The
 * flattening below matches the @playwright/test ^1.47 shape (suites ->
 * specs -> tests -> results -> steps, recursively nested). If you're on a
 * different version, check `results.json` and adjust flattenSteps().
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { orderLifecycleManifest, type ScenarioStep } from '../scenarios/order-lifecycle.manifest';
import { loadMetadataFixtureRecord } from '../support/metadata-fixture-loader';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RESULTS_PATH = path.resolve(__dirname, '../test-results/results.json');
const OUT_PATH = path.resolve(__dirname, '../docs/order-lifecycle.living-doc.md');

interface StepRunInfo {
  status: 'passed' | 'failed';
  duration: number;
}

interface RawStep {
  title: string;
  duration: number;
  error?: { message: string };
  steps?: RawStep[];
}

/** Walks the JSON report and returns a title -> {status, duration} map for every test.step(). */
function flattenStepResults(report: any): Map<string, StepRunInfo> {
  const byTitle = new Map<string, StepRunInfo>();

  const visitSteps = (steps: RawStep[] | undefined) => {
    for (const step of steps ?? []) {
      byTitle.set(step.title, { status: step.error ? 'failed' : 'passed', duration: step.duration });
      visitSteps(step.steps);
    }
  };

  const visitSuite = (suite: any) => {
    for (const spec of suite.specs ?? []) {
      for (const t of spec.tests ?? []) {
        for (const result of t.results ?? []) {
          visitSteps(result.steps);
        }
      }
    }
    for (const child of suite.suites ?? []) {
      visitSuite(child);
    }
  };

  for (const suite of report.suites ?? []) {
    visitSuite(suite);
  }

  return byTitle;
}

function renderStepSection(step: ScenarioStep, runInfo: StepRunInfo | undefined): string {
  const lines: string[] = [];
  const badge = !runInfo ? '⏳' : runInfo.status === 'passed' ? '✅' : '❌';
  const durationNote = runInfo ? ` _(${runInfo.duration}ms)_` : ' _(not yet run)_';

  lines.push(`### ${badge} ${step.title}${durationNote}`);
  lines.push('');
  lines.push(step.kind === 'provision' ? '_Provisions platform metadata:_' : '_Exercises the running platform:_');
  lines.push('');

  for (const fixturePath of step.fixtures ?? []) {
    const record = loadMetadataFixtureRecord(fixturePath);
    const data = record.data as { description?: string };
    if (data?.description) {
      lines.push(`> ${data.description}`);
      lines.push('');
    }
    lines.push(`_Fixture: \`${record.relativePath}\`_`);
    lines.push('');
    lines.push('```yaml');
    lines.push(record.raw.trim());
    lines.push('```');
    lines.push('');
  }

  return lines.join('\n');
}

function main() {
  const hasResults = fs.existsSync(RESULTS_PATH);
  const runResults = hasResults
    ? flattenStepResults(JSON.parse(fs.readFileSync(RESULTS_PATH, 'utf-8')))
    : new Map<string, StepRunInfo>();

  const passCount = [...runResults.values()].filter((r) => r.status === 'passed').length;
  const summary = hasResults
    ? `Last run: ${passCount}/${runResults.size} steps passed.`
    : 'No test run found yet — run `npm run test:e2e` first, then regenerate.';

  const sections = orderLifecycleManifest.map((step) => renderStepSection(step, runResults.get(step.title)));

  const doc = [
    '# Order Lifecycle — Living Documentation',
    '',
    'Generated from the executable E2E scenario in `scenarios/order-lifecycle.manifest.ts`.',
    'Every YAML block below is the actual ProcessPuzzle metadata used to provision the',
    'platform for this test — not a description written separately from it. Status badges',
    'reflect the most recent test run.',
    '',
    `> ${summary}`,
    '',
    '---',
    '',
    ...sections,
  ].join('\n');

  fs.mkdirSync(path.dirname(OUT_PATH), { recursive: true });
  fs.writeFileSync(OUT_PATH, doc, 'utf-8');
  console.log(`Living doc written to ${OUT_PATH}`);
}

main();

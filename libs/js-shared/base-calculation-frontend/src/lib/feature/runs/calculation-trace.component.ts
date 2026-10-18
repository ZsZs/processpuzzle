import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { CALCULATION_EVALUATE_I18N_SCOPE } from '../../base-calculation.i18n';
import type { EvaluationResult } from '../../domain/calculation-api';

/**
 * What a calculation computed: the single values, then every decision in evaluation order with its scope and
 * value — a row decision's per row, a group decision's per group — and the error of the one it stopped at.
 */
@Component({
  selector: 'pp-calculation-trace',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    @if (result().error; as error) {
      <p class="error" role="alert" data-testid="calculation-result-error">{{ error }}</p>
    }
    <h3>{{ scope + '.values' | transloco }}</h3>
    <table data-testid="calculation-values">
      <tbody>
        @for (entry of values(); track entry.name) {
          <tr>
            <th>{{ entry.name }}</th>
            <td><pre>{{ entry.value }}</pre></td>
          </tr>
        }
      </tbody>
    </table>
    <h3>{{ scope + '.trace' | transloco }}</h3>
    <table data-testid="calculation-trace">
      <thead>
        <tr>
          <th>{{ scope + '.decision' | transloco }}</th>
          <th>{{ scope + '.kind' | transloco }}</th>
          <th>{{ scope + '.scope' | transloco }}</th>
          <th>{{ scope + '.value' | transloco }}</th>
        </tr>
      </thead>
      <tbody>
        @for (entry of trace(); track entry.decision) {
          <tr [class.error]="!!entry.error">
            <td>{{ entry.decision }}</td>
            <td>{{ entry.kind }}</td>
            <td>{{ entry.scope }}</td>
            <td><pre>{{ entry.error ?? entry.value }}</pre></td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    table {
      border-collapse: collapse;
      font-size: 13px;
      margin-bottom: 8px;
    }
    th,
    td {
      border: 1px solid #dddddd;
      padding: 4px 6px;
      text-align: left;
      vertical-align: top;
    }
    th {
      background: #f4f6f8;
    }
    pre {
      margin: 0;
      white-space: pre-wrap;
      font-size: 12px;
      max-height: 160px;
      overflow: auto;
    }
    .error {
      color: #b00020;
    }
  `,
})
export class CalculationTraceComponent {
  readonly result = input.required<EvaluationResult>();

  protected readonly scope = CALCULATION_EVALUATE_I18N_SCOPE;
  protected readonly values = computed(() => Object.entries(this.result().values ?? {}).map(([name, value]) => ({ name, value: format(value) })));
  protected readonly trace = computed(() =>
    (this.result().trace ?? []).map((entry) => ({ ...entry, value: format(entry.value), error: entry.error ?? undefined })),
  );
}

/** A value as it reads best: text and numbers as they are, anything structured as indented JSON. */
export function format(value: unknown): string {
  if (value === null || value === undefined) return '—';
  return typeof value === 'object' ? JSON.stringify(value, null, 1) : String(value);
}

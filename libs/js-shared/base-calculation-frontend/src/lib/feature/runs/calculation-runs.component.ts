import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { CALCULATION_RUNS_I18N_SCOPE } from '../../base-calculation.i18n';
import type { CalculationRun, EvaluationResult } from '../../domain/calculation-api';
import { CalculationApiService } from '../../domain/calculation-api.service';
import { CALCULATION_TAB_STYLES } from '../calculation-tab.styles';
import { messageOf } from '../message-of';
import { CalculationTraceComponent } from './calculation-trace.component';

/**
 * The Calculation runs screen: every run of the organization, newest first and filterable by calculation or
 * subject; a run's results, trace and the definition source it ran; and Freeze, which makes a completed run's
 * results final — its subject is not recalculated afterwards.
 */
@Component({
  selector: 'pp-calculation-runs',
  standalone: true,
  imports: [TranslocoPipe, CalculationTraceComponent],
  template: `
    <div class="pp-calculation-tab">
      <div class="pp-calculation-tab__toolbar">
        <label>
          {{ scope + '.calculation' | transloco }}
          <input data-testid="calculation-runs-filter-calculation" [value]="calculationId()" (change)="calculationId.set($any($event.target).value)" />
        </label>
        <label>
          {{ scope + '.subject' | transloco }}
          <input data-testid="calculation-runs-filter-subject" [value]="subjectId()" (change)="subjectId.set($any($event.target).value)" />
        </label>
        <button type="button" data-testid="calculation-runs-refresh" [disabled]="busy()" (click)="load()">{{ scope + '.refresh' | transloco }}</button>
      </div>

      @if (failure(); as failure) {
        <p class="error" role="alert" data-testid="calculation-runs-failure">{{ failure }}</p>
      }

      @if (runs().length === 0) {
        <p class="muted" data-testid="calculation-runs-none">{{ scope + '.none' | transloco }}</p>
      } @else {
        <table data-testid="calculation-runs-list">
          <thead>
            <tr>
              <th>{{ scope + '.startedAt' | transloco }}</th>
              <th>{{ scope + '.calculation' | transloco }}</th>
              <th>{{ scope + '.version' | transloco }}</th>
              <th>{{ scope + '.subject' | transloco }}</th>
              <th>{{ scope + '.triggeredBy' | transloco }}</th>
              <th>{{ scope + '.status' | transloco }}</th>
              <th>{{ scope + '.frozen' | transloco }}</th>
            </tr>
          </thead>
          <tbody>
            @for (run of runs(); track run.id) {
              <tr [attr.data-testid]="'calculation-run-' + run.id" [class.selected]="run.id === selected()?.id" (click)="open(run)">
                <td>{{ run.startedAt }}</td>
                <td>{{ run.calculationId }}</td>
                <td>{{ run.definitionVersion }}</td>
                <td>{{ run.subjectType }} {{ run.subjectId }}</td>
                <td>{{ run.triggeredBy }}</td>
                <td [class.ok]="run.status === 'COMPLETED'" [class.error]="run.status === 'FAILED'">{{ run.status }}</td>
                <td>{{ run.frozen ? '✓' : '' }}</td>
              </tr>
            }
          </tbody>
        </table>
      }

      @if (selected(); as run) {
        <section data-testid="calculation-run-details">
          <div class="pp-calculation-tab__toolbar">
            <strong>{{ run.calculationId }} · {{ run.id }}</strong>
            <button type="button" data-testid="calculation-run-freeze" [disabled]="busy() || !canFreeze()" (click)="freeze(run)">
              {{ scope + '.freeze' | transloco }}
            </button>
            @if (run.frozen) {
              <span class="muted">{{ scope + '.frozenAt' | transloco: { at: run.frozenAt } }}</span>
            }
          </div>
          @if (run.error) {
            <p class="error">{{ run.error }}</p>
          }
          @if (details(); as details) {
            <pp-calculation-trace [result]="details" />
          }
          <h3>{{ scope + '.source' | transloco }}</h3>
          <pre data-testid="calculation-run-source">{{ run.definitionSource }}</pre>
        </section>
      }
    </div>
  `,
  styles: [
    CALCULATION_TAB_STYLES,
    `
      tbody tr {
        cursor: pointer;
      }
      tr.selected td {
        background: #e3f2fd;
      }
    `,
  ],
})
export class CalculationRunsComponent implements OnInit {
  protected readonly scope = CALCULATION_RUNS_I18N_SCOPE;
  private readonly api = inject(CalculationApiService);

  protected readonly calculationId = signal('');
  protected readonly subjectId = signal('');
  protected readonly runs = signal<CalculationRun[]>([]);
  protected readonly selected = signal<CalculationRun | undefined>(undefined);
  protected readonly details = signal<EvaluationResult | undefined>(undefined);
  protected readonly failure = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);
  protected readonly canFreeze = computed(() => this.selected()?.status === 'COMPLETED' && !this.selected()?.frozen);

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    await this.guarded(async () => {
      this.runs.set(await firstValueFrom(this.api.runs(this.calculationId().trim() || undefined, this.subjectId().trim() || undefined)));
    });
  }

  protected async open(run: CalculationRun): Promise<void> {
    this.selected.set(run);
    this.details.set(undefined);
    await this.guarded(async () => this.details.set({ ...run.result, trace: await firstValueFrom(this.api.trace(run.id)) }));
  }

  protected async freeze(run: CalculationRun): Promise<void> {
    await this.guarded(async () => {
      const frozen = await firstValueFrom(this.api.freeze(run.id));
      this.selected.set(frozen);
      this.runs.update((runs) => runs.map((each) => (each.id === frozen.id ? frozen : each)));
    });
  }

  private async guarded(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.failure.set(undefined);
    try {
      await action();
    } catch (error) {
      this.failure.set(messageOf(error));
    } finally {
      this.busy.set(false);
    }
  }
}

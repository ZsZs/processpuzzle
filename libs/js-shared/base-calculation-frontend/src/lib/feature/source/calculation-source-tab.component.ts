import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { CALCULATION_SOURCE_I18N_SCOPE } from '../../base-calculation.i18n';
import type { ValidationReport } from '../../domain/calculation-api';
import { CalculationApiService } from '../../domain/calculation-api.service';
import { CALCULATION_TAB_STYLES } from '../calculation-tab.styles';
import { currentCalculation } from '../current-calculation';
import { messageOf } from '../message-of';
import { CalculationSourceEditorComponent } from './calculation-source-editor.component';

/**
 * The Source tab: the calculation's YAML in a code editor, Validate — the backend's check of references, scopes,
 * FEEL syntax and cycles, with the decisions in evaluation order — and Save.
 */
@Component({
  selector: 'pp-calculation-source-tab',
  standalone: true,
  imports: [TranslocoPipe, CalculationSourceEditorComponent],
  template: `
    <div class="pp-calculation-tab">
      <div class="pp-calculation-tab__toolbar">
        <button type="button" class="secondary" data-testid="calculation-validate" [disabled]="busy()" (click)="validate()">
          {{ scope + '.validate' | transloco }}
        </button>
        <button type="button" data-testid="calculation-save" [disabled]="busy() || !dirty()" (click)="save()">{{ scope + '.save' | transloco }}</button>
        @if (dirty()) {
          <span class="muted" data-testid="calculation-unsaved">{{ scope + '.unsaved' | transloco }}</span>
        }
      </div>

      <pp-calculation-source-editor [value]="working()" (valueChange)="edit($event)" />

      @if (failure(); as failure) {
        <p class="error" role="alert" data-testid="calculation-source-failure">{{ failure }}</p>
      }
      @if (report(); as report) {
        @if (report.valid) {
          <p class="ok" data-testid="calculation-valid">{{ scope + '.valid' | transloco }}</p>
        } @else {
          <ul class="error" data-testid="calculation-errors">
            @for (error of report.errors; track $index) {
              <li>{{ error }}</li>
            }
          </ul>
        }
        @if (report.decisions?.length) {
          <table data-testid="calculation-decisions">
            <thead>
              <tr>
                <th>#</th>
                <th>{{ scope + '.decision' | transloco }}</th>
                <th>{{ scope + '.kind' | transloco }}</th>
                <th>{{ scope + '.scope' | transloco }}</th>
                <th>{{ scope + '.requires' | transloco }}</th>
              </tr>
            </thead>
            <tbody>
              @for (decision of ordered(); track decision.name) {
                <tr>
                  <td>{{ decision.position }}</td>
                  <td>{{ decision.name }}</td>
                  <td>{{ decision.kind }}</td>
                  <td>{{ decision.scope }}</td>
                  <td>{{ decision.requires }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
      }
    </div>
  `,
  styles: CALCULATION_TAB_STYLES,
})
export class CalculationSourceTabComponent {
  readonly entityId = input.required<string>();

  protected readonly scope = CALCULATION_SOURCE_I18N_SCOPE;
  private readonly api = inject(CalculationApiService);
  private readonly calculation = currentCalculation(this.entityId);

  protected readonly working = signal('');
  protected readonly report = signal<ValidationReport | undefined>(undefined);
  protected readonly failure = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);
  protected readonly dirty = computed(() => this.calculation.definition() !== undefined && this.working() !== this.calculation.definition()?.source);

  /** The decisions in evaluation order — declaration order where the graph has a cycle — with what each requires. */
  protected readonly ordered = computed(() => {
    const report = this.report();
    if (!report?.decisions) return [];
    const order = report.order?.length ? report.order : report.decisions.map((decision) => decision.name);
    return order
      .map((name) => report.decisions?.find((decision) => decision.name === name))
      .filter((decision) => decision !== undefined)
      .map((decision, index) => ({
        ...decision,
        position: report.order?.length ? index + 1 : '–',
        requires: (report.dependencies ?? []).filter((edge) => edge.to === decision.name).map((edge) => edge.from).join(', '),
      }));
  });

  constructor() {
    // A newly selected or newly saved definition replaces what is being edited.
    effect(() => {
      const definition = this.calculation.definition();
      if (definition) this.working.set(definition.source);
    });
  }

  protected edit(source: string): void {
    this.working.set(source);
  }

  protected async validate(): Promise<void> {
    await this.guarded(async () => this.report.set(await firstValueFrom(this.api.validate(this.working()))));
  }

  protected async save(): Promise<void> {
    await this.guarded(async () => {
      const report = await firstValueFrom(this.api.validate(this.working()));
      this.report.set(report);
      if (!report.valid) return;
      const saved = await this.calculation.save(this.working());
      if (!saved) this.failure.set(this.calculation.store.error() ?? 'Save failed');
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


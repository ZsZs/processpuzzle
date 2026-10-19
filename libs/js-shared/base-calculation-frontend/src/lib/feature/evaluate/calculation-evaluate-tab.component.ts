import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { CALCULATION_EVALUATE_I18N_SCOPE } from '../../base-calculation.i18n';
import type { CalculationRun, EvaluationResult } from '../../domain/calculation-api';
import { CalculationApiService } from '../../domain/calculation-api.service';
import { EntityChoice, EntityChoicesService } from '../../domain/entity-choices.service';
import { CALCULATION_TAB_STYLES } from '../calculation-tab.styles';
import { currentCalculation } from '../current-calculation';
import { messageOf } from '../message-of';
import { CalculationTraceComponent } from '../runs/calculation-trace.component';

/**
 * The Evaluate tab: a value per parameter — an entity parameter is picked from its objects, or takes the object's
 * id when there are too many to list — then Evaluate, which
 * computes the stored draft and stores nothing, or Run, which computes the released definition, writes its
 * output and keeps the run. Both show the values and the trace of every decision.
 */
@Component({
  selector: 'pp-calculation-evaluate-tab',
  standalone: true,
  imports: [TranslocoPipe, CalculationTraceComponent],
  template: `
    <div class="pp-calculation-tab">
      <h3>{{ scope + '.parameters' | transloco }}</h3>
      @if (parameters().length === 0) {
        <p class="muted">{{ scope + '.noParameters' | transloco }}</p>
      }
      <div class="pp-calculation-tab__toolbar">
        @for (parameter of parameters(); track parameter.name) {
          <label>
            {{ parameter.name }}
            @if (parameter.entity && choices()[parameter.entity]?.length) {
              <select [attr.data-testid]="'calculation-parameter-' + parameter.name" [value]="values()[parameter.name] ?? ''" (change)="setValue(parameter.name, $any($event.target).value)">
                <option value="">{{ scope + '.pick' | transloco: { entity: parameter.entity } }}</option>
                @for (choice of choices()[parameter.entity]; track choice.id) {
                  <option [value]="choice.id" [selected]="values()[parameter.name] === choice.id">{{ choice.label }}</option>
                }
              </select>
            } @else {
              <input
                [attr.data-testid]="'calculation-parameter-' + parameter.name"
                [placeholder]="parameter.entity ? (scope + '.entityHint' | transloco: { entity: parameter.entity }) : (parameter.type ?? '')"
                [value]="values()[parameter.name] ?? ''"
                (input)="setValue(parameter.name, $any($event.target).value)"
              />
            }
          </label>
        }
      </div>
      <div class="pp-calculation-tab__toolbar">
        <button type="button" data-testid="calculation-evaluate" [disabled]="busy() || !definition()" (click)="evaluate()">{{ scope + '.evaluate' | transloco }}</button>
        <button type="button" class="secondary" data-testid="calculation-run" [disabled]="busy() || !definition()" (click)="run()">{{ scope + '.run' | transloco }}</button>
      </div>

      @if (failure(); as failure) {
        <p class="error" role="alert" data-testid="calculation-evaluate-failure">{{ failure }}</p>
      }
      @if (lastRun(); as run) {
        <p [class.ok]="run.status === 'COMPLETED'" [class.error]="run.status === 'FAILED'" data-testid="calculation-run-status">
          {{ scope + '.runResult' | transloco: { status: run.status, written: run.written ?? 0, id: run.id } }}
          @if (run.error) {
            — {{ run.error }}
          }
        </p>
      }
      @if (result(); as result) {
        <pp-calculation-trace [result]="result" />
      }
    </div>
  `,
  styles: CALCULATION_TAB_STYLES,
})
export class CalculationEvaluateTabComponent {
  readonly entityId = input.required<string>();

  protected readonly scope = CALCULATION_EVALUATE_I18N_SCOPE;
  private readonly api = inject(CalculationApiService);
  private readonly entityChoices = inject(EntityChoicesService);
  private readonly calculation = currentCalculation(this.entityId);

  protected readonly definition = this.calculation.definition;
  protected readonly parameters = computed(() => this.definition()?.parameters ?? []);
  protected readonly values = signal<Record<string, string>>({});
  protected readonly result = signal<EvaluationResult | undefined>(undefined);
  protected readonly lastRun = signal<CalculationRun | undefined>(undefined);
  protected readonly failure = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);
  /** The objects each entity parameter's type offers, by entity; absent while loading, empty when not listable. */
  protected readonly choices = signal<Record<string, EntityChoice[]>>({});

  constructor() {
    effect(() => {
      const known = untracked(this.choices);
      const entities = new Set(this.parameters().flatMap((parameter) => (parameter.entity ? [parameter.entity] : [])));
      entities.forEach((entity) => {
        if (entity in known) return;
        this.choices.update((choices) => ({ ...choices, [entity]: [] }));
        this.entityChoices.choicesOf(entity).subscribe((offered) => this.choices.update((choices) => ({ ...choices, [entity]: offered })));
      });
    });
  }

  protected setValue(name: string, value: string): void {
    this.values.update((values) => ({ ...values, [name]: value }));
  }

  protected async evaluate(): Promise<void> {
    await this.guarded(async (id) => {
      this.lastRun.set(undefined);
      this.result.set(await firstValueFrom(this.api.evaluate(id, this.supplied(), 'draft')));
    });
  }

  protected async run(): Promise<void> {
    await this.guarded(async (id) => {
      const run = await firstValueFrom(this.api.run(id, this.supplied(), 'released'));
      this.lastRun.set(run);
      this.result.set({ ...run.result, trace: await firstValueFrom(this.api.trace(run.id)), error: run.error });
    });
  }

  /** The values entered; an empty field is left out, so the parameter's default applies. */
  private supplied(): Record<string, unknown> {
    return Object.fromEntries(Object.entries(this.values()).filter(([, value]) => value.trim() !== ''));
  }

  private async guarded(action: (id: string) => Promise<void>): Promise<void> {
    const id = this.definition()?.id;
    if (!id) return;
    this.busy.set(true);
    this.failure.set(undefined);
    try {
      await action(id);
    } catch (error) {
      this.result.set(undefined);
      this.failure.set(messageOf(error));
    } finally {
      this.busy.set(false);
    }
  }
}

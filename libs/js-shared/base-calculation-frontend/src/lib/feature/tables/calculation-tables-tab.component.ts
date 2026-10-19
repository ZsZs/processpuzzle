import { Component, computed, effect, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { CALCULATION_TABLES_I18N_SCOPE } from '../../base-calculation.i18n';
import { type DecisionTableModel, HIT_POLICIES, type HitPolicy, newTable, readTables, removeTable, writeTable, yamlErrorsOf } from '../../domain/calculation-source';
import { CALCULATION_TAB_STYLES } from '../calculation-tab.styles';
import { currentCalculation } from '../current-calculation';
import { messageOf } from '../message-of';

/**
 * The Decision tables tab: a grid over the source's `tables` — hit policy, input columns, output, and the rules,
 * which can be added, removed and reordered. Every edit rewrites that table in the YAML document and leaves the
 * rest of it, comments included, as it was; Save stores the source like the Source tab does.
 *
 * Cells are FEEL as the source holds them: a unary test per input (`>= 1000`, `"GOLD"`, `-` for any) and an
 * expression for the output. Validate on the Source tab checks them.
 */
@Component({
  selector: 'pp-calculation-tables-tab',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <div class="pp-calculation-tab">
      @if (unreadable(); as error) {
        <p class="error" role="alert" data-testid="calculation-tables-unreadable">{{ scope + '.unreadable' | transloco }} {{ error }}</p>
      } @else {
        <div class="pp-calculation-tab__toolbar">
          @for (table of tables(); track table.name) {
            <button type="button" [class.secondary]="table.name !== selected()?.name" [attr.data-testid]="'calculation-table-' + table.name" (click)="select(table.name)">
              {{ table.name }}
            </button>
          }
          <button type="button" class="secondary" data-testid="calculation-table-add" (click)="addTable()">{{ scope + '.add' | transloco }}</button>
          <button type="button" data-testid="calculation-tables-save" [disabled]="busy() || !dirty()" (click)="save()">{{ scope + '.save' | transloco }}</button>
        </div>

        @if (selected(); as table) {
          <div class="pp-calculation-tab__toolbar">
            <label>
              {{ scope + '.name' | transloco }}
              <input data-testid="calculation-table-name" [value]="table.name" (change)="rename($any($event.target).value)" />
            </label>
            <label>
              {{ scope + '.hitPolicy' | transloco }}
              <select data-testid="calculation-table-hit-policy" [value]="table.hitPolicy" (change)="update({ hitPolicy: $any($event.target).value })">
                @for (policy of hitPolicies; track policy) {
                  <option [value]="policy">{{ policy }}</option>
                }
              </select>
            </label>
            <label>
              {{ scope + '.output' | transloco }}
              <input data-testid="calculation-table-output" [value]="table.output" (change)="update({ output: $any($event.target).value })" />
            </label>
            <button type="button" class="secondary" data-testid="calculation-table-remove" (click)="remove()">{{ scope + '.remove' | transloco }}</button>
          </div>

          <table data-testid="calculation-table-grid">
            <thead>
              <tr>
                <th>#</th>
                @for (column of table.inputs; track $index; let c = $index) {
                  <th>
                    <input [attr.data-testid]="'calculation-table-input-' + c" [value]="column" (change)="setInput(c, $any($event.target).value)" />
                    <button type="button" class="secondary" [attr.aria-label]="scope + '.removeInput' | transloco" [disabled]="table.inputs.length === 1" (click)="removeInput(c)">×</button>
                  </th>
                }
                <th>
                  <button type="button" class="secondary" data-testid="calculation-table-add-input" (click)="addInput()">+</button>
                </th>
                <th>{{ scope + '.then' | transloco }}: {{ table.output }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (rule of table.rules; track $index; let r = $index; let last = $last; let first = $first) {
                <tr [attr.data-testid]="'calculation-table-rule-' + r">
                  <td>{{ r + 1 }}</td>
                  @for (cell of rule.when; track $index; let c = $index) {
                    <td><input [value]="cell" [attr.data-testid]="'calculation-table-cell-' + r + '-' + c" (change)="setCell(r, c, $any($event.target).value)" /></td>
                  }
                  <td></td>
                  <td><input [value]="rule.result" [attr.data-testid]="'calculation-table-result-' + r" (change)="setResult(r, $any($event.target).value)" /></td>
                  <td>
                    <button type="button" class="secondary" [disabled]="first" [attr.aria-label]="scope + '.moveUp' | transloco" (click)="move(r, -1)">↑</button>
                    <button type="button" class="secondary" [disabled]="last" [attr.aria-label]="scope + '.moveDown' | transloco" (click)="move(r, 1)">↓</button>
                    <button type="button" class="secondary" [attr.aria-label]="scope + '.removeRule' | transloco" [attr.data-testid]="'calculation-table-remove-rule-' + r" (click)="removeRule(r)">×</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <div class="pp-calculation-tab__toolbar">
            <button type="button" class="secondary" data-testid="calculation-table-add-rule" (click)="addRule()">{{ scope + '.addRule' | transloco }}</button>
          </div>
        } @else {
          <p class="muted" data-testid="calculation-tables-none">{{ scope + '.none' | transloco }}</p>
        }
      }
      @if (failure(); as failure) {
        <p class="error" role="alert" data-testid="calculation-tables-failure">{{ failure }}</p>
      }
    </div>
  `,
  styles: CALCULATION_TAB_STYLES,
})
export class CalculationTablesTabComponent {
  readonly entityId = input.required<string>();

  protected readonly scope = CALCULATION_TABLES_I18N_SCOPE;
  protected readonly hitPolicies = HIT_POLICIES;
  private readonly calculation = currentCalculation(this.entityId);

  protected readonly working = signal('');
  protected readonly selectedName = signal<string | undefined>(undefined);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | undefined>(undefined);

  protected readonly unreadable = computed(() => yamlErrorsOf(this.working())[0]);
  protected readonly tables = computed(() => readTables(this.working()));
  /** The table being edited: the one picked, else the first; none when the source declares none. */
  protected readonly selected = computed<DecisionTableModel | undefined>(() => this.tables().find((table) => table.name === this.selectedName()) ?? this.tables().at(0));
  protected readonly dirty = computed(() => this.calculation.definition() !== undefined && this.working() !== this.calculation.definition()?.source);

  constructor() {
    effect(() => {
      const definition = this.calculation.definition();
      if (definition) this.working.set(definition.source);
    });
  }

  protected select(name: string): void {
    this.selectedName.set(name);
  }

  protected addTable(): void {
    const names = new Set(this.tables().map((table) => table.name));
    let index = 1;
    let name = 'new-table';
    while (names.has(name)) name = `new-table-${++index}`;
    this.write(newTable(name));
    this.selectedName.set(name);
  }

  protected remove(): void {
    const table = this.selected();
    if (!table) return;
    this.apply(() => removeTable(this.working(), table.name));
    this.selectedName.set(undefined);
  }

  protected rename(name: string): void {
    const table = this.selected();
    if (!table || !name.trim() || name === table.name) return;
    this.write({ ...table, name: name.trim() }, table.name);
    this.selectedName.set(name.trim());
  }

  protected update(change: Partial<Pick<DecisionTableModel, 'hitPolicy' | 'output'>>): void {
    this.edit((table) => ({ ...table, ...change, hitPolicy: (change.hitPolicy ?? table.hitPolicy) as HitPolicy }));
  }

  protected setInput(column: number, value: string): void {
    this.edit((table) => ({ ...table, inputs: table.inputs.map((input, index) => (index === column ? value : input)) }));
  }

  protected addInput(): void {
    this.edit((table) => ({ ...table, inputs: [...table.inputs, 'input'], rules: table.rules.map((rule) => ({ ...rule, when: [...rule.when, '-'] })) }));
  }

  protected removeInput(column: number): void {
    this.edit((table) => ({
      ...table,
      inputs: table.inputs.filter((_, index) => index !== column),
      rules: table.rules.map((rule) => ({ ...rule, when: rule.when.filter((_, index) => index !== column) })),
    }));
  }

  protected setCell(row: number, column: number, value: string): void {
    this.editRule(row, (rule) => ({ ...rule, when: rule.when.map((cell, index) => (index === column ? value.trim() || '-' : cell)) }));
  }

  protected setResult(row: number, value: string): void {
    this.editRule(row, (rule) => ({ ...rule, result: value }));
  }

  protected addRule(): void {
    this.edit((table) => ({ ...table, rules: [...table.rules, { when: table.inputs.map(() => '-'), result: '0' }] }));
  }

  protected removeRule(row: number): void {
    this.edit((table) => ({ ...table, rules: table.rules.filter((_, index) => index !== row) }));
  }

  protected move(row: number, by: number): void {
    this.edit((table) => {
      const rules = [...table.rules];
      const [rule] = rules.splice(row, 1);
      rules.splice(row + by, 0, rule);
      return { ...table, rules };
    });
  }

  protected async save(): Promise<void> {
    this.busy.set(true);
    this.failure.set(undefined);
    try {
      const saved = await this.calculation.save(this.working());
      if (!saved) this.failure.set(this.calculation.store.error() ?? 'Save failed');
    } finally {
      this.busy.set(false);
    }
  }

  private editRule(row: number, change: (rule: DecisionTableModel['rules'][number]) => DecisionTableModel['rules'][number]): void {
    this.edit((table) => ({ ...table, rules: table.rules.map((rule, index) => (index === row ? change(rule) : rule)) }));
  }

  private edit(change: (table: DecisionTableModel) => DecisionTableModel): void {
    const table = this.selected();
    if (table) this.write(change(table), table.name);
  }

  private write(table: DecisionTableModel, previousName?: string): void {
    this.apply(() => writeTable(this.working(), table, previousName));
  }

  private apply(rewrite: () => string): void {
    try {
      this.working.set(rewrite());
      this.failure.set(undefined);
    } catch (error) {
      this.failure.set(messageOf(error));
    }
  }
}

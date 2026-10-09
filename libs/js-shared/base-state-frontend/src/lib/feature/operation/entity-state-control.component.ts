import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BaseEntity, BaseFormControlComponent, EntityLabelPipe } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { ENTITY_STATE_CONTROL_I18N_SCOPE } from '../../base-state.i18n';
import { GovernedEntityRegistry } from '../../domain/definition/governed-entity.registry';
import { StateMachineDefinition } from '../../domain/definition/state-machine-definition';
import { AvailableTransition, EntityObjectState } from '../../domain/operation/entity-object-state';
import { EntityObjectStateService } from '../../domain/operation/entity-object-state.service';

/**
 * The STATE control of a governed entity's Details form: where this record is in its machine, and a drop-down of
 * the transitions allowed from there.
 *
 * **Not a value editor.** Its form control is disabled — `BaseEntityFormBuilder` makes every STATE control so —
 * which keeps the state out of the form's PUT; base-state is the only writer of the state attribute. Picking a
 * transition fires its trigger at once, as a command of its own, and the record is then reloaded: firing bumped
 * its version, so without the reload the form's next save would be a 409. Unsaved edits of the other fields
 * survive the reload, because the form keeps its existing controls when it is rebuilt.
 *
 * **Guards are the server's.** The offered transitions come from the operation endpoint's dry run; one whose
 * guards fail is listed disabled with the reason, and the server evaluates the guards again when it fires.
 */
@Component({
  selector: 'pp-entity-state-control',
  standalone: true,
  imports: [MatFormField, MatLabel, MatHint, MatSelect, MatOption, TranslocoPipe, EntityLabelPipe],
  template: `
    @if (config().visible) {
      <mat-form-field floatLabel="always">
        <mat-label>{{ config().i18nKey() | ppLabel: config().label }}</mat-label>
        <mat-select
          [value]="null"
          [placeholder]="initialStateLabel() ? (scope + '.initialState' | transloco: { state: initialStateLabel() }) : currentStateLabel()"
          [disabled]="!canTransition()"
          (selectionChange)="onTransition($event.value, $event.source)"
          [attr.data-testid]="'state-control-select'"
        >
          @for (transition of transitions(); track transition.transitionKey) {
            <mat-option [value]="transition" [disabled]="!transition.guardsSatisfied" [attr.data-testid]="'transition-' + transition.triggerKey">
              {{ scope + '.transitionTo' | transloco: { state: stateLabel(transition.targetStateKey) } }}
              @if (isAmbiguous(transition)) {
                {{ scope + '.via' | transloco: { trigger: transition.triggerKey } }}
              }
              @if (!transition.guardsSatisfied) {
                <span class="pp-state-control__blocked">
                  {{ transition.blockedReason ? (scope + '.blocked' | transloco: { reason: transition.blockedReason }) : (scope + '.blockedNoReason' | transloco) }}
                </span>
              }
            </mat-option>
          }
        </mat-select>
        @if (objectState() && transitions().length === 0) {
          <mat-hint>{{ scope + '.noTransitions' | transloco }}</mat-hint>
        }
      </mat-form-field>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      mat-form-field {
        width: 100%;
      }
      /* The placeholder is the current state here, not a prompt — so it reads as a value, not greyed out. */
      :host ::ng-deep .mat-mdc-select-placeholder {
        color: inherit;
      }
      .pp-state-control__blocked {
        display: block;
        font-size: 12px;
        color: #d9534f;
      }
    `,
  ],
})
export class EntityStateControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  protected readonly scope = ENTITY_STATE_CONTROL_I18N_SCOPE;

  private readonly governed = inject(GovernedEntityRegistry);
  private readonly objectStates = inject(EntityObjectStateService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  private readonly machine = signal<StateMachineDefinition | undefined>(undefined);
  protected readonly objectState = signal<EntityObjectState | undefined>(undefined);
  private readonly busy = signal(false);

  protected readonly transitions = computed<AvailableTransition[]>(() => this.objectState()?.availableTransitions ?? []);
  protected readonly canTransition = computed(() => !this.busy() && !this.config().disabled && this.transitions().some((transition) => transition.guardsSatisfied));

  /**
   * The current state's name, shown as the select's placeholder. A record the operation endpoint does not know
   * falls back to the attribute's own value.
   */
  protected readonly currentStateLabel = computed(() => {
    const current = this.objectState()?.currentStateKey ?? (this.value() == null ? undefined : String(this.value()));
    return current ? this.stateLabel(current) : '';
  });

  /**
   * The initial state's name for a new record, which has no object state yet — it is created in that state.
   * Translated in the template rather than here, so the text follows the scope once it has loaded.
   */
  protected readonly initialStateLabel = computed(() => {
    const initial = this.machine()?.initialStateKey;
    return !this.entity().id && !this.objectState() && initial ? this.stateLabel(initial) : undefined;
  });

  async ngOnInit(): Promise<void> {
    const machine = await this.governed.machineFor(this.entityName());
    this.machine.set(machine);
    const objectId = this.entity().id;
    if (machine && objectId) {
      this.objectState.set(await firstValueFrom(this.objectStates.findState(machine.entityName, objectId)).catch(() => undefined));
    }
  }

  /** The state's own name where it has one, its key otherwise — the rule the State Machine tab follows. */
  protected stateLabel(stateKey: string): string {
    return this.machine()?.states.find((state) => state.key === stateKey)?.name || stateKey;
  }

  /** Two transitions to the same target are told apart by their trigger. */
  protected isAmbiguous(transition: AvailableTransition): boolean {
    return this.transitions().filter((candidate) => candidate.targetStateKey === transition.targetStateKey).length > 1;
  }

  protected async onTransition(transition: AvailableTransition | null, select: MatSelect): Promise<void> {
    // The select only ever triggers; it never holds a value, so the placeholder keeps showing the current state.
    select.writeValue(null);
    const machine = this.machine();
    const objectId = this.entity().id;
    if (!transition || !machine || !objectId) return;

    this.busy.set(true);
    try {
      const version = Number((this.entity() as { version?: number }).version ?? 0);
      const result = await firstValueFrom(this.objectStates.fireTransition(machine.entityName, objectId, transition.triggerKey, version));
      const message = result.success
        ? this.transloco.translate(`${this.scope}.fired`, { state: this.stateLabel(result.newStateKey ?? transition.targetStateKey) })
        : this.transloco.translate(`${this.scope}.rejected`, { reason: result.rejectionReason ?? '' });
      this.snackBar.open(message, undefined, { duration: 4000 });
    } catch (error) {
      // A 409 or a 5xx has already been reported by the HTTP error interceptor; reloading below is the remedy.
      this.logger.warn('EntityStateControlComponent failed to fire', { trigger: transition.triggerKey, error });
    } finally {
      this.busy.set(false);
    }
    // Rebuilds the form — and with it this control, which then reads the new state afresh.
    await this.store.reload(objectId);
  }
}

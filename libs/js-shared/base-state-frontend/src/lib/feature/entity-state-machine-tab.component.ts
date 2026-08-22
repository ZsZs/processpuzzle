import { Component, computed, effect, inject, input, signal, Signal } from '@angular/core';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { ENTITY_STATE_MACHINE_I18N_SCOPE } from '../base-state.i18n';
import { GovernedEntityRegistry } from '../domain/governed-entity.registry';
import { DiagramDefinitionService } from '../domain/modeler/data-access/diagram-definition.service';
import { DiagramDefinition } from '../domain/modeler/models/diagram-definition';
import { EntityObjectState } from '../domain/operation/entity-object-state';
import { EntityObjectStateService } from '../domain/operation/entity-object-state.service';
import { StateMachineDefinition } from '../domain/state-machine-definition';
import { StateMachineCanvasComponent } from './modeler/components/state-machine-canvas.component';

/** What one load produced — kept as one signal so the three fields can never be half-updated. */
interface TabView {
  machine?: StateMachineDefinition;
  layout?: DiagramDefinition;
  objectState?: EntityObjectState;
}

/**
 * The State Machine tab of a **governed entity** — mounted at `<entity>/<id>/state-machine` beside that
 * entity's own Details form, and contributed onto it by `StateMachineTabContributor` rather than declared
 * by whoever owns the entity.
 *
 * It answers one question: where is *this record* in its lifecycle. The machine is drawn as the modeler
 * draws it — same canvas, same UML shapes, same saved arrangement — with the state the record currently
 * sits in ringed, and with every gesture that could change something removed. Authoring stays where it
 * belongs, under `BASE_STATE_ROUTES`; a caption says so, because a diagram that cannot be edited and does
 * not explain why reads as broken.
 *
 * **Two names for the entity.** The descriptor names it `Order`; base-state keys its machine, its diagram
 * and its operation endpoint by the definition *code*, `order`. {@link GovernedEntityRegistry} owns that
 * translation, and everything below asks it rather than snake-casing anything itself.
 *
 * **The state is read from the operation endpoint, not off the record.** base-state stores no copy of an
 * object's state — the value lives in the record's own `stateAttributeKey` attribute, which this component
 * could read from the form's store — but the endpoint additionally falls back to the machine's
 * `initialStateKey` when that attribute is empty. That is not a corner case: `GovernedStateConsistencyCheck`
 * documents seeded objects landing exactly there. Reading the attribute would show such a record as having
 * no state at all while the server considers it to be in the initial one.
 */
@Component({
  selector: 'pp-entity-state-machine-tab',
  standalone: true,
  imports: [StateMachineCanvasComponent, TranslocoPipe],
  template: `
    <div class="pp-entity-state-machine">
      @if (isLoading()) {
        <p class="pp-entity-state-machine__note">{{ scope + '.loading' | transloco }}</p>
      } @else if (!machine()) {
        <p class="pp-entity-state-machine__note">{{ scope + '.noMachine' | transloco }}</p>
      } @else {
        <header class="pp-entity-state-machine__header">
          <div class="pp-entity-state-machine__summary">
            <span class="pp-entity-state-machine__label">{{ scope + '.currentState' | transloco }}</span>
            @if (currentStateKey(); as stateKey) {
              <span class="pp-entity-state-machine__state" [attr.data-testid]="'current-state'">{{ currentStateLabel() }}</span>
              @if (objectState()?.isFinal) {
                <span class="pp-entity-state-machine__final">{{ scope + '.final' | transloco }}</span>
              }
              @if (!stateIsDeclared()) {
                <span class="pp-entity-state-machine__warning" role="alert">{{ scope + '.unknownState' | transloco: { state: stateKey } }}</span>
              }
            } @else {
              <span class="pp-entity-state-machine__state">{{ scope + '.noState' | transloco }}</span>
            }
          </div>
          <p class="pp-entity-state-machine__note">{{ scope + '.readOnly' | transloco }}</p>
        </header>

        <div class="pp-entity-state-machine__body">
          <pp-state-machine-canvas class="pp-entity-state-machine__canvas" [machine]="machine()" [layout]="layout()" [currentStateKey]="currentStateKey()" [readOnly]="true" />
        </div>
      }
    </div>
  `,
  styles: [
    `
      /* The same white card surface as the status bar above it — same white, same corner radius — so the
         tab sits in the page the way the Details form and the modeler do. */
      .pp-entity-state-machine {
        display: flex;
        flex-direction: column;
        gap: 8px;
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-entity-state-machine__header {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .pp-entity-state-machine__summary {
        display: flex;
        align-items: baseline;
        flex-wrap: wrap;
        gap: 8px;
      }
      .pp-entity-state-machine__label {
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: #666;
      }
      .pp-entity-state-machine__state {
        font-size: 16px;
        font-weight: 600;
      }
      /* The same ring the current node wears, shrunk to a chip — so the caption and the diagram are
         visibly saying the same thing. */
      .pp-entity-state-machine__final {
        padding: 1px 8px;
        border-radius: 10px;
        font-size: 12px;
        background: var(--pp-color-light-green, rgb(92, 218, 207));
        color: var(--pp-color-dark-blue, rgb(24, 111, 206));
      }
      .pp-entity-state-machine__warning {
        font-size: 12px;
        color: #d9534f;
      }
      .pp-entity-state-machine__note {
        margin: 0;
        font-size: 12px;
        color: #666;
      }
      /* A grid rather than another flex child, and this is not cosmetic: the ng-diagram element sizes
         itself to 100% of its container, so the canvas has to be given a *definite* height or that
         element computes to zero and clips everything it drew. A grid item stretches to its row, and the
         row here has a height — whereas a flex item in a column carrying only a minimum height leaves the
         percentage resolving against an indefinite parent, which is auto, which is nothing. Same
         arrangement as the modeler's own body, which is where this was learned. */
      .pp-entity-state-machine__body {
        display: grid;
        grid-template-columns: 1fr;
        min-height: 420px;
      }
      .pp-entity-state-machine__canvas {
        border: 1px solid #cccccc;
        border-radius: 4px;
      }
    `,
  ],
})
export class EntityStateMachineTabComponent {
  /**
   * Bound from the route's `:entityId` param by `withComponentInputBinding()`, the same way
   * `BaseEntityFormComponent` receives it — so a deep link and a reload resolve the same record as a click
   * through the tab does.
   */
  readonly entityId = input.required<string>();

  protected readonly scope = ENTITY_STATE_MACHINE_I18N_SCOPE;

  /**
   * The descriptor of the entity this tab was mounted on, handed down by `BaseEntityTabsComponent`'s
   * `<router-outlet [routerOutletData]="entityDescriptor()">`.
   *
   * Read from the outlet rather than from `BaseFormNavigatorSingletonStore.entityName()`: the navigator is
   * one store for the whole application and its `entityName` follows the last navigation, which on an
   * embedded drill-down is a child rather than the entity whose screens this tab belongs to. The outlet
   * data is scoped to exactly this outlet and cannot be wrong.
   */
  private readonly outletData = inject(ROUTER_OUTLET_DATA, { optional: true }) as Signal<BaseEntityDescriptor | undefined> | null;

  private readonly governed = inject(GovernedEntityRegistry);
  private readonly diagrams = inject(DiagramDefinitionService);
  private readonly objectStates = inject(EntityObjectStateService);

  private readonly viewSignal = signal<TabView>({});
  private readonly loadingSignal = signal(true);

  protected readonly isLoading = this.loadingSignal.asReadonly();
  protected readonly machine = computed(() => this.viewSignal().machine);
  protected readonly layout = computed(() => this.viewSignal().layout);
  protected readonly objectState = computed(() => this.viewSignal().objectState);
  protected readonly currentStateKey = computed(() => this.viewSignal().objectState?.currentStateKey);

  /**
   * Whether the key the server reported is one the machine declares. It normally is; it is not when a
   * record was left behind by an edit that removed the state it was sitting in, which the caption names
   * rather than letting the diagram silently highlight nothing.
   */
  protected readonly stateIsDeclared = computed(() => {
    const key = this.currentStateKey();
    return key !== undefined && (this.machine()?.states ?? []).some((state) => state.key === key);
  });

  /** The state's own name where it has one, its key otherwise — the same rule the node labels follow. */
  protected readonly currentStateLabel = computed(() => {
    const key = this.currentStateKey();
    if (!key) return '';
    const state = (this.machine()?.states ?? []).find((candidate) => candidate.key === key);
    return state?.name || key;
  });

  constructor() {
    // An effect rather than `ngOnInit`, because both halves of the subject can change without this
    // component being recreated: the router reuses it when only `:entityId` changes — clicking a different
    // row and coming back to this tab — and the descriptor arrives through an outlet input.
    effect(() => {
      const entityName = this.outletData?.()?.entityName;
      const objectId = this.entityId();
      void this.load(entityName, objectId);
    });
  }

  /**
   * Fetches the three things the screen is made of, tolerating the absence of each.
   *
   * The machine comes first and short-circuits: an entity with no machine has nothing to arrange and no
   * state to read, and asking anyway would be two requests answering 404. The other two are fetched
   * together — neither depends on the other, and a diagram that has never been arranged is as normal as a
   * record that has just been created.
   *
   * Failures are swallowed into an empty view rather than surfaced: the HTTP error interceptor has already
   * told the user, and this tab is a read-only aside on someone else's form.
   */
  private async load(entityName: string | undefined, objectId: string | undefined): Promise<void> {
    this.loadingSignal.set(true);
    try {
      const machine = await this.governed.machineFor(entityName);
      if (!machine) {
        this.viewSignal.set({});
        return;
      }

      const machineKey = machine.entityName;
      const [layout, objectState] = await Promise.all([
        firstValueFrom(this.diagrams.findByEntityName(machineKey)).catch(() => undefined),
        objectId ? firstValueFrom(this.objectStates.findState(machineKey, objectId)).catch(() => undefined) : Promise.resolve(undefined),
      ]);
      this.viewSignal.set({ machine, layout, objectState });
    } finally {
      this.loadingSignal.set(false);
    }
  }
}

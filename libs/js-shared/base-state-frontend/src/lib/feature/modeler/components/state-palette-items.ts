import { NgDiagramPaletteItem } from 'ng-diagram';
import { State } from '../../../domain/state-machine-definition';
import { STATE_NODE_TYPE, StateNodeData } from '../../../domain/modeler/graph/state-machine-graph';

/**
 * The three symbols {@link ElementPaletteComponent} offers, and the rules that turn one of them into a
 * {@link State} once it has been dropped.
 *
 * `base-state-api.yaml` declares no start and no end *node*: a machine's entry point is the state named by
 * `StateMachineDefinition.initialStateKey` and an exit is a state whose `isFinal` flag is set. All three
 * symbols therefore produce an ordinary state, and what distinguishes them is which of those two things
 * the drop also does — which is exactly what {@link PaletteStateKind} records and nothing else in the
 * modeler has to know.
 *
 * Kept apart from the palette component and free of Angular, because {@link nextStateKey} is a rule about
 * a machine's keys rather than about a template, and a rule is worth being able to test on its own.
 */

/** Which of the three palette symbols a dropped node came from. */
export type PaletteStateKind = 'start' | 'end' | 'state';

/**
 * What a palette item carries in `data`, and therefore what the node ng-diagram creates from it starts
 * out with.
 *
 * A {@link StateNodeData} already — so `StateNodeComponent` can draw the item in the palette and in the
 * drag preview with no second template — plus the {@link PaletteStateKind} the drop handler reads. The
 * `state` in it is a placeholder: `data` is *spread* into the new node by ng-diagram's palette drop
 * handler, so every drop of the same item would otherwise share one `State` instance.
 */
export interface PaletteStateNodeData extends StateNodeData {
  kind: PaletteStateKind;
}

/** Prefix of the keys {@link nextStateKey} mints. Upper case, as the seeded machines spell their states. */
const STATE_KEY_PREFIX = 'STATE_';

/**
 * The palette, in the order it is shown: the entry point first, then the exit, then the plain state —
 * left to right through a machine's life, which is also the order `DagreLayoutService` lays a graph out in.
 */
export const STATE_PALETTE_ITEMS: NgDiagramPaletteItem<PaletteStateNodeData>[] = (['start', 'end', 'state'] as const).map((kind) => ({
  type: STATE_NODE_TYPE,
  // Sized by its content, as a state node drawn from a saved layout is when that layout recorded no size.
  autoSize: true,
  data: { ...newStateData(kind, ''), kind },
}));

/**
 * The lowest `STATE_n` no state uses yet.
 *
 * Counts up past gaps rather than off the highest key in use: a machine that had `STATE_1` deleted should
 * be able to reuse it, and a key derived from `states.length` would collide the moment one is removed.
 * Keys the user wrote by hand on the Details tab are taken into account simply by being in `existingKeys` —
 * only `STATE_n` shaped ones can collide, and those are compared as the strings they are.
 */
export function nextStateKey(existingKeys: Iterable<string>): string {
  const taken = new Set(existingKeys);
  let ordinal = 1;
  while (taken.has(`${STATE_KEY_PREFIX}${ordinal}`)) ordinal++;
  return `${STATE_KEY_PREFIX}${ordinal}`;
}

/**
 * The node data a drop installs: a real {@link State} under `key`, flagged according to `kind`.
 *
 * `initial` is only reported here; making it true of *one* state is the canvas's job, since it means
 * unsetting it on whichever state held it before.
 */
export function newStateData(kind: PaletteStateKind, key: string): StateNodeData {
  const state = new State({ key, name: defaultStateName(key), isFinal: kind === 'end' });
  return { state, label: state.name, initial: kind === 'start' };
}

/**
 * Which of the three symbols a state *reads* as — the inverse of {@link newStateData}, and what
 * {@link StateNodeComponent} picks a shape by.
 *
 * `initial` beats `isFinal`, because a machine whose one state is both its entry point and its exit still
 * has to be drawn as something, and where an object starts is the more useful of the two facts. A state
 * with neither flag is a plain one, which is also what every state of a machine written by hand on the
 * Details tab reads as until one of the two flags is set.
 */
export function stateKind(state: State, initial: boolean): PaletteStateKind {
  if (initial) return 'start';
  return state.isFinal ? 'end' : 'state';
}

/**
 * `STATE_3` reads back as `State 3`. A default the user is expected to replace in the properties panel, so
 * it is deliberately plain — and it is persisted data rather than UI chrome, which is why it is not
 * translated: a machine's states would otherwise be named by whoever happened to draw them.
 */
function defaultStateName(key: string): string {
  return key.startsWith(STATE_KEY_PREFIX) ? `State ${key.slice(STATE_KEY_PREFIX.length)}` : key;
}

import type { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { ENTITY_STATE_MACHINE_I18N_KEY } from '../base-state.i18n';
import { EntityStateMachineTabComponent } from './entity-state-machine-tab.component';

/**
 * URL segment the State Machine tab is mounted at, appended to `<entity>/<id>/`. A constant because the
 * same value has to reach two places — the route `baseEntityRoutes` builds and the link the tab bar
 * renders — and a mismatch would be a tab navigating to a URL nothing matches.
 */
export const ENTITY_STATE_MACHINE_TAB_SEGMENT = 'state-machine';

/**
 * The State Machine tab as `EntityScreenResolver` hands it to `baseEntityRoutes`: a read-only view of the
 * machine governing whatever entity it was contributed onto.
 *
 * Declared once and shared by every governed entity — the descriptor holds no entity-specific state, and
 * the component works out which machine it is drawing from the outlet data it is rendered with. That is
 * what makes `EntityScreenResolver`'s deduplication by segment safe: two entities contributing "the same
 * tab" really are contributing the same object.
 */
export const ENTITY_STATE_MACHINE_TAB: EntityTabDescriptor = {
  segment: ENTITY_STATE_MACHINE_TAB_SEGMENT,
  i18nKey: ENTITY_STATE_MACHINE_I18N_KEY,
  component: EntityStateMachineTabComponent,
};

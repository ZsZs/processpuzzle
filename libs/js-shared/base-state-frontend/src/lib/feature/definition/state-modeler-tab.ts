import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { STATE_MODELER_I18N_KEY } from '../../base-state.i18n';
import { StateModelerTabComponent } from './state-modeler-tab.component';

/**
 * The State Modeler tab, declared once and consumed twice: {@link BASE_STATE_ROUTES} mounts it as
 * `state-machine-definition/<entityName>/modeler`, and `StateMachineDefinitionFacade` puts it on the
 * descriptor so the tab bar renders the link. One constant rather than two literals because the segment
 * is what ties the link to the route — a mismatch would render a tab that navigates to a URL nothing
 * matches.
 *
 * `testIdSuffix` is left unset: the framework's default is already `show-modeler`.
 */
export const STATE_MODELER_TAB: EntityTabDescriptor = {
  segment: 'modeler',
  i18nKey: STATE_MODELER_I18N_KEY,
  component: StateModelerTabComponent,
};

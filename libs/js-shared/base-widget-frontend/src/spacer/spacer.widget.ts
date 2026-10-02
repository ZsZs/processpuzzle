import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { SpacerComponent } from './spacer.component';

export const SPACER_WIDGET = 'spacer';

export const SPACER_WIDGET_REGISTRATION: WidgetRegistration = {
  type: SPACER_WIDGET,
  component: SpacerComponent,
  definition: {
    name: 'Spacer',
    translocoId: 'base_widget.spacer.name',
    description: 'Empty space that fills the row, pushing the widgets after it to the far end. Two spacers centre the widgets between them.',
    category: 'Layout',
    icon: 'space_bar',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

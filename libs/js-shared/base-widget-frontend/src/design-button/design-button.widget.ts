import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { DesignButtonComponent } from './design-button.component';

export const DESIGN_BUTTON_WIDGET = 'design-button';

export const DESIGN_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: DESIGN_BUTTON_WIDGET,
  component: DesignButtonComponent,
  definition: {
    name: 'Design button',
    translocoId: 'base_widget.design_button.name',
    description: 'Toggles between the application and its designer, under the route prefix the application provides.',
    category: 'Navigation',
    icon: 'design_services',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

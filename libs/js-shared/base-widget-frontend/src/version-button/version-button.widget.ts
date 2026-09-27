import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { VersionButtonComponent } from './version-button.component';

export const VERSION_BUTTON_WIDGET = 'version-button';

export const VERSION_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: VERSION_BUTTON_WIDGET,
  component: VersionButtonComponent,
  definition: {
    name: 'Version button',
    translocoId: 'base_widget.version_button.name',
    description: "Shows the running application's version, read from its runtime configuration.",
    category: 'System',
    icon: 'info',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

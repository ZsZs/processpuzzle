import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { ThemesButtonComponent } from './themes-button.component';

export const THEMES_BUTTON_WIDGET = 'themes-button';

export const THEMES_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: THEMES_BUTTON_WIDGET,
  component: ThemesButtonComponent,
  definition: {
    name: 'Themes button',
    translocoId: 'base_widget.themes_button.name',
    description:
      "Lets the user pick one of the theme presets and a light, dark or automatic colour scheme. Inside an application it overrides that application's theme and is remembered per application.",
    category: 'Navigation',
    icon: 'palette',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
    outputPorts: [
      {
        name: 'themeChange',
        type: 'OBJECT',
        description: 'The selection, preset and colour scheme, after every change the user makes.',
      },
    ],
  },
};

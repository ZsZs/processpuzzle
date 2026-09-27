import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { LanguageSelectorComponent } from './language-selector.component';

export const LANGUAGE_SELECTOR_WIDGET = 'language-selector';

export const LANGUAGE_SELECTOR_WIDGET_REGISTRATION: WidgetRegistration = {
  type: LANGUAGE_SELECTOR_WIDGET,
  component: LanguageSelectorComponent,
  definition: {
    name: 'Language selector',
    translocoId: 'base_widget.language_selector.name',
    description: 'Switches the active language, listing the locales the application was configured with.',
    category: 'Navigation',
    icon: 'translate',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

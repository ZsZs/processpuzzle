import { NavigateBackComponent } from '@processpuzzle/util';
import { WidgetRegistration } from '../widget-registry/widget-registry.token';

export const NAVIGATE_BACK_WIDGET = 'navigate-back';

// The component lives in util, below this library, and is used directly in templates as well; only its
// registration as a widget is this library's.
export const NAVIGATE_BACK_WIDGET_REGISTRATION: WidgetRegistration = {
  type: NAVIGATE_BACK_WIDGET,
  component: NavigateBackComponent,
  definition: {
    name: 'Navigate back',
    translocoId: 'base_widget.navigate_back.name',
    description: 'Returns to the previous page of the navigation history.',
    category: 'Navigation',
    icon: 'arrow_back',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

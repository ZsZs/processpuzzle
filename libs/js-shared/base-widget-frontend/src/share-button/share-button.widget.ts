import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { ShareButtonComponent } from './share-button.component';

export const SHARE_BUTTON_WIDGET = 'share-button';

export const SHARE_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: SHARE_BUTTON_WIDGET,
  component: ShareButtonComponent,
  definition: {
    name: 'Share button',
    translocoId: 'base_widget.share_button.name',
    description: 'Shares the current page to the social networks the application enabled.',
    category: 'Social',
    icon: 'share',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

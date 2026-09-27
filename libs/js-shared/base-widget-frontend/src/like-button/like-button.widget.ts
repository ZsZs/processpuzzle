import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { LikeButtonComponent } from './like-button.component';

export const LIKE_BUTTON_WIDGET = 'like-button';

export const LIKE_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: LIKE_BUTTON_WIDGET,
  component: LikeButtonComponent,
  definition: {
    name: 'Like button',
    translocoId: 'base_widget.like_button.name',
    description: 'A toggle that counts likes of the current application, persisted through the application property store.',
    category: 'Social',
    icon: 'thumb_up',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

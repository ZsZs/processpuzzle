import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { ImageZoomComponent } from './image-zoom.component';

export const IMAGE_ZOOM_WIDGET = 'image-zoom';

export const IMAGE_ZOOM_WIDGET_REGISTRATION: WidgetRegistration = {
  type: IMAGE_ZOOM_WIDGET,
  component: ImageZoomComponent,
  definition: {
    name: 'Image zoom',
    translocoId: 'base_widget.image_zoom.name',
    description: 'An image thumbnail that opens full size on click.',
    category: 'Content',
    icon: 'zoom_in',
    propsSchema: {
      type: 'object',
      properties: {
        src: {
          type: 'string',
          title: 'Image URL',
          format: 'uri',
        },
        alt: {
          type: 'string',
          title: 'Alternative text',
        },
      },
      required: ['src'],
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'src',
        type: 'STRING',
        required: true,
        description: 'URL of the image.',
      },
    ],
  },
};

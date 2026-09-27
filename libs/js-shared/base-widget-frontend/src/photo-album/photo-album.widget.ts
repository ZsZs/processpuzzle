import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { PhotoAlbumComponent } from './photo-album.component';

export const PHOTO_ALBUM_WIDGET = 'photo-album';

export const PHOTO_ALBUM_WIDGET_REGISTRATION: WidgetRegistration = {
  type: PHOTO_ALBUM_WIDGET,
  component: PhotoAlbumComponent,
  definition: {
    name: 'Photo album',
    translocoId: 'base_widget.photo_album.name',
    description: 'Pages through a series of images, each opening full size on click.',
    category: 'Content',
    icon: 'photo_library',
    propsSchema: {
      type: 'object',
      properties: {
        images: {
          type: 'array',
          title: 'Images',
          items: {
            type: 'object',
            properties: {
              src: {
                type: 'string',
                format: 'uri',
              },
              alt: {
                type: 'string',
              },
            },
            required: ['src', 'alt'],
          },
        },
        maxHeight: {
          type: 'string',
          title: 'Maximum height',
          description: 'A CSS length, or a number of pixels. Defaults to 240px.',
        },
      },
      required: ['images'],
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'images',
        type: 'ARRAY',
        required: true,
        description: 'The images to page through, in order.',
      },
    ],
  },
};

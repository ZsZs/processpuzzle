import type { PropsSchema } from '../widget-definition/widget-definition';

export function createNavigationPropsSchema(visibilities: readonly string[], visibilityDescription: string): PropsSchema {
  return {
    type: 'object',
    properties: {
      visibility: {
        type: 'string',
        title: 'Visibility',
        enum: [...visibilities],
        description: visibilityDescription,
      },
      items: {
        type: 'array',
        title: 'Items',
        description: "Overrides the application's navigation.",
        items: {
          type: 'object',
          properties: {
            id: {
              type: 'string',
            },
            label: {
              type: 'string',
            },
            translocoId: {
              type: 'string',
            },
            icon: {
              type: 'string',
            },
            routePath: {
              type: 'string',
            },
            children: {
              type: 'array',
            },
          },
          required: ['id', 'label'],
        },
      },
    },
    additionalProperties: false,
  };
}

import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { CopyrightComponent } from './copyright.component';

export const COPYRIGHT_WIDGET = 'copyright';

export const COPYRIGHT_WIDGET_REGISTRATION: WidgetRegistration = {
  type: COPYRIGHT_WIDGET,
  component: CopyrightComponent,
  definition: {
    name: 'Copyright',
    translocoId: 'base_widget.copyright.name',
    description: 'A copyright notice, prefixed with the © sign.',
    category: 'System',
    icon: 'copyright',
    propsSchema: {
      type: 'object',
      properties: {
        text: {
          type: 'string',
          title: 'Text',
          description: 'The notice after the © sign, e.g. "2026 ProcessPuzzle".',
        },
      },
      required: ['text'],
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'text',
        type: 'STRING',
        required: true,
        description: 'The notice after the © sign.',
      },
    ],
  },
};

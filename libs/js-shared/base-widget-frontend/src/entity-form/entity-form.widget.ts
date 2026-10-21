import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { EntityFormComponent } from './entity-form.component';

export const ENTITY_FORM_WIDGET = 'entity-form';

// `entity` is the one bound input: a document binds it to an ENTITY_REF port (`order`). `entityName` says which
// descriptor lays the object out, so it is a prop the author types, not a port.
export const ENTITY_FORM_WIDGET_REGISTRATION: WidgetRegistration = {
  type: ENTITY_FORM_WIDGET,
  component: EntityFormComponent,
  definition: {
    name: 'Entity form',
    translocoId: 'base_widget.entity_form.name',
    description: 'The generated form of one object, laid out by its entity type and shown in place — without Save, Cancel or Delete.',
    category: 'Data',
    icon: 'article',
    propsSchema: {
      type: 'object',
      properties: {
        entityName: {
          type: 'string',
          title: 'Entity name',
          description: 'The entity type whose form lays the object out.',
        },
        readOnly: {
          type: 'boolean',
          title: 'Read only',
          description: 'Disable every control. On by default: the widget never saves.',
          default: true,
        },
        entity: {
          type: 'object',
          title: 'Entity',
          description: 'The object to show. Usually bound to a port rather than typed in.',
        },
      },
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'entity',
        type: 'ENTITY_REF',
        required: false,
        description: 'The object to show.',
      },
    ],
  },
};

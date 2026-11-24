import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { VideoPlayerComponent } from './video-player.component';

export const VIDEO_PLAYER_WIDGET = 'video-player';

/**
 * Plays a video an entity row holds. Which rows it reads is set by its props, so the same widget serves any entity with
 * an ARTIFACT attribute: a designer names the entity, the query that selects the row, and the attribute.
 */
export const VIDEO_PLAYER_WIDGET_REGISTRATION: WidgetRegistration = {
  type: VIDEO_PLAYER_WIDGET,
  component: VideoPlayerComponent,
  definition: {
    name: 'Video player',
    translocoId: 'base_widget.video_player.name',
    description: 'Plays the video an entity row holds, in the application language, beside an optional text attribute of the same row.',
    category: 'Content',
    icon: 'smart_display',
    propsSchema: {
      type: 'object',
      properties: {
        entityName: {
          type: 'string',
          title: 'Entity name',
          description: 'The entity whose rows are read, by its definition code.',
        },
        query: {
          type: 'string',
          title: 'Query',
          description: 'An RSQL query that selects the row, such as name==entity-designer.',
        },
        attribute: {
          type: 'string',
          title: 'Video attribute',
          description: 'The ARTIFACT attribute that holds the video.',
        },
        textAttribute: {
          type: 'string',
          title: 'Text attribute',
          description: 'A text attribute shown beneath the video. Nothing is shown when left empty.',
        },
      },
      required: ['entityName', 'query', 'attribute'],
      additionalProperties: false,
    },
  },
};

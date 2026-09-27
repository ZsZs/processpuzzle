import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { MarkdownPageComponent } from './markdown-page.component';

export const MARKDOWN_PAGE_WIDGET = 'markdown-page';

export const MARKDOWN_PAGE_WIDGET_REGISTRATION: WidgetRegistration = {
  type: MARKDOWN_PAGE_WIDGET,
  component: MarkdownPageComponent,
  definition: {
    name: 'Markdown page',
    translocoId: 'base_widget.markdown_page.name',
    description: 'Renders a Markdown document fetched from a URL.',
    category: 'Content',
    icon: 'article',
    propsSchema: {
      type: 'object',
      properties: {
        markdownSrc: {
          type: 'string',
          title: 'Markdown source',
          description: 'URL of the document to render.',
        },
      },
      required: ['markdownSrc'],
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'markdownSrc',
        type: 'STRING',
        required: true,
        description: 'URL of the document to render.',
      },
    ],
  },
};

import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';
import { ContentBookWidgetComponent } from './content-book-widget.component';

export const CONTENT_BOOK_WIDGET = 'content-book';

/**
 * Registered by base-document rather than base-widget: the widget reads documents, and base-document already
 * depends on base-widget — the other way round would close a cycle. The seed catalogue carries a copy of this
 * definition all the same, so a designer can place it; `content-book-widget.contract.spec.ts` keeps the two equal.
 */
export const CONTENT_BOOK_WIDGET_REGISTRATION: WidgetRegistration = {
  type: CONTENT_BOOK_WIDGET,
  component: ContentBookWidgetComponent,
  definition: {
    name: 'Content book',
    translocoId: 'base_document.content_book._self',
    description: 'A book of documents: its table of contents as a tree, in the sidenav when there is one, and the selected page beside it. Editors arrange the pages in place.',
    category: 'Content',
    icon: 'menu_book',
    propsSchema: {
      type: 'object',
      properties: {
        bookSlug: {
          type: 'string',
          title: 'Book',
          description: 'Slug of the content book to show.',
        },
        locale: {
          type: 'string',
          title: 'Language',
          description: 'Language the pages are read in; the active language when left empty.',
        },
        sidenav: {
          type: 'boolean',
          title: 'Contents in the sidenav',
          description: 'Show the table of contents in the application sidenav instead of beside the page.',
          default: true,
        },
        startDocumentSlug: {
          type: 'string',
          title: 'First page',
          description: 'Slug of the document opened first; the first page of the book when left empty.',
        },
      },
      required: ['bookSlug'],
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'bookSlug',
        type: 'STRING',
        required: true,
        description: 'Slug of the content book to show.',
      },
    ],
    outputPorts: [
      {
        name: 'documentSelected',
        type: 'STRING',
        description: 'Slug of the document the reader opens.',
      },
    ],
  },
};

export function provideContentBookWidget(): Provider[] {
  return provideWidget(CONTENT_BOOK_WIDGET_REGISTRATION);
}

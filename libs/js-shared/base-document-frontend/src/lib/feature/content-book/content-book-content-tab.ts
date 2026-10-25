import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { CONTENT_BOOK_CONTENT_I18N_KEY } from '../../base-document.i18n';
import { ContentBookContentTabComponent } from './content-book-content-tab.component';

/** The Content tab of a content book, declared once for the route and the tab bar — as DOCUMENT_CONTENT_TAB is. */
export const CONTENT_BOOK_CONTENT_TAB: EntityTabDescriptor = {
  segment: 'content',
  i18nKey: CONTENT_BOOK_CONTENT_I18N_KEY,
  component: ContentBookContentTabComponent,
  // The book's own toolbar carries its title; the breadcrumb above it would name the same book twice.
  hideStatusbar: true,
  testIdSuffix: 'show-content',
};

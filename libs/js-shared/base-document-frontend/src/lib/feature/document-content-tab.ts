import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { DOCUMENT_CONTENT_I18N_KEY } from '../base-document.i18n';
import { DocumentContentTabComponent } from './document-content-tab.component';

/**
 * The Content tab, declared once and consumed twice: `BASE_DOCUMENT_ROUTES` mounts it as
 * `document/<id>/content`, and `BaseDocumentContainerComponent` puts it on the descriptor so the tab bar
 * renders the link. One constant rather than two literals because the segment is what ties the link to the
 * route — a mismatch would render a tab that navigates to a URL nothing matches.
 */
export const DOCUMENT_CONTENT_TAB: EntityTabDescriptor = {
  segment: 'content',
  i18nKey: DOCUMENT_CONTENT_I18N_KEY,
  component: DocumentContentTabComponent,
  testIdSuffix: 'show-content',
};

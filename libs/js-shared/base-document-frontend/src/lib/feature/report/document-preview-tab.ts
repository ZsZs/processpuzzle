import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { DOCUMENT_PREVIEW_I18N_KEY } from '../../base-document.i18n';
import { DocumentPreviewTabComponent } from './document-preview-tab.component';

/** The Preview tab, declared once for the route and the tab bar — the same arrangement as `DOCUMENT_CONTENT_TAB`. */
export const DOCUMENT_PREVIEW_TAB: EntityTabDescriptor = {
  segment: 'preview',
  i18nKey: DOCUMENT_PREVIEW_I18N_KEY,
  component: DocumentPreviewTabComponent,
  testIdSuffix: 'show-preview',
};

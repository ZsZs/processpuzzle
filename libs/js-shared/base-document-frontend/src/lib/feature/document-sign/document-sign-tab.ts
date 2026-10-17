import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { DOCUMENT_SIGN_I18N_KEY } from '../../base-document.i18n';
import { DocumentSignTabComponent } from './document-sign-tab.component';

/** The Sign tab, declared once for the route and the tab bar — the same arrangement as `DOCUMENT_CONTENT_TAB`. */
export const DOCUMENT_SIGN_TAB: EntityTabDescriptor = {
  segment: 'sign',
  i18nKey: DOCUMENT_SIGN_I18N_KEY,
  component: DocumentSignTabComponent,
  testIdSuffix: 'show-sign',
};

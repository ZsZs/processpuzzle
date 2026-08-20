/*
 * Public API Surface of @processpuzzle/base-document
 */

export {
  Document,
  DocumentInputPort,
  DocumentOutputPort,
  AttributeVisibilityMode,
  BlockKind,
  DocumentStatus,
  DOCUMENT_SLUG_PATTERN,
  DOCUMENT_SOURCE_LOCALES,
  PortType,
  WidgetPlacement,
} from './lib/domain/base-document';
export type { AttributeVisibility, DocumentBlock, DocumentProperties, DocumentTranslation, DocumentTranslationSummary } from './lib/domain/base-document';
export { DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './lib/domain/document-entity-names';
export { createDocumentDescriptor } from './lib/domain/base-document.descriptors';
export { createDocumentInputPortDescriptor, createDocumentOutputPortDescriptor, DOCUMENT_PORT_ID_FIELD } from './lib/domain/document-port.descriptors';
export { BaseDocumentMapper } from './lib/domain/base-document.mapper';
export { BaseDocumentService } from './lib/domain/base-document.service';
export { BaseDocumentStore } from './lib/domain/base-document.store';
export { BaseDocumentContainerComponent } from './lib/feature/base-document-container.component';
export { DocumentFacade } from './lib/feature/document.facade';
export { DocumentInputPortFacade } from './lib/feature/document-input-port.facade';
export { DocumentOutputPortFacade } from './lib/feature/document-output-port.facade';
export { BASE_DOCUMENT_ENTITY_FACADES, BASE_DOCUMENT_FACADE_PROVIDERS } from './lib/base-document.providers';
export { BASE_DOCUMENT_ROUTES } from './lib/base-document.routes';
export { DocumentContentTabComponent } from './lib/feature/document-content-tab.component';
export { DOCUMENT_CONTENT_TAB } from './lib/feature/document-content-tab';
export { DocumentEditorComponent } from './lib/feature/document-editor/document-editor.component';
export { DocumentTextBlockComponent } from './lib/feature/document-editor/document-text-block.component';
export { DocumentContentService } from './lib/feature/document-editor/document-content.service';
export { DocumentContentStore } from './lib/feature/document-editor/document-content.store';
export {
  BASE_DOCUMENT_TRANSLOCO_SCOPE,
  DOCUMENT_CONTENT_I18N_KEY,
  DOCUMENT_I18N_SCOPE,
  DOCUMENT_INPUT_PORT_I18N_SCOPE,
  DOCUMENT_OUTPUT_PORT_I18N_SCOPE,
} from './lib/base-document.i18n';
export { BASE_DOCUMENT_TRANSLATION_SOURCE } from './lib/base-document.i18n';

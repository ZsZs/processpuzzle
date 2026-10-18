/*
 * Public API Surface of @processpuzzle/base-document
 */

export {
  Document,
  DocumentInputPort,
  DocumentOutputPort,
  AttributeVisibilityMode,
  BlockKind,
  DocumentKind,
  DocumentStatus,
  DOCUMENT_SLUG_PATTERN,
  DOCUMENT_SOURCE_LOCALES,
  PortType,
  WidgetPlacement,
} from './lib/domain/base-document';
export type { AttributeVisibility, DocumentBlock, DocumentProperties, DocumentSignature, DocumentSignatureSubject, DocumentTranslation, DocumentTranslationSummary } from './lib/domain/base-document';
export { CONTENT_BOOK_ENTITY_NAME, DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './lib/domain/document-entity-names';
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
export { ContentBook } from './lib/domain/content-book';
export type { ContentBookNode } from './lib/domain/content-book';
export { createContentBookDescriptor } from './lib/domain/content-book.descriptors';
export { ContentBookMapper } from './lib/domain/content-book.mapper';
export { ContentBookService } from './lib/domain/content-book.service';
export { ContentBookStore } from './lib/domain/content-book.store';
export { DOCUMENT_ACCESS_CONTEXT, mayEdit } from './lib/domain/document-access-context';
export type { DocumentAccessContext } from './lib/domain/document-access-context';
export { ContentBookFacade } from './lib/feature/content-book.facade';
export { ContentBookContainerComponent } from './lib/feature/content-book-container.component';
export { CONTENT_BOOK_CONTENT_TAB } from './lib/feature/content-book/content-book-content-tab';
export { CONTENT_BOOK_PAGE_PARAM, ContentBookWidgetComponent } from './lib/feature/content-book/content-book-widget.component';
export { ContentBookDocumentViewComponent } from './lib/feature/content-book/content-book-document-view.component';
export { CONTENT_BOOK_WIDGET, CONTENT_BOOK_WIDGET_REGISTRATION, provideContentBookWidget } from './lib/feature/content-book/content-book.widget';
export { DocumentContentTabComponent } from './lib/feature/document-content-tab.component';
export { DOCUMENT_CONTENT_TAB } from './lib/feature/document-content-tab';
export { DocumentEditorComponent } from './lib/feature/document-editor/document-editor.component';
export { DocumentTextBlockComponent } from './lib/feature/document-editor/document-text-block.component';
export { DocumentContentService } from './lib/feature/document-editor/document-content.service';
export { DocumentContentStore } from './lib/feature/document-editor/document-content.store';
export { DocumentDataContext } from './lib/feature/report/document-data-context';
export type { DocumentObjectOption, DocumentPortValues } from './lib/feature/report/document-data-context';
export { DocumentPathCatalog } from './lib/feature/report/document-path-catalog';
export type { DocumentCollectionPath, DocumentFieldPath, DocumentPaths } from './lib/feature/report/document-path-catalog';
export { DocumentReportViewComponent } from './lib/feature/report/document-report-view.component';
export { DOCUMENT_PREVIEW_TAB } from './lib/feature/report/document-preview-tab';
export { EntityReportRegistry } from './lib/feature/report/entity-report.registry';
export type { EntityReportTemplate } from './lib/feature/report/entity-report.registry';
export { ENTITY_REPORTS_TAB, ENTITY_REPORTS_TAB_SEGMENT, EntityReportsTabContributor, provideEntityReportsTab } from './lib/feature/report/entity-reports-tab.contributor';
export {
  BASE_DOCUMENT_TRANSLOCO_SCOPE,
  CONTENT_BOOK_I18N_SCOPE,
  DOCUMENT_CONTENT_I18N_KEY,
  DOCUMENT_I18N_SCOPE,
  DOCUMENT_INPUT_PORT_I18N_SCOPE,
  DOCUMENT_OUTPUT_PORT_I18N_SCOPE,
} from './lib/base-document.i18n';
export { BASE_DOCUMENT_TRANSLATION_SOURCE } from './lib/base-document.i18n';

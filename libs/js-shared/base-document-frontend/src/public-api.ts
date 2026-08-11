/*
 * Public API Surface of @processpuzzle/base-document
 */

export { Document, DocumentInputPort, DocumentOutputPort, AttributeVisibility, BlockKind, PortType, WidgetPlacement } from './lib/domain/base-document';
export type { DocumentBlock } from './lib/domain/base-document';
export { DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './lib/domain/document-entity-names';
export { createDocumentDescriptor } from './lib/domain/base-document.descriptors';
export { createDocumentInputPortDescriptor, createDocumentOutputPortDescriptor } from './lib/domain/document-port.descriptors';
export { BaseDocumentMapper } from './lib/domain/base-document.mapper';
export { BaseDocumentService } from './lib/domain/base-document.service';
export { BaseDocumentStore } from './lib/domain/base-document.store';
export { BaseDocumentContainerComponent } from './lib/feature/base-document-container.component';
export { DocumentEditorComponent } from './lib/feature/document-editor/document-editor.component';
export { DocumentTextBlockComponent } from './lib/feature/document-editor/document-text-block.component';
export { DocumentContentService } from './lib/feature/document-editor/document-content.service';
export { DocumentContentStore } from './lib/feature/document-editor/document-content.store';
export { DOCUMENT_I18N_SCOPE } from './lib/base-document.i18n';

/**
 * Left over from the lib scaffold and unrelated to the wiki-style Document above — a file/blob
 * descriptor, which is `base-entity`'s `FormControlType.ARTIFACT` control's job, not this
 * library's. Still exported so nothing that already imported it breaks; remove it once nothing
 * does.
 */
export { BaseDocument, DEFAULT_DOCUMENT_CONTENT_TYPE } from './lib/base-document';

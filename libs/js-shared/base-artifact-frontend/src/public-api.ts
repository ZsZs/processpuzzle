/*
 * Public API Surface of @processpuzzle/base-artifact
 */

export { Artifact, ArtifactInputPort, ArtifactOutputPort, AttributeVisibility, BlockKind, PortType, WidgetPlacement } from './lib/domain/base-artifact';
export type { ArtifactBlock } from './lib/domain/base-artifact';
export { ARTIFACT_ENTITY_NAME, ARTIFACT_INPUT_PORT_ENTITY_NAME, ARTIFACT_OUTPUT_PORT_ENTITY_NAME } from './lib/domain/artifact-entity-names';
export { createArtifactDescriptor } from './lib/domain/base-artifact.descriptors';
export { createArtifactInputPortDescriptor, createArtifactOutputPortDescriptor } from './lib/domain/artifact-port.descriptors';
export { BaseArtifactMapper } from './lib/domain/base-artifact.mapper';
export { BaseArtifactService } from './lib/domain/base-artifact.service';
export { BaseArtifactStore } from './lib/domain/base-artifact.store';
export { BaseArtifactContainerComponent } from './lib/feature/base-artifact-container.component';
export { ArtifactEditorComponent } from './lib/feature/artifact-editor/artifact-editor.component';
export { ArtifactTextBlockComponent } from './lib/feature/artifact-editor/artifact-text-block.component';
export { ArtifactContentService } from './lib/feature/artifact-editor/artifact-content.service';
export { ArtifactContentStore } from './lib/feature/artifact-editor/artifact-content.store';
export { ARTIFACT_I18N_SCOPE } from './lib/base-artifact.i18n';

/**
 * Left over from the lib scaffold and unrelated to the wiki-style Artifact above — a file/blob
 * descriptor that happens to share the name. Still exported so nothing that already imported it
 * breaks, but it needs renaming or removing; see also the ArtifactAttr / FormControlType.ARTIFACT
 * collision noted on WIDGET_REGISTRY.
 */
export { BaseArtifact, DEFAULT_ARTIFACT_CONTENT_TYPE } from './lib/base-artifact';

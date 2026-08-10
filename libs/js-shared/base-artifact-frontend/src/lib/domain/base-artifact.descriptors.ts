import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { ARTIFACT_I18N_SCOPE } from '../base-artifact.i18n';
import { ARTIFACT_ENTITY_NAME, ARTIFACT_INPUT_PORT_ENTITY_NAME, ARTIFACT_OUTPUT_PORT_ENTITY_NAME } from './artifact-entity-names';

export { ARTIFACT_ENTITY_NAME };

function createArtifactAttrDescriptors(): AbstractAttrDescriptor[] {
  const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title', undefined, true);
  titleAttr.required = true;
  titleAttr.isHeading = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');

  const inputPortsAttr = new BaseEntityAttrDescriptor('inputPorts', FormControlType.EMBEDDED_COMPONENTS, 'Input ports');
  inputPortsAttr.linkedEntityType = ARTIFACT_INPUT_PORT_ENTITY_NAME;
  inputPortsAttr.hideInTable = true;

  const outputPortsAttr = new BaseEntityAttrDescriptor('outputPorts', FormControlType.EMBEDDED_COMPONENTS, 'Output ports');
  outputPortsAttr.linkedEntityType = ARTIFACT_OUTPUT_PORT_ENTITY_NAME;
  outputPortsAttr.hideInTable = true;

  // Deliberately no `blocks` attribute here. Content is edited by ArtifactEditorComponent
  // through ArtifactContentService's block-level endpoints, not through this form — see
  // BaseArtifactContainerComponent for how the two are composed on one screen, and
  // BaseArtifactService.updateProperties/ArtifactPropertiesInput for why this form's save
  // is structurally incapable of touching blocks even by accident.
  return [new FlexboxDescriptor([titleAttr, descriptionAttr, inputPortsAttr, outputPortsAttr], FlexDirection.COLUMN)];
}

export function createArtifactDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ARTIFACT_ENTITY_NAME,
    attrDescriptors: createArtifactAttrDescriptors(),
    i18nScope: ARTIFACT_I18N_SCOPE,
  });
}

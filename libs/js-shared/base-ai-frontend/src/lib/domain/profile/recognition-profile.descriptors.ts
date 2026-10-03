import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { RECOGNITION_PROFILE_I18N_SCOPE } from '../../base-ai.i18n';
import { RECOGNITION_PROFILE_ENTITY_NAME } from './recognition-profile';

/**
 * The object classes the stock detector (RT-DETR, trained on COCO) is most likely to be asked for. A
 * dropdown rather than free text, because a misspelled class is not an error anywhere — the detector simply
 * finds nothing, and every photo comes back NO_SUBJECT. The full list of 80 is what the vision server's
 * `/models` reports; these are the ones an identifiable-object use case plausibly needs.
 */
export const DETECTOR_CLASSES = ['boat', 'person', 'car', 'truck', 'bus', 'motorcycle', 'bicycle', 'airplane', 'train', 'horse', 'dog', 'cat'];

function numberAttr(attrName: string, label: string): BaseEntityAttrDescriptor {
  const attr = new BaseEntityAttrDescriptor(attrName, FormControlType.TEXT_BOX, label, undefined, undefined, { inputType: 'number' });
  attr.hideInTable = true;
  return attr;
}

function createRecognitionProfileAttrDescriptors(): AbstractAttrDescriptor[] {
  // The business key and the record's identity, as a state machine's entityName is.
  const entityNameAttr = new BaseEntityAttrDescriptor('entityName', FormControlType.TEXT_BOX, 'Entity Type', undefined, true);
  entityNameAttr.required = true;
  entityNameAttr.isHeading = true;
  entityNameAttr.placeholder = 'Definition code of the subjects, e.g. boat';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const detectorClassAttr = new BaseEntityAttrDescriptor(
    'detectorClass',
    FormControlType.DROPDOWN,
    'Detector Class',
    DETECTOR_CLASSES.map((detectorClass) => ({ key: detectorClass, value: detectorClass })),
  );
  detectorClassAttr.required = true;

  // Text, not a dropdown of the subject's attributes: those belong to another feature's definition, which
  // this form cannot see. The backend checks on save that it names a TEXT attribute of entityName.
  const identifierAttributeKeyAttr = new BaseEntityAttrDescriptor('identifierAttributeKey', FormControlType.TEXT_BOX, 'Identifier Attribute');
  identifierAttributeKeyAttr.placeholder = 'TEXT attribute holding the identifier, e.g. sailNumber';

  const identifierPatternAttr = new BaseEntityAttrDescriptor('identifierPattern', FormControlType.TEXT_BOX, 'Identifier Pattern');
  identifierPatternAttr.placeholder = 'e.g. ^[A-Z]{3} ?[0-9]{1,5}$';
  identifierPatternAttr.hideInTable = true;

  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;
  versionAttr.hideInTable = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  const row = (attrs: AbstractAttrDescriptor[]) => {
    const flexbox = new FlexboxDescriptor(attrs, FlexDirection.ROW);
    flexbox.style = { 'column-gap': '10px' };
    return flexbox;
  };

  const container = new FlexboxDescriptor(
    [
      row([entityNameAttr, nameAttr]),
      row([detectorClassAttr, identifierAttributeKeyAttr, identifierPatternAttr]),
      row([numberAttr('identifierWeight', 'Identifier Weight'), numberAttr('acceptScore', 'Accept Score'), numberAttr('acceptMargin', 'Accept Margin'), numberAttr('sampleFps', 'Sampled FPS')]),
      row([versionAttr, updatedAtAttr]),
      descriptionAttr,
    ],
    FlexDirection.COLUMN,
  );
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

export function createRecognitionProfileDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: RECOGNITION_PROFILE_ENTITY_NAME,
    attrDescriptors: createRecognitionProfileAttrDescriptors(),
    i18nScope: RECOGNITION_PROFILE_I18N_SCOPE,
  });
}

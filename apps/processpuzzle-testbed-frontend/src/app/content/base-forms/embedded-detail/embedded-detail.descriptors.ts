// eslint-disable-next-line @nx/enforce-module-boundaries
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';

function createEmbeddedDetailAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  const noteAttr = new BaseEntityAttrDescriptor('note', FormControlType.TEXTAREA, 'Note');

  const column = new FlexboxDescriptor([nameAttr, noteAttr], FlexDirection.COLUMN);
  column.style = { 'row-gap': '5px' };
  return [column];
}

export function createEmbeddedDetailDescriptor(): BaseEntityDescriptor {
  // Embedded in an entity that is itself embedded: the row is reached through `Test Entity` → `Embedded
  // Component` → here, and it is `Test Entity`'s document that carries — and persists — all three.
  return new BaseEntityDescriptor({
    entityName: 'Embedded Detail',
    attrDescriptors: createEmbeddedDetailAttrDescriptors(),
    componentParent: 'Embedded Component',
    isEmbedded: true,
  });
}

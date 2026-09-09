// eslint-disable-next-line @nx/enforce-module-boundaries
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';

function createEmbeddedComponentAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');

  // Containment inside a contained entity: the details are carried by this component, which is itself
  // carried by `Test Entity` — so all three levels travel in, and are saved as, one document.
  const embeddedDetailsAttr = new BaseEntityAttrDescriptor('embeddedDetails', FormControlType.EMBEDDED_COMPONENTS, 'Embedded Details');
  embeddedDetailsAttr.linkedEntityType = 'Embedded Detail';
  embeddedDetailsAttr.hideInTable = true;

  const column = new FlexboxDescriptor([nameAttr, descriptionAttr, embeddedDetailsAttr], FlexDirection.COLUMN);
  column.style = { 'row-gap': '5px' };
  return [column];
}

export function createEmbeddedComponentDescriptor(): BaseEntityDescriptor {
  // Embedded: no endpoint of its own — `Test Entity`'s document carries it, and a save here rewrites that
  // document. There is no foreign key back to the parent either; the component is located by its position in
  // the payload, which is what the nested route in `app.routes.ts` spells out.
  return new BaseEntityDescriptor({
    entityName: 'Embedded Component',
    attrDescriptors: createEmbeddedComponentAttrDescriptors(),
    componentParent: 'Test Entity',
    isEmbedded: true,
  });
}

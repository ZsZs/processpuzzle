// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';

const column_1 = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
column_1.required = true;
const column_2 = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');

export const firestoreDocDescriptors: BaseEntityAttrDescriptor[] = [column_1, column_2];

let cachedDescriptor: BaseEntityDescriptor | undefined;

export function createFirestoreDocDescriptor(): BaseEntityDescriptor {
  if (cachedDescriptor) return cachedDescriptor;
  cachedDescriptor = new BaseEntityDescriptor({ entityName: 'Firestore Doc', attrDescriptors: firestoreDocDescriptors });
  return cachedDescriptor;
}

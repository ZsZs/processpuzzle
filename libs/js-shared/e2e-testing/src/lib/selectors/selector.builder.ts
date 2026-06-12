import type { FormControlType } from '@processpuzzle/base-entity';
import { formControlLocatorForType } from '../controls/control-tester';
import { formControlTestId, toTestId } from './test-id';

/** "Test Entity Component" → "testEntityComponent" */
export { toTestId };

/** e.g. [data-testid="testEntity-name"] */
export function attrSelector(entityName: string, attrName: string): string {
  return `[data-testid="${toTestId(entityName)}-${attrName}"]`;
}

/** e.g. [data-testid="testEntity-save"] */
export function buttonTestId(entityName: string, action: 'new' | 'save' | 'delete' | 'edit' | 'cancel'): string {
  return `${toTestId(entityName)}-${action}`;
}

/** e.g. [data-testid="testEntity-save"] */
export function buttonSelector(entityName: string, action: 'new' | 'save' | 'delete' | 'edit' | 'cancel'): string {
  return `[data-testid="${toTestId(entityName)}-${action}"]`;
}

export function formControlSelector(entityName: string, attrName: string): string {
  return formControlTestId(entityName, attrName);
}

export function formControlLocator(type: FormControlType): string {
  return formControlLocatorForType(type);
}

/** aria-label of the "Select <EntityName>" button in the FK control. */
export function selectButtonAriaLabel(linkedEntityName: string): string {
  return `Select ${linkedEntityName}`;
}

export function listSelectButtonTestId(linkedEntityName: string): string {
  return `${toTestId(linkedEntityName)}-select`;
}

export function listCancelButtonTestId(linkedEntityName: string): string {
  return `${toTestId(linkedEntityName)}-cancel`;
}

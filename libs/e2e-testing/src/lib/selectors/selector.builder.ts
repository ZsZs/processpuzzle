import type { FormControlType } from '@processpuzzle/base-entity';

/** "Test Entity Component" → "testEntityComponent" */
export function toTestId(entityName: string): string {
  return entityName
    .split(' ')
    .map((word, i) => (i === 0 ? word.charAt(0).toLowerCase() + word.slice(1) : word.charAt(0).toUpperCase() + word.slice(1)))
    .join('');
}

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
  return `${toTestId(entityName)}-${attrName}`;
}

export function formControlLocator(type: FormControlType): string {
  switch (type as string) {
    case 'TEXT_BOX':
    case 'TEXTAREA':
      return 'input, textarea';
    case 'CHECKBOX':
      return 'input[type="checkbox"]';
    case 'DATE':
      return 'input[matInput]';
    case 'DROPDOWN':
      return 'mat-select';
    case 'TAGS':
      return 'mat-chip-grid input';
    case 'FOREIGN_KEY':
    case 'LOOKUP':
      return 'input[matInput]';
    // TODO: ARTIFACT / LOOKUP / COMPONENTS — deferred, depends on linked-entity resolution
    default:
      return '';
  }
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
